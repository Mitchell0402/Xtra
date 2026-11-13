// Macrobenchmarks (startup, scrolling, chat stress). Runs against the app's non-debuggable "benchmark"
// build type by default; pass -Pandroid.testInstrumentationRunnerArguments.targetPackage=<id> to
// measure another installed package, e.g. the kept v0.1 baseline build.
plugins {
    alias(libs.plugins.android.test)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "io.github.mitchell0402.lagoon.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // the emulator is noisy; numbers from it are only compared within one session
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
