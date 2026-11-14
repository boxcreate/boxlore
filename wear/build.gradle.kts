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
    namespace = "cx.aswin.boxlore.wear"
    compileSdk = 37

    val localPropsFile = rootProject.file("local.properties")
    val localProps = Properties()
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { localProps.load(it) }
    }
    // The watch reaches the same API as the phone; the address comes from local.properties.
    val apiBaseUrl =
        localProps.getProperty("BOXLORE_API_BASE_URL")?.trim().orEmpty()
            .ifEmpty { localProps.getProperty("BOXCAST_API_BASE_URL")?.trim().orEmpty() }
    // The watch app must carry the phone app's signature, or the two cannot talk to each other.
    val phoneKeystore = rootProject.file("app/release.keystore")

    defaultConfig {
        applicationId = "cx.aswin.boxlore"
        minSdk = 33
        targetSdk = 36
        versionCode = 31
        versionName = "0.0.31"
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
            // `-Pwear.sample=true` builds a debug app that runs on sample shows.
            val sample = providers.gradleProperty("wear.sample").orNull == "true"
            buildConfigField("boolean", "WATCH_SAMPLE_DATA", sample.toString())
            // `-Pwear.phoneKey=true` signs the debug app with the phone app's key, so it can link with a
            // release build of the phone app on a real phone.
            if (providers.gradleProperty("wear.phoneKey").orNull == "true") {
                signingConfigs.findByName("release")?.let { signingConfig = it }
            }
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
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.input)
    // The tile and the watch-face shortcut.
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.complications.datasource)
    implementation(libs.androidx.concurrent.futures)

    implementation(projects.core.network)
    implementation(projects.core.model)
    // The format and connection the phone app and the watch app share.
    implementation(projects.core.wearlink)
    implementation(libs.play.services.wearable)
    // The phone's feed reader, so the watch sees new episodes as soon as the phone would.
    implementation(projects.core.rss)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}
