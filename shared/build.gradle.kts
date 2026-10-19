plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// JavaFX classifier of the OS and CPU this build runs on (each release job builds on its own OS)
val javafxPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")
    when {
        os.contains("mac") -> if (arm) "mac-aarch64" else "mac"
        os.contains("win") -> "win"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        // Room generates an actual object for the expect AppDatabaseConstructor
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
                // Show the cause of failing tests in the build output (CI logs)
                testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }

    android {
       namespace = "com.lerchenflo.relisto.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)

            // DI - androidContext() for platform singletons
            implementation(libs.koin.android)

            // Networking - OkHttp engine for Ktor
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Navigation - Navigation 3 with type-safe routes
            implementation(libs.bundles.navigation3)

            // DI - Koin for dependency injection
            api(libs.bundles.koin)

            // Database - Room for the local listing database
            implementation(libs.bundles.room)

            // Local storage - DataStore for settings, KSafe for the encrypted session cookies
            api(libs.bundles.datastore)
            implementation(libs.ksafe)

            // Networking - Ktor for willhaben requests
            implementation(libs.bundles.ktor)

            // Async + date formatting
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)

            // Images - Coil for listing images, Reorderable for drag-to-reorder
            implementation(libs.coil3.coil.compose)
            implementation(libs.reorderable)
        }
        jvmMain.dependencies {
            // Main dispatcher on desktop
            implementation(libs.kotlinx.coroutinesSwing)

            // Networking - OkHttp engine for Ktor
            implementation(libs.ktor.client.okhttp)

            // Login - JavaFX WebView shows the real willhaben login page. JavaFX jars are per OS;
            // their POMs pick the platform with Maven profiles Gradle ignores, so modules are listed
            // one by one with the classifier of the OS this build runs on.
            listOf("base", "graphics", "controls", "media", "swing", "web").forEach { module ->
                implementation("org.openjfx:javafx-$module:${libs.versions.javafx.get()}:$javafxPlatform") { isTransitive = false }
            }
            implementation("org.openjfx:jdk-jsobject:${libs.versions.javafx.get()}:$javafxPlatform") { isTransitive = false }
        }
        commonTest.dependencies {
            // Testing - kotlin.test, coroutines-test, Turbine, AssertK, Ktor MockEngine
            implementation(libs.kotlin.test)
            implementation(libs.bundles.testing)
        }
        jvmTest.dependencies {
            // Testing - JUnit 5 runner for desktop tests
            implementation(libs.kotlin.test.junit5)

            // Testing - Room migration tests against the exported schemas
            implementation(libs.room.testing)

            // Testing - Compose UI tests on the desktop renderer
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)

    // Room KSP compiler - generates DAO implementations for every target
    add("kspJvm", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
}
