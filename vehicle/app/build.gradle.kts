plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.kapt") }
android {
    namespace = "dev.todaystrack.vehicle"
    compileSdk = 35
    useLibrary("android.car")
    defaultConfig {
        applicationId = "dev.todaystrack.vehicle"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0-submission"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1536m"
            it.maxParallelForks = 1
        }
    }
    signingConfigs {
        if (System.getenv("TODAYSTRACK_SIGNING_STORE") != null) create("submission") {
            storeFile = file(System.getenv("TODAYSTRACK_SIGNING_STORE"))
            storePassword = System.getenv("TODAYSTRACK_SIGNING_PASSWORD")
            keyAlias = "submission"
            keyPassword = System.getenv("TODAYSTRACK_SIGNING_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            applicationIdSuffix = ".submission"
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("submission")
        }
    }
}
dependencies {
    implementation(project(":music-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
