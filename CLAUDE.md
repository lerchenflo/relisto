# Willhaben Updater

Compose Multiplatform desktop app to manage my own willhaben.at marketplace listings.

**Main goal:** handle listing text and images locally, push them to willhaben, keep listings that are
already on willhaben up to date, and renew expired listings. **Last phase:** publish the same local
listings on other marketplaces (Kleinanzeigen, Shpock, Facebook Marketplace).

## Documents

- `PLAN.md` – full plan: scope, willhaben API analysis (section 3), architecture (section 4),
  phases, decisions (section 6), other marketplaces (section 7).
- `TODO.md` – ordered to-do list; work top to bottom and tick items when done.
- `docs/willhaben-api.md` – confirmed willhaben API facts (created in Phase 3/4).
- `docs/api-samples/` – captured API responses (gitignored, may contain personal data).

## Rules for working on this project

- Product/scope decisions: ask with the interview method (AskUserQuestion). Implementation details
  (libraries, code structure, what infrastructure is needed): decide alone, following `PLAN.md` and
  the KMP skills (android-project-init, -dependency-setup, -module-structure, -data-layer,
  -error-handling, -di-koin, -navigation, -presentation-mvi, -compose-ui). Record notable choices
  in `PLAN.md` section 6.
- Test-driven development for all new logic (test-driven-development + android-testing skills):
  failing test first, then the code. Running `./gradlew :shared:jvmTest` is allowed.
- Commit regularly. Push to `origin main` only when the compile check below passes (every push to
  `main` will create a release).
- Use SchneaggchatV3 (`~/Desktop/SchneaggchatV3`) as the reference for versions, Gradle setup and
  release workflows.
- After each work step: tick the item in `TODO.md` and add a line to the progress log below.
- Keep `PLAN.md` in sync when a decision or an API fact changes it.
- Do not send any write request to willhaben (or another marketplace) without asking first which
  listing to use for testing.
- Never log or commit cookie values, CSRF tokens or captured personal data.
- Use the user's KMP skills for code (module structure, data layer, Koin, MVI, navigation, error
  handling), adapted to the module layout below.

## Project setup

| Item | Value |
|---|---|
| Modules | `:shared` (all app code, targets `jvm` + `android`), `:desktopApp` (`main.kt`, packaging), `:androidApp` (kept from the wizard, no features yet) |
| Package | `com.lerchenflo.willhabenupdater` |
| Java | 25 everywhere, same as SchneaggchatV3: `jvmToolchain(25)` + `java { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_25 }` in every module |
| Gradle daemon | JetBrains Runtime 25 (`gradle/gradle-daemon-jvm.properties`) |
| Versions | Gradle 9.8.0, Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 9.4.1 (`gradle/libs.versions.toml`) |
| Git remote | `origin` → https://github.com/lerchenflo/willhabenupdater.git |

Compile check (allowed without asking on this desktop machine):

```
./gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:compileDebugKotlin
```

Run the desktop app (only when asked): `./gradlew :desktopApp:run`

## Current status

Phase 1/2 in progress (first implementation run, 2026-10-18, stopped at the 90 % usage cap).

Done, not compiled yet:
- Version catalog (versions from SchneaggchatV3), `:shared` build with Room/KSP, Koin, Ktor,
  DataStore, Navigation 3, Coil, Reorderable, serialization.
- Core: `app/domain` (Result, DataError), `utilities` (UiText, DataErrorText, PriceFormatter,
  TimeUtils, AppDirectories), theme, navigation (Route, Navigator, ObserveAsEvents).
- Data: Room entities + DAOs + AppDatabase, RoomListingRepository, Preferencemanager (settings),
  willhaben catalog (category tree cache, suggestions, locations) + willhaben fields data source,
  Ktor client + safe call helpers, Koin `datasourceModule`, desktop platform module, `onAppStart()`.

Next, in this order:
1. Add `kotlinx-io-core` (0.9.1) to the catalog (used by the category tree cache).
2. Load the test-driven-development + android-testing skills; add test deps (kotlin-test,
   coroutines-test, turbine, assertk, ktor-client-mock); write tests first for PriceFormatter,
   RoomListingRepository (fake DAOs/ImageStorage), catalog repository (MockEngine), ViewModels.
3. Presentation: ListingsViewModel/Screen (list + selection), ListingEditorViewModel/Screen
   (autosave, images with drag-to-reorder + drop target), WillhabenTabViewModel/Screen
   (category picker, attributes, location, delivery), SettingsViewModel/Screen; `strings.xml`;
   `App.kt` with NavDisplay; `presentationModule`.
4. Android: `androidMain` platform module (DB builder, DataStore, ImageStorage, picker stub),
   `initKoin(context)` from `MainActivity`.
5. `desktopApp/main.kt`: call `onAppStart()`, window title/size; packaging config.
6. Compile check green → commit → push.
7. Release workflow (like SchneaggchatV3, but on every push to `main`): macOS DMG, Windows MSI,
   Linux DEB/RPM/Arch, Android APK signed with a committed sideload keystore; version
   `1.0.<run number>`, tag `v1.0.<run number>`.

## Progress log

- 2026-10-18 – willhaben web client analysed (public JS bundles + unauthenticated requests);
  `PLAN.md` and `TODO.md` written; scope and decisions set in interviews.
- 2026-10-18 – Project created with the KMP wizard (`:shared`, `:desktopApp`, `:androidApp`).
- 2026-10-18 – Switched all modules to Java 25 like SchneaggchatV3 (toolchain + source/target 25
  incl. Android), Gradle daemon on JetBrains Runtime 25. Compile check passed, class files are
  version 69 (Java 25).
- 2026-10-18 – Plan updated: "refresh" = renew expired listings; generic data model
  (`ListingEntity` + `ListingPublicationEntity`); Phase 8 other marketplaces added.
- 2026-10-18 – `CLAUDE.md` added; git repository initialised with remote `origin`.
- 2026-10-18 – Implementation started (autonomous run): dependencies, core, data layer for
  listings/settings/willhaben catalog. Stopped at the 90 % usage cap before the UI; WIP commit
  local only (not pushed, does not compile yet).
