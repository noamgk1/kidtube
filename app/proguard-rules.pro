# NewPipeExtractor evaluates YouTube's player JavaScript through Rhino.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# The PO token bridge is an anonymous object whose methods are only ever called from
# JavaScript inside the WebView, so R8 sees them as unreachable and is free to rename or
# remove them. Nothing would fail at build time; BotGuard would simply stop working in
# release builds only.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
