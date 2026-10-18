package com.noam.kidtube.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs Google's BotGuard inside a WebView to obtain proof-of-origin tokens.
 *
 * The division of labour is not obvious and is worth stating: the asset page defines
 * three plain JavaScript functions and nothing else. Kotlin registers the bridge object,
 * injects the calls to it, and performs *all* the network itself — the WebView is created
 * with network loading blocked precisely so it cannot reach out on its own. The full
 * sequence is:
 *
 *   1. fetch visitorData from Innertube (Kotlin, IO)
 *   2. load the local page and wait for it to call back (WebView, main thread)
 *   3. POST the jnn/v1/Create endpoint for the BotGuard challenge (Kotlin, IO)
 *   4. runBotGuard(challenge) in the page, which yields a botguardResponse (WebView)
 *   5. POST jnn/v1/GenerateIT with that response for an integrityToken (Kotlin, IO)
 *   6. mint the streaming token from visitorData — once, before any player token
 *
 * Timeouts and retry policy live in [PoTokenSession], not here: this class reports what
 * happened and nothing more.
 */
class WebViewPoTokenGenerator(
    appContext: Context,
    private val downloader: Downloader = OkDownloader(),
) : PoTokenGenerator {

    private val context = appContext.applicationContext

    private var webView: WebView? = null
    private var pageLoaded = CompletableDeferred<Unit>()
    private var botguardResponse = CompletableDeferred<String>()
    private val pendingTokens = ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()

    private val bridge = object {
        /** Injected into the page, so it fires once the asset has finished loading. */
        @JavascriptInterface
        fun downloadAndRunBotguard() {
            pageLoaded.complete(Unit)
        }

        @JavascriptInterface
        fun onJsInitializationError(error: String) {
            val failure = IllegalStateException("BotGuard JS error: $error")
            pageLoaded.completeExceptionally(failure)
            botguardResponse.completeExceptionally(failure)
        }

        @JavascriptInterface
        fun onRunBotguardResult(response: String) {
            botguardResponse.complete(response)
        }

        @JavascriptInterface
        fun onObtainPoTokenError(identifier: String, error: String) {
            pendingTokens.remove(identifier)
                ?.complete(Result.failure(IllegalStateException("PO token error: $error")))
        }

        @JavascriptInterface
        fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
            pendingTokens.remove(identifier)
                ?.complete(runCatching { PoTokenCodec.u8ToBase64(poTokenU8) })
        }
    }

    override suspend fun initialise(): Result<PoTokenSessionData> = runCatching {
        val visitorData = withContext(Dispatchers.IO) { fetchVisitorData() }

        pageLoaded = CompletableDeferred()
        botguardResponse = CompletableDeferred()
        withContext(Dispatchers.Main) { createWebView() }
        pageLoaded.await()

        val challengeRaw = withContext(Dispatchers.IO) {
            post(CREATE_URL, "[ \"$REQUEST_KEY\" ]")
        }
        val challengeJson = PoTokenCodec.parseChallengeData(challengeRaw)
        withContext(Dispatchers.Main) { runBotGuard(challengeJson) }
        val botguard = botguardResponse.await()

        val integrityRaw = withContext(Dispatchers.IO) {
            post(GENERATE_IT_URL, "[ \"$REQUEST_KEY\", \"$botguard\" ]")
        }
        val (integrityTokenLiteral, ttlSeconds) = PoTokenCodec.parseIntegrityTokenData(integrityRaw)
        withContext(Dispatchers.Main) {
            evaluate("this.integrityToken = $integrityTokenLiteral")
        }

        // The streaming token must be minted from visitorData before any player token.
        val streaming = mint(visitorData).getOrThrow()

        PoTokenSessionData(
            visitorData = visitorData,
            streamingPoToken = streaming,
            // A ten-minute margin, so a token is never used as it lapses.
            expiresAtEpochSeconds = System.currentTimeMillis() / 1_000 + ttlSeconds - EXPIRY_MARGIN,
        )
    }

    override suspend fun tokenFor(identifier: String): Result<String> =
        runCatching { mint(identifier).getOrThrow() }

    override fun release() {
        val view = webView ?: return
        webView = null
        pendingTokens.values.forEach {
            it.complete(Result.failure(IllegalStateException("generator released")))
        }
        pendingTokens.clear()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                view.removeJavascriptInterface(JS_INTERFACE)
                view.loadUrl("about:blank")
                view.destroy()
            }
        }
    }

    private suspend fun mint(identifier: String): Result<String> {
        val deferred = CompletableDeferred<Result<String>>()
        pendingTokens[identifier] = deferred
        withContext(Dispatchers.Main) {
            val view = webView
            if (view == null) {
                pendingTokens.remove(identifier)
                deferred.complete(
                    Result.failure(IllegalStateException("generator not initialised")),
                )
            } else {
                evaluate(obtainPoTokenScript(identifier))
            }
        }
        return deferred.await()
    }

    private fun fetchVisitorData(): String {
        val requestInfo = InnertubeClientRequestInfo.ofWebClient()
        requestInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
        return YoutubeParsingHelper.getVisitorDataFromInnertube(
            requestInfo,
            NewPipe.getPreferredLocalization(),
            NewPipe.getPreferredContentCountry(),
            YoutubeParsingHelper.getYouTubeHeaders(),
            YoutubeParsingHelper.YOUTUBEI_V1_URL,
            null,
            false,
        )
    }

    private fun post(url: String, body: String): String {
        val response = downloader.post(
            url,
            mapOf(
                "User-Agent" to listOf(BOTGUARD_USER_AGENT),
                "Accept" to listOf("application/json"),
                "Content-Type" to listOf("application/json+protobuf"),
                "x-goog-api-key" to listOf(GOOGLE_API_KEY),
                "x-user-agent" to listOf("grpc-web-javascript/0.1"),
            ),
            body.toByteArray(Charsets.UTF_8),
        )
        check(response.responseCode() == 200) {
            "BotGuard service request to $url returned HTTP ${response.responseCode()}"
        }
        return response.responseBody()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
        // The page itself never calls the bridge; the call is injected here, which is why
        // grepping the asset for the interface name finds nothing.
        val withBridgeCall = html.replaceFirst(
            "</script>",
            "\n$JS_INTERFACE.downloadAndRunBotguard()</script>",
        )

        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.userAgentString = BOTGUARD_USER_AGENT
            // Kotlin does the networking; the page must not fetch anything itself.
            settings.blockNetworkLoads = true
            addJavascriptInterface(bridge, JS_INTERFACE)
        }
        webView = view
        view.loadDataWithBaseURL(
            "https://www.youtube.com",
            withBridgeCall,
            "text/html",
            "utf-8",
            null,
        )
    }

    private fun runBotGuard(challengeJson: String) {
        evaluate(
            """
            try {
                data = $challengeJson
                runBotGuard(data).then(function (result) {
                    this.webPoSignalOutput = result.webPoSignalOutput
                    $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                }, function (error) {
                    $JS_INTERFACE.onJsInitializationError(error + " " + error.stack)
                })
            } catch (error) {
                $JS_INTERFACE.onJsInitializationError(error + " " + error.stack)
            }
            """.trimIndent(),
        )
    }

    private fun obtainPoTokenScript(identifier: String): String {
        val u8Identifier = PoTokenCodec.stringToU8(identifier)
        return """
            try {
                identifier = ${quote(identifier)}
                u8Identifier = $u8Identifier
                poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                poTokenU8String = ""
                for (i = 0; i < poTokenU8.length; i++) {
                    if (i != 0) poTokenU8String += ","
                    poTokenU8String += poTokenU8[i]
                }
                $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
            } catch (error) {
                $JS_INTERFACE.onObtainPoTokenError(${quote(identifier)}, error + " " + error.stack)
            }
        """.trimIndent()
    }

    private fun evaluate(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    /** visitorData is base64-ish but not guaranteed JS-safe, so it is quoted properly. */
    private fun quote(value: String): String =
        com.grack.nanojson.JsonWriter.string(value)

    private companion object {
        // Public BotGuard API key, as used by youtube.com itself.
        const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        const val JS_INTERFACE = "PoTokenWebView"
        const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
        const val EXPIRY_MARGIN = 600L
    }
}
