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
        mainClass = "com.lerchenflo.relisto.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Relisto"
            packageVersion = appVersion
            description = "Manage marketplace listings locally"
            vendor = "lerchenflo"
            // OkHttp/TLS, bundled SQLite, DataStore and the JavaFX WebView need these on top of the default runtime
            modules("java.instrument", "java.management", "java.naming", "java.net.http", "java.scripting", "java.sql", "jdk.unsupported", "jdk.crypto.ec", "jdk.xml.dom")

            linux {
                // Lowercase without spaces for DEB/RPM package names
                packageName = "relisto"
                shortcut = true
                menuGroup = "Office"
                iconFile.set(project.file("icons/relisto.png"))
            }

            windows {
                perUserInstall = true
                // Never change: lets a new MSI replace the installed version
                upgradeUuid = "e5b067a6-c9f4-46c0-8863-e7f15b81a1af"
                menu = true
                shortcut = true
                iconFile.set(project.file("icons/relisto.ico"))
            }

            macOS {
                bundleID = "com.lerchenflo.relisto"
                iconFile.set(project.file("icons/relisto.icns"))
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Development runs record willhaben API responses for analysis (gitignored folder)
tasks.withType<JavaExec>().configureEach {
    if (name == "run" || name.startsWith("hotRun")) {
        systemProperty("willhaben.captureDir", rootProject.layout.projectDirectory.dir("docs/api-samples").asFile.absolutePath)
    }
}
