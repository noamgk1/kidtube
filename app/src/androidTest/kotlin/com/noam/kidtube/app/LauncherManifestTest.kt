package com.noam.kidtube.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Can the app actually start?
 *
 * Nothing asked this before. The Compose tests render screens directly, and
 * `ActivityScenario.launch(MainActivity::class.java)` would launch by class and so skip
 * the manifest entirely — which is precisely where the fault was: the manifest declared
 * `.MainActivity` against namespace `com.noam.kidtube`, while the class lives in
 * `com.noam.kidtube.app`. Every build installed fine and died on launch with
 * ClassNotFoundException.
 *
 * So this resolves the launcher the way the launcher icon does, through the merged
 * manifest, and then insists the named class exists.
 */
class LauncherManifestTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun thePackageDeclaresALauncherActivity() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull(intent, "no launcher intent: the app has no icon to tap")
        assertNotNull(intent.component, "the launcher intent names no component")
    }

    @Test
    fun theLauncherActivityNamedInTheManifestExists() {
        val intent = assertNotNull(
            context.packageManager.getLaunchIntentForPackage(context.packageName),
        )
        val className = assertNotNull(intent.component).className

        // Loading it with the app's own class loader is exactly what the system does
        // when the icon is tapped.
        val loaded = runCatching { Class.forName(className, false, context.classLoader) }
        assertTrue(
            loaded.isSuccess,
            "the manifest names " + className + " as the launcher activity, but that " +
                "class does not exist: " + loaded.exceptionOrNull(),
        )
    }

    @Test
    fun theSystemCanResolveTheLauncherActivity() {
        val intent = assertNotNull(
            context.packageManager.getLaunchIntentForPackage(context.packageName),
        )
        assertNotNull(
            context.packageManager.resolveActivity(intent, 0),
            "the system cannot resolve the declared launcher activity",
        )
    }
}
