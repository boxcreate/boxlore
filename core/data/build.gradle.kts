plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cx.aswin.boxcast.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // ...
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        disable += "NullSafeMutableLiveData"
        abortOnError = false
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.datastore.preferences)

    // Room
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // JSON Streaming
    implementation(libs.gson)
    implementation(libs.okhttp)
    // Media
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    
    // Firebase (database and messaging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)

    // PostHog
    implementation(libs.posthog.android)

    // Install Referrer
    implementation("com.android.installreferrer:installreferrer:2.2")

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Testing
    testImplementation(libs.junit)
}
