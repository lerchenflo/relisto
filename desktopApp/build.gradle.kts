import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// Set by the release workflow (-PappVersion=1.0.<run number>); MAJOR must be > 0 for DMG/MSI
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.lerchenflo.willhabenupdater.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Willhaben Updater"
            packageVersion = appVersion
            description = "Manage willhaben listings locally"
            vendor = "lerchenflo"
            // OkHttp/TLS, bundled SQLite and DataStore need these on top of the default runtime
            modules("java.instrument", "java.management", "java.naming", "java.net.http", "java.sql", "jdk.unsupported", "jdk.crypto.ec")

            linux {
                // Lowercase without spaces for DEB/RPM package names
                packageName = "willhaben-updater"
                shortcut = true
                menuGroup = "Office"
            }

            windows {
                perUserInstall = true
                upgradeUuid = "e5b067a6-c9f4-46c0-8863-e7f15b81a1af"
                menu = true
                shortcut = true
            }

            macOS {
                bundleID = "com.lerchenflo.willhabenupdater"
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
