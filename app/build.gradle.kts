import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.apollo)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(21)
}

// Release signing comes from keystore.properties in the project root (git-ignored), or from
// LAGOON_KEYSTORE_* environment variables. Without either, release builds fall back to the debug key.
val releaseSigning = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
    System.getenv("LAGOON_KEYSTORE_FILE")?.let { setProperty("storeFile", it) }
    System.getenv("LAGOON_KEYSTORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("LAGOON_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("LAGOON_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}.takeIf { it.getProperty("storeFile") != null }

android {
    signingConfigs {
        getByName("debug") {
            keyAlias = "debug"
            keyPassword = "123456"
            storeFile = file("debug-keystore.jks")
            storePassword = "123456"
        }
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }
    namespace = "com.github.andreyasadchy.xtra"
    compileSdk = 37

    defaultConfig {
        // own id so it installs next to upstream Xtra; the code namespace stays com.github.andreyasadchy.xtra
        applicationId = "io.github.mitchell0402.lagoon"
        minSdk = 29
        targetSdk = 37
        // bumped once per UI-overhaul stage so any stage can be rolled back by reinstalling the previous one
        versionCode = 3
        versionName = "0.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // Release code (R8 on) signed with the debug key and installed under its own id. Manual tests run on
        // this build so shrinker crashes show up without ever touching the release key.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-QA"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        // Non-debuggable, profileable build for the :benchmark module. Also carries the U0 lab screens.
        create("benchmark") {
            initWith(getByName("qa"))
            applicationIdSuffix = ".bench"
            versionNameSuffix = "-BENCH"
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    lint {
        disable += "ContentDescription"
        error += "MissingTranslation"
        // existing issues are recorded here; CI fails only on new ones
        baseline = file("lint-baseline.xml")
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
    sourceSets {
        // real ad playlists captured in stage 0, used by the ad detector tests
        getByName("test").resources.directories.add("../tools/ad-probe/samples")
        // U0 lab: technical spikes (chat list, player surface switch, Compose shell), never in qa or release
        listOf("debug", "benchmark").forEach {
            getByName(it).kotlin.directories.add("src/lab/kotlin")
            getByName(it).manifest.srcFile("src/lab/AndroidManifest.xml")
            getByName(it).assets.directories.add("../tools/chat-probe/samples")
        }
    }
    // minSdk 29 would store dex files uncompressed (+4.7 MB APK); keep them compressed like v0.1 so the
    // size gate (plan 10.4) compares like with like and the GitHub download stays small
    packaging.dex.useLegacyPackaging = true
    packaging.jniLibs.excludes.addAll(listOf(
        "lib/x86/libtranslate_jni.so",
        "lib/x86/liblanguage_id_l2c_jni.so",
        "lib/x86_64/libtranslate_jni.so",
        "lib/x86_64/liblanguage_id_l2c_jni.so",
        "lib/armeabi-v7a/libtranslate_jni.so",
        "lib/armeabi-v7a/liblanguage_id_l2c_jni.so",
    ))
}

dependencies {
    compileOnly("com.google.j2objc:j2objc-annotations:3.0.0") // OkHttpDataSource SettableFuture
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")

    implementation(libs.material)
    implementation(libs.markwon.core)
    implementation(libs.markwon.linkify)

    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.coordinatorlayout)
    implementation(libs.core)
    implementation(libs.fragment)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.paging.runtime)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.paging)
    implementation(libs.swiperefreshlayout)
    implementation(libs.viewpager2)
    implementation(libs.webkit)
    implementation(libs.work.runtime)

    implementation(libs.cronet.api)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.conscrypt)
    implementation(libs.serialization.json)
    implementation(libs.apollo.api)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.okhttp)

    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.glide.okhttp)
    implementation(libs.glide.webpdecoder)

    implementation(libs.coroutines)

    testImplementation(libs.junit)

    // Compose: the Lagoon design system (ui/design) from U1 on; screens move over stage by stage (plan section 8)
    val compose = platform(libs.compose.bom)
    implementation(compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    // only the U0 lab spikes use these so far
    listOf("debugImplementation", "benchmarkImplementation").forEach { conf ->
        add(conf, libs.compose.ui.viewbinding)
        add(conf, libs.media3.ui.compose)
    }
    implementation(libs.profileinstaller)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
}

roborazzi {
    // baselines are recorded and compared on CI (Linux) only, see .github/workflows
    outputDir.set(file("src/testDebug/screenshots"))
}

apollo {
    @Suppress("ApolloEndpointNotConfigured")
    service("service") {
        packageName.set("com.github.andreyasadchy.xtra.graphql")
    }
}

// Delete large build log files from ~/.gradle/daemon/X.X/daemon-XXX.out.log
// Source: https://discuss.gradle.org/t/gradle-daemon-produces-a-lot-of-logs/9905
File("${project.gradle.gradleUserHomeDir.absolutePath}/daemon/${project.gradle.gradleVersion}").listFiles()?.forEach {
    if (it.name.endsWith(".out.log")) {
        // println("Deleting gradle log file: $it") // Optional debug output
        it.delete()
    }
}