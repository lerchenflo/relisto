plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// Set by the release workflow; local builds use the defaults
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")
val appVersionCode = providers.gradleProperty("appVersionCode").getOrElse("1").toInt()

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.lerchenflo.willhabenupdater"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lerchenflo.willhabenupdater"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
    }
    signingConfigs {
        // Fixed key committed to the repo so every release APK installs over the previous one
        // without managing secrets. Only for sideloading, never for a store upload.
        create("sideload") {
            storeFile = file("sideload.keystore")
            storePassword = "willhaben-sideload"
            keyAlias = "sideload"
            keyPassword = "willhaben-sideload"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
    }
}