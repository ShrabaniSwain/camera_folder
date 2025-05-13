plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}


android {
    namespace = "com.app.camerademo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.camfolder"
        minSdk = 26
        targetSdk = 34
        versionCode = 10001
        versionName = "1.0.01.12052025.10001"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")


// Video recording
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.camera:camera-core:1.4.2")

    val exoplayerVersion = "2.18.6"
    implementation("com.google.android.exoplayer:exoplayer:$exoplayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-core:$exoplayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-ui:$exoplayerVersion")

    implementation("com.github.bumptech.glide:glide:4.14.2")

    implementation("com.jsibbold:zoomage:1.3.1")

//    implementation("androidx.camera:camera-view:$cameraxVersion")
//    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    implementation("com.github.evgenyneu:js-evaluator-for-android:v6.0.0")

}