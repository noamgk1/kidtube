plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.noam.kidtube.extractor"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Quarantine: the network suite is opt-in, so a normal build never depends on YouTube.
        testInstrumentationRunnerArguments["networkTest"] =
            (project.findProperty("networkTest") as String?) ?: "false"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":engine-api"))
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.nanojson)
    implementation(libs.jsoup)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
