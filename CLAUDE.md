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
- Running the app (`:desktopApp:run`) and packaging tasks still need an explicit request.
- Use SchneaggchatV3 (`~/Desktop/SchneaggchatV3`) as the reference for versions, Gradle setup and
  release workflows.
- After each work step: tick the item in `TODO.md` and add a line to the progress log below.
- Keep `PLAN.md` in sync when a decision or an API fact changes it.
- Do not send any write request to willhaben (or another marketplace) without asking first which
  listing to use for testing.
- Never log or commit cookie values, CSRF tokens or captured personal data.
- Never drop or reset the local database on updates: no `fallbackToDestructiveMigration`. Every
  schema change raises the version and ships a Room migration (prefer `AutoMigration`) with a
  migration test; schemas are exported to `shared/schemas/`.
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

Phase 3 (willhaben login) implemented; needs a real login test by the user:
`./gradlew :desktopApp:run` (or the IDE hot run), then "Log in to willhaben". The login window is
a JavaFX WebView (KCEF/Chromium was dropped because JetBrains Runtime ships a conflicting `libjcef`). Development runs write every willhaben API response to
`docs/api-samples/` (gitignored) for Phase 4.

Releases: every push to `main` runs `.github/workflows/release.yml` (first green test run: #2).

Open in Phase 3: throttling (3.6), automatic retry after `401` for API calls, verification of
session lifetime and CSRF behaviour with real samples (3.12).

Next: Phase 4 import, built from the captured samples of the first real login.

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
- 2026-10-18 – Implementation started (autonomous run, usage cap lifted by the user).
  Switched to TDD on request: logic written before was deleted and rebuilt test-first.
  Pushed: project setup (4587cc1), data layer with 76 tests (21f1bdf), ViewModels (afdf5cf),
  screens + app wiring + packaging (0fc7114), login (2f8a16e), update hint, response capture.
