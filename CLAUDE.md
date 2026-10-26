# Relisto

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
- Never log, capture or commit personal data, passwords, session cookies, CSRF tokens or login
  codes. The account name may only be shown in the app UI. HTTP logging stays at INFO (no bodies,
  sensitive headers masked, login/OAuth URLs filtered; see `HttpLoggingTest`). Response capture
  only covers listing endpoints. The project is only about listings, never about user data.
- Every UI string exists in German (default, `composeResources/values/`) and English
  (`values-en/`); the app follows the system language on phone and desktop (English systems get
  English, every other language German).
- Never drop or reset the local database on updates: no `fallbackToDestructiveMigration`. Every
  schema change raises the version and ships a Room migration (prefer `AutoMigration`) with a
  migration test (`AppDatabaseMigrationTest`, room-testing); schemas are exported to `shared/schemas/`.
  Exception until the first production release (after Phase 7): the user allows dropping the
  database, but cheap real migrations are still preferred. Never edit an exported schema JSON; a
  red test run with a changed entity and an unchanged version overwrites it - restore it from git.
- Use the user's KMP skills for code (module structure, data layer, Koin, MVI, navigation, error
  handling), adapted to the module layout below.

## Project setup

| Item | Value |
|---|---|
| Modules | `:shared` (all app code, targets `jvm` + `android`), `:desktopApp` (`main.kt`, packaging), `:androidApp` (kept from the wizard, no features yet) |
| Package | `com.lerchenflo.relisto` |
| Java | 25 everywhere, same as SchneaggchatV3: `jvmToolchain(25)` + `java { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_25 }` in every module |
| Gradle daemon | JetBrains Runtime 25 (`gradle/gradle-daemon-jvm.properties`) |
| Versions | Gradle 9.8.0, Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 9.4.1 (`gradle/libs.versions.toml`) |
| Git remote | `origin` → https://github.com/lerchenflo/relisto.git |

Compile check (allowed without asking on this desktop machine):

```
./gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:compileDebugKotlin
```

Run the desktop app (only when asked): `./gradlew :desktopApp:run`

## Current status

Phases 1-6 built; live tests outstanding (see `TODO.md` "Next session"). Phase 7 in progress.
- willhaben: import (all verticals), renew (single, bulk, automatic), push changes + images with conflict
  check and comparison dialog, status actions (single and bulk), publish new marketplace listings with a
  progress dialog, activity log of every write. Requests are spaced 300 ms apart, one at a time; image
  uploads up to four at a time.
- UI: one root Scaffold (insets, snackbars), phone layout below 720 dp, follows the system language,
  login hint on the home screen while logged out.
- Only marketplace listings are created by the app; motor code is kept for import/renew.

Next: user test of the UI (N.1), live tests (N.2-N.4), then Phase 7 (7.3-7.14).

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
- 2026-10-18 – KCEF replaced by JavaFX WebView (JetBrains Runtime crash). Login tested OK by the
  user. Privacy rules added (no personal data or secrets in logs/captures), German default UI,
  import step A (fetch + count).
- 2026-10-18 – Import step B: listings, images and willhaben fields stored locally, status in list.
- 2026-10-18 – Phase 5 part 1: republish (single, bulk, automatic on start), status section in the
  willhaben tab, replaced-listing guard with DB migration 1 → 2, update icon in the header.
  TODO.md cleaned up to open work only. 275 tests green.
- 2026-10-19 – willhaben login moved from the home header into the settings; the header shows a
  market icon with the login state (opens the settings) and an import icon. One app-wide `AccountViewModel`.
- 2026-10-19 – Home: row below the header lists the logged-in marketplaces with their logo
  (willhaben favicon in `drawable/market_willhaben.png`); import stays an icon in the header.
- 2026-10-19 – All verticals: web client analysed (bbx-adin bundles), import and renew for Auto & Motor
  and other ad types with a free product, ad type kept in the willhaben fields JSON (no schema change).
- 2026-10-19 – Category picker step by step (breadcrumb, ranked search); sold or deactivated listings
  no longer count as expired (their past end date made them renewable before); renew always sets the
  ad type's free product like the web client. Motor JSON documented.
- 2026-10-19 – Editor willhaben tab shows the section ("Auto & Motor") instead of marketplace category,
  details and delivery for other verticals. Decisions: live test with "Beta RR 50", auto renew for all
  listings, bulk delete asks each time (local / local + willhaben).
- 2026-10-19 – Renamed to Relisto everywhere: package `com.lerchenflo.relisto`, Android ids, bundle ID,
  resources package, theme, data folder `Relisto`, DB `relisto.db`, GitHub repo `lerchenflo/relisto`.
  No data migration (user decision); the old data folder is not used anymore.
- 2026-10-19 – Multi-selection: checkbox on every listing, select all / all expired, bulk renew (expired
  ones only), archive / restore, local delete with one confirmation.
- 2026-10-19 – Live renew "Beta RR 50": activate failed (motor drafts need a package). Renew now picks
  the free package (`PUT upselling/new`, never a paid one) and continues a remembered draft
  (`pendingDraftId`) instead of copying again. Images: willhaben serves WebP (stored as JPEG via
  TwelveMonkeys); the import loads all images from `/atimage/{adId}` (none when that fails).
- 2026-10-19 – Live: "Aprilia SX 50" renewed (→ "in Warteschlange"). willhaben's copy replaces the expired
  listing, so the publication now follows the draft right after the copy; drafts ("Bearbeitung") get
  "Fertigstellen" (finish instead of copy), deactivated listings are never renewed.
- 2026-10-19 – List rows show the willhaben logo plus a state icon (pen = draft, timer = expired, check =
  online, hourglass = in review, tag = sold, pause = deactivated, bookmark = reserved) instead of text.
- 2026-10-19 – Status actions (5.3): links from my-adverts stored per import (`remoteActionsJson`, DB v3
  with AutoMigration + test); willhaben tab buttons for activate, deactivate, reserved, sold, delete
  (sold/delete confirmed); `PUT`/`DELETE` like the web client; "Inaktiv" shown as deactivated.
- 2026-10-19 – Bulk delete asks "nur lokal" or "lokal und auf willhaben" (DELETE per listing, one import
  at the end; a listing whose willhaben delete failed stays locally).
- 2026-10-19 – Android crash (`ClassNotFoundException` AndroidModulesKt) fixed: after the folder move
  `:shared:bundleLibRuntimeToDirAndroidMain` stayed UP-TO-DATE with an old partial output (378 of 618
  classes); deleting `shared/build/intermediates/runtime_library_classes_dir` + `androidApp/build`
  fixed it, app runs on the emulator. List: willhaben state aligned right.
- 2026-10-19 – Import marks listings willhaben no longer lists (`SyncState.REMOTE_GONE`, cloud-off icon,
  status/expiry/actions cleared, local listing kept); a listing that comes back syncs normally.
- 2026-10-19 – Phone layout: below 720 dp the list fills the screen and a listing opens as its own
  screen (back arrow + system back via `ui-backhandler`); content stays clear of the status bar
  (`safeDrawingPadding`); editor buttons wrap. Texts say "device"/"Gerät" instead of "computer".
- 2026-10-22 – Paid-package stop names the cheapest package and price ("Basic - 14 Tage für 4,99 €")
  and says nothing was booked. V.4 dropped: new listings only on the marketplace (motor: one free
  listing per user); motor import/renew code kept.
- 2026-10-22 – One root Scaffold in `App.kt` like SchneaggchatV3 (`contentWindowInsets = safeDrawing`:
  system bars, camera cutout, keyboard; the only snackbar host); screens have no Scaffold of their own.
  Phone editor: top bar with back arrow and icon buttons. Android: photo picker + image storage
  (content URIs, WebP/HEIC stored as JPEG), long-press to reorder images.
- 2026-10-22 – Push changes (5.1/5.2) built: `WillhabenListingUpdater` (reload, conflict check against the
  snapshot, patch, `PUT selfLinkEdit`, images only when their fingerprint changed, new snapshot); shared
  `WillhabenImageUploader`; activity log table (DB v4, AutoMigration). Home shows a login hint while
  logged out. Language follows the system on desktop too; local builds (1.0.0) skip the update hint.
- 2026-10-22 – Willhaben tab: "Änderungen übertragen" and conflict comparison (keep local / take willhaben).
  List: filter "Läuft ab" (expired or past half the 45-day runtime, marketplace only), bulk push of
  checked listings with local changes, willhaben menu for bulk activate/deactivate/reserve/sold (M.3).
- 2026-10-22 – Activity log: every write (push, take willhaben, renew, status actions) is logged with
  its result; screen "Verlauf" from the home header (all) or the willhaben tab (one listing), filters
  "only failures" and marketplace (shown with more than one). Entries older than 180 days are removed.
- 2026-10-22 – Phase 6 built: new marketplace listings are published from the willhaben tab (confirmation,
  progress dialog with steps, required fields and attributes checked first, interrupted publish continues
  with its draft). `WillhabenDraftFinisher` shares images/finish/package/activate with the renew.
- 2026-10-22 – Requests to willhaben are spaced (300 ms, `RequestSpacing`, also image downloads); image
  uploads run up to four at a time (7.7, 7.11). TODO.md rewritten to open work only; paused by the user
  (token budget).
- 2026-10-26 – 7.3/7.4: unexpected willhaben responses and missing links say "willhaben changed its interface,
  check for a Relisto update"; willhaben tab has "Auf willhaben öffnen" (not for drafts or gone listings).
- 2026-10-26 – 7.6: app icon (white price tag with renew arrow on teal, two colours) for the window, installers
  (`desktopApp/icons`: icns/ico/png) and the Android launcher; window size, position and maximized state
  remembered in `window.properties` (ignored when its title bar is on no screen).
