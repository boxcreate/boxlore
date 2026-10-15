plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "cx.aswin.boxlore.core.testing"
    compileSdk = 36

    buildFeatures { compose = true }

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
            // Konsist / filesystem architecture guards need the repo root.
            it.workingDir = rootProject.projectDir
            it.systemProperty("boxlore.projectRoot", rootProject.projectDir.absolutePath)
        }
    }
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(projects.core.domain)
    api(libs.junit.jupiter)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.okhttp.mockwebserver)
    api(libs.junit)
    api(libs.robolectric)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui.test.junit4)
    api(libs.androidx.activity.compose)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.coil.compose)
    api("org.mockito:mockito-core:5.14.2")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)

    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
