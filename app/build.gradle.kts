import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.noam.kidtube"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.noam.kidtube"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    signingConfigs {
        /**
         * Sideload signing. The release build type is not debuggable, which matters for
         * this app specifically: a debuggable build lets anyone with adb run `run-as` and
         * read the app's private files - the whitelist and the password hash among them -
         * which is exactly the promise the app makes to a parent.
         *
         * This uses the local debug keystore so a release APK can be installed without
         * inventing a password. The key is well known and public, so an APK signed with
         * it must never be distributed as if it were trusted; point `sideload.keystore`
         * in local.properties at a real key before sharing builds with anyone.
         */
        create("sideload") {
            val localProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            val custom = localProps.getProperty("sideload.keystore")
            if (custom != null) {
                storeFile = file(custom)
                storePassword = localProps.getProperty("sideload.storePassword")
                keyAlias = localProps.getProperty("sideload.keyAlias")
                keyPassword = localProps.getProperty("sideload.keyPassword")
            } else {
                storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    /**
     * One APK per CPU rather than one carrying all of them.
     *
     * The on-device translation engine ships as a native library, and four architectures
     * of it is 62MB of a 67MB download - for a tablet that can use exactly one. Split,
     * each build is about 22MB. x86 is left out: a 32-bit-only emulator is the only
     * thing that would want it.
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("sideload")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine-api"))
    implementation(project(":engine-extractor")) // wired in Wiring.kt only
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.media3.ui)
    implementation(libs.mlkit.translate)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(kotlin("test"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
