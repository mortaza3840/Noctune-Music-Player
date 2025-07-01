plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.music.noctune"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.music.noctune"
        minSdk = 24
        targetSdk = 34
        versionCode = 109
        versionName = "6.4"

        renderscriptTargetApi = 30
        renderscriptSupportModeEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation ("com.karumi:dexter:6.2.3")
    implementation ("androidx.core:core:1.10.1")
    implementation ("androidx.media:media:1.6.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation ("androidx.room:room-runtime:2.5.2")
    implementation ("com.google.android.material:material:1.9.0")
    implementation("androidx.palette:palette:1.0.0")

    annotationProcessor ("androidx.room:room-compiler:2.5.2")


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}