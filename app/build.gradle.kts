plugins {
    id("com.android.application")
}

android {
    namespace = "com.tivilite.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tivilite.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.5"

        val privateSalt = (project.findProperty("TIVILITE_SALT") as String?) ?: "COMMUNITY_DEFAULT_SALT"
        buildConfigField("String", "LICENSE_SALT", "\"$privateSalt\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("tivilite.jks")
            storePassword = "tivilite123"
            keyAlias = "tivilite"
            keyPassword = "tivilite123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Media3 ExoPlayer for ultra low latency TV playback
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")

    // Glide for Channel Logos
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.3")
}
