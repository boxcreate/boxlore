plugins {
    alias(libs.plugins.androidLibrary)
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
    namespace = "cx.aswin.boxlore.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
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
    implementation(libs.kotlinx.serialization.json)
    // @StringRes / @PluralsRes on UiText.
    api(libs.androidx.annotation)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Locale formatting runs on android.icu, which Robolectric provides on the JVM.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
