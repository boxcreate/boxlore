import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

kover {
    currentProject {
        createVariant("merged") {
            add("debug")
        }
    }
}

android {
    namespace = "cx.aswin.boxlore.tv"
    compileSdk = 37

    val localPropsFile = rootProject.file("local.properties")
    val localProps = Properties()
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { localProps.load(it) }
    }
    // The TV reaches the same API as the phone; the address comes from local.properties.
    val apiBaseUrl =
        localProps.getProperty("BOXLORE_API_BASE_URL")?.trim().orEmpty()
            .ifEmpty { localProps.getProperty("BOXCAST_API_BASE_URL")?.trim().orEmpty() }
    // The TV app carries the phone app's signature, so Play and the phone treat them as one app.
    val phoneKeystore = rootProject.file("app/release.keystore")

    // The TV app is released with the phone app and carries its version name. Its version code is the
    // phone's plus a fixed offset: Google Play takes each code once per app, and the phone, the watch
    // (+1,000,000) and the TV share one. Both are read from the phone app's build file.
    val phoneBuild = providers.fileContents(rootProject.layout.projectDirectory.file("app/build.gradle.kts")).asText.get()
    val phoneVersion = { pattern: String ->
        Regex(pattern, RegexOption.MULTILINE).find(phoneBuild)?.groupValues?.get(1) ?: error("app/build.gradle.kts: nothing matches $pattern")
    }
    val tvVersionCodeOffset = 2_000_000

    defaultConfig {
        applicationId = "cx.aswin.boxlore"
        // Play's TV rule TV-PS asks for 31 or lower; the core modules the TV will read from need 31.
        minSdk = 31
        targetSdk = 36
        versionCode = tvVersionCodeOffset + phoneVersion("""^\s*versionCode\s*=\s*(\d+)\s*$""").toInt()
        versionName = phoneVersion("""^\s*versionName\s*=\s*"([^"]+)"\s*$""")
        buildConfigField("String", "BOXLORE_API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        if (phoneKeystore.exists()) {
            create("release") {
                storeFile = phoneKeystore
                storePassword = localProps.getProperty("KEY_STORE_PASSWORD")
                keyAlias = "upload"
                keyPassword = localProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds run on sample shows; `-Ptv.sample=false` reads the real API and plays real
            // audio instead. A release build never shows made-up content.
            val sample = providers.gradleProperty("tv.sample").orNull != "false"
            buildConfigField("boolean", "TV_SAMPLE_DATA", sample.toString())
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // A release-speed build on the sample shows, signed with the debug key, for checking how
        // smoothly the app runs on a real TV. Debug builds run several times slower on old TVs.
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            val sample = providers.gradleProperty("tv.sample").orNull != "false"
            buildConfigField("boolean", "TV_SAMPLE_DATA", sample.toString())
        }
    }

    sourceSets {
        getByName("benchmark") {
            kotlin.srcDir("src/debug/java")
        }
    }

    lint {
        // The TV's strings land in English first; their translations follow before release.
        warning += "MissingTranslation"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // The same API, feed reader and episode IDs as the phone and the watch.
    implementation(projects.core.network)
    implementation(projects.core.model)
    implementation(projects.core.rss)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.tv.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
