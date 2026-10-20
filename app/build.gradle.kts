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
        versionCode = 21
        versionName = "2.1.0"

        val privateSalt = (project.findProperty("TIVILITE_SALT") as String?) ?: "COMMUNITY_DEFAULT_SALT"
        buildConfigField("String", "LICENSE_SALT", "\"$privateSalt\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val envPath = System.getenv("RELEASE_KEYSTORE_PATH")
            val envStorePass = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val envAlias = System.getenv("RELEASE_KEY_ALIAS")
            val envKeyPass = System.getenv("RELEASE_KEY_PASSWORD")

            val propsFile = rootProject.file("keystore.properties")
            val props = java.util.Properties()
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }

            val keystoreFile = when {
                !envPath.isNullOrEmpty() -> file(envPath)
                props.containsKey("storeFile") -> file(props.getProperty("storeFile"))
                file("tivilite.jks").exists() -> file("tivilite.jks")
                else -> null
            }

            val storePass = envStorePass
                ?: props.getProperty("storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD")

            val alias = envAlias
                ?: props.getProperty("keyAlias")
                ?: "tivilite"

            val keyPass = envKeyPass
                ?: props.getProperty("keyPassword")
                ?: storePass

            if (keystoreFile != null && keystoreFile.exists() && !storePass.isNullOrEmpty()) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
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
