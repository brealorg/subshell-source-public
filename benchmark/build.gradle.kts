plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.brealorg.subshell.benchmark"
    compileSdk = 37
    // Compose 1.13 (BOM 2026.11.01) needs compileSdk 37.1 or later; 37.2 is the newest stable
    // platform (1.0.5 tool update, 2026-11-19). targetSdk stays 37.
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 29
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("benchmark")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
