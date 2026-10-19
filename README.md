# Relisto

Desktop app (Kotlin Multiplatform, Compose) to manage my own willhaben.at listings: text, price
and images live in a local database and are pushed to willhaben from there. Existing willhaben
listings can be imported, updated and renewed when they expire. Later the same listings can be
published on other marketplaces.

- Plan and willhaben API analysis: [PLAN.md](PLAN.md)
- Ordered to-do list: [TODO.md](TODO.md)
- Project notes and progress log: [CLAUDE.md](CLAUDE.md)

## Modules

- `shared` – all app code (Compose UI, Room, Ktor, Koin), targets desktop (JVM) and Android
- `desktopApp` – desktop entry point and packaging (DMG, MSI, DEB, RPM)
- `androidApp` – Android entry point

## Build

Java 25 is required (the Gradle daemon uses JetBrains Runtime 25, see `gradle/gradle-daemon-jvm.properties`).

- Run the desktop app: `./gradlew :desktopApp:run`
- Unit tests: `./gradlew :shared:jvmTest`
- Compile check: `./gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:compileDebugKotlin`
