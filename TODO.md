# Willhaben Updater – To-do list

Work top to bottom. Each phase ends with a usable state. Background and details: [PLAN.md](PLAN.md).
Progress log and project rules: [CLAUDE.md](CLAUDE.md).
Decisions that come up during the work are asked in an interview (AskUserQuestion) before they are made.

**Main goal:** handle listing text and images locally → push them to willhaben → keep existing
willhaben listings up to date → renew expired listings. **Last phase:** same listings on other marketplaces.

## Phase 1 – Project bootstrap

- [x] 1.1 Project created with the KMP wizard: modules `:shared`, `:desktopApp`, `:androidApp`, package `com.lerchenflo.willhabenupdater`.
- [x] 1.2 Java 25 everywhere like SchneaggchatV3: `jvmToolchain(25)` + `java { VERSION_25 }` in all three modules, Gradle daemon JetBrains Runtime 25. Compile check passed.
- [x] 1.3 `CLAUDE.md` with project rules and progress log.
- [x] 1.4 Git repository with remote `origin` (https://github.com/lerchenflo/willhabenupdater.git), local `main` tracks `origin/main`; `.gitignore` extended (`docs/api-samples/`, `*.har`).
- [x] 1.4b First commit + push; README rewritten for the project.
- [x] 1.5 Remove wizard sample code (`Greeting`, `GreetingUtil`, sample tests).
- [x] 1.5b Release workflow on every push to `main` (like SchneaggchatV3): macOS DMG, Windows MSI, Linux DEB/RPM/Arch, Android APK (sideload keystore in repo, no release keys needed).
- [x] 1.5c Test setup for TDD (kotlin-test, coroutines-test, Turbine, AssertK, Ktor MockEngine).
- [x] 1.6 Run the `android-project-init` skill, adapted to the `:shared` / `:desktopApp` / `:androidApp` layout: version catalog, Koin, Room KMP + KSP + `sqlite-bundled`, DataStore, Ktor (OkHttp engine, content negotiation, logging, cookies), Coil 3, Navigation 3, `Result`/`DataError`, `UiText`.
- [ ] 1.7 Add KSafe and the login web view library to the catalog (moved to Phase 3). FileKit not needed: native AWT file dialog is used.
- [x] 1.8 `AppDirs` for the app data folder (macOS / Windows / Linux) with `images/`, `thumbs/`, database file; Android `actual` stub.
- [x] 1.9 Main window: size, minimum size, light/dark theme. Open: remembered size/position, app icon.
- [x] 1.10 Empty master–detail screen wired through Navigation 3 and Koin.

## Phase 2 – Local listings (no login needed)

- [x] 2.1 Room entities + DAOs: `ListingEntity`, `ListingImageEntity`, `ListingPublicationEntity`; `AppDatabase` with schema export. `PublicationImageEntity` and `ActivityLogEntity` follow when first used (Phase 4/5).
- [x] 2.2 Domain models (`Listing`, `ListingImage`, `Price`, `Condition`, `Marketplace`, `Publication`, `SyncState`, `RemoteStatus`) + `ListingRepository` and `PublicationRepository` (Flow-based) + mappers.
- [ ] 2.3 `MarketplaceAdapter` interface (see PLAN.md section 7) - moved to Phase 4, when the first implementation exists.
- [x] 2.4 Image store: copy added files into `images/` named by SHA-256, read size, create thumbnails, orphan cleanup on start.
- [x] 2.5 Listing list (left pane): thumbnail, title, price, status chip, sync badge; search; filters (status, sync state, archived, expired).
- [x] 2.6 Editor (right pane): title with character counter, description, price in de-AT format (euro cents in the DB), condition, postcode.
- [x] 2.7 Image grid in the editor: file picker (FileKit), drag-and-drop from Finder, reorder by drag, remove, first image = cover.
- [x] 2.8 willhaben tab in the editor – category picker: load the public category tree (`/iad/categorytree/withattributes/67`), cache it, suggestions from the title (`/iad/bap/categorysuggestion/attributetree`).
- [x] 2.9 willhaben tab – category attributes: dynamic form built from the cached category tree attributes.
- [x] 2.10 willhaben tab – location: postcode lookup (`/iad/locations/{postcode}?isoCountryCode=AT`); default postcode in settings.
- [x] 2.11 willhaben tab – delivery options: local model (pickup, shipping, shipping price, PayLivery on/off), stored in `marketplaceFieldsJson`.
- [x] 2.12 Validation, unsaved-changes tracking, "save locally".
- [x] 2.13 New, duplicate, archive and delete listings locally (delete with confirmation).
- [x] 2.14 Settings screen (DataStore): default postcode, default condition, default delivery options.

Result: complete offline listing manager.

## Phase 3 – willhaben session

- [ ] 3.1 `HttpClientFactory`: base URL `https://www.willhaben.at/webapi`, `Accept`, `X-WH-Client: api@willhaben.at;responsive_web;server;1.0.0;desktop`, browser `User-Agent`, timeouts.
- [ ] 3.2 CSRF plugin: add `x-bbx-csrf-token` header (value from the cookie) to every non-GET request.
- [ ] 3.3 `PersistentCookiesStorage` (Ktor `CookiesStorage`) backed by KSafe.
- [ ] 3.4 Logging with redaction of cookies and CSRF; debug response capture into `docs/api-samples/`.
- [ ] 3.5 `safeCall` + mapping of status codes and willhaben error bodies to `DataError` (incl. `API_CHANGED`, `PAID_PRODUCT`).
- [ ] 3.6 Request throttling: 1 normal request at a time, 4 parallel image uploads, back-off on `429`/`503`.
- [ ] 3.7 Ask: KCEF download vs. JetBrains Runtime's built-in JCEF. Then set up the embedded browser with a persistent cookie store in the app data folder.
- [ ] 3.8 Login window: open `https://www.willhaben.at/iad/myprofile/login?r=/iad/myprofile/myadverts`, detect arrival on `/iad/myprofile/myadverts`, read cookies of `www.willhaben.at` and `sso.willhaben.at`, store them, close.
- [ ] 3.9 Session check `GET /userprofile-service/userprofile/me` (204 = logged out); account chip in the top bar.
- [ ] 3.10 On `401`: silent re-login via `/webapi/oauth2/authorization/sso` with the stored SSO cookies, repeat the request once; else show "login required" and offer the login window.
- [ ] 3.11 Logout: `GET /webapi/logout?redirect_uri=https://www.willhaben.at/iad/logout`, clear KSafe cookies and the browser cookie store.
- [ ] 3.12 Verify and document in `docs/willhaben-api.md`: session lifetime, when the CSRF cookie appears, whether silent re-login works over plain HTTP.

## Phase 4 – Import my willhaben listings (read only)

- [ ] 4.1 Fetch `GET /myatz/atverz/showpagedadsfromuser` (marketplace only) with paging; check how `vertical` must be encoded.
- [ ] 4.2 Fetch each listing with `GET /iad/bap/{adId}`; store the raw JSON as `remoteSnapshotJson` of the willhaben publication.
- [ ] 4.3 From the captured samples: document the JSON keys for title, description, price, category, attributes, location, delivery, images, status values and expiry date in `docs/willhaben-api.md`.
- [ ] 4.4 Lenient DTOs (`ignoreUnknownKeys`) for the list response and context links; listing detail stays a `JsonObject`.
- [ ] 4.5 Mapper remote → local: content into `ListingEntity`, willhaben fields + status + `expiresAt` into `ListingPublicationEntity`; snapshot hash of the managed fields.
- [ ] 4.6 Download listing images into the image store; link them in `PublicationImageEntity` (`remoteReference` / `remoteUrl`).
- [ ] 4.7 Matching by publication `remoteId`: new remote listing → new local listing + publication; known listing → update or mark `REMOTE_CHANGED` / `CONFLICT`; listing gone on willhaben → mark it.
- [ ] 4.8 Import UI: button, progress, summary (new / updated / conflicts / expired), activity log entries.

Result: all my willhaben listings are in the local database.

## Phase 5 – Update existing willhaben listings, renew, status actions (first writes)

Before the first write test: ask which listing to use for testing.

- [ ] 5.1 `ContextLinks` helper: find a link by id, build `{base}/{serviceName}{relativePath}`, `iad` → `iad-upload` rule for image ordering, `API_CHANGED` when a link is missing.
- [ ] 5.2 `AdJsonPatcher`: write managed fields into a fresh listing JSON, keep all other fields.
- [ ] 5.3 Push changes (text, price, attributes): reload `GET /iad/bap/{adId}`, conflict check against the snapshot, `PUT` link `selfLinkEdit`, save the new snapshot.
- [ ] 5.4 Push images: `POST /iad-upload/atimage/temp` for new images, `PUT …/order-and-associate` with the full order, `DELETE` unused temp images; document image limits.
- [ ] 5.5 Delivery options: `PUT /p2p-aggregator/restapi/v2/p2pp/options/delivery` (and `/iad/bap/edit` if needed).
- [ ] 5.6 **Renew expired listings** (link id `6`): only when the product is free (check `/iad/productgroups/{loginId}`), then presave → finish → activate; "expired / expiring soon" filter with bulk renew.
- [ ] 5.7 Status actions from `advertEditStatusActionsList`: deactivate (`2`), activate (`4`), reserved on/off, sold (+ sold reasons), delete (`3`, + delete reasons). Hide paid actions (`upsellingsActiveAd`, `requestBillLink`).
- [ ] 5.8 Bulk deactivate / activate / delete for multi-selection.
- [ ] 5.9 Confirmation dialogs for destructive actions; activity log for every write.
- [ ] 5.10 Verify each action's HTTP method and body against the real responses; update `docs/willhaben-api.md`.

## Phase 6 – Create and publish new willhaben listings

- [ ] 6.1 `GET /iad/productgroups`: pick the free private marketplace product (`adTypeId` 67, price 0); abort if none.
- [ ] 6.2 Template `GET /iad/bap?productId=67` (verify; inferred endpoint).
- [ ] 6.3 Fill the template with `AdJsonPatcher` (category, attributes, title, description, price, location).
- [ ] 6.4 `POST` link `presave` → store `remoteId` / `remoteUuid` in the publication at once, state `PUBLISHING`.
- [ ] 6.5 Upload images + `order-and-associate`.
- [ ] 6.6 `POST` link `selfLinkFinish` (fallback `selfLinkSave`).
- [ ] 6.7 Set delivery options.
- [ ] 6.8 Reload; stop with a message if `paymentURI` exists; else `PUT` link `selfLinkActivate`.
- [ ] 6.9 Publish progress dialog with step list; resume an interrupted publish from the stored `remoteId`.
- [ ] 6.10 First real publish with an item I want to sell; then state `SYNCED` and status from the next import.

## Phase 7 – Polish and packaging

- [ ] 7.1 Conflict dialog: local vs. marketplace side by side, "keep local" / "take remote".
- [ ] 7.2 Activity log panel with filter per listing and marketplace.
- [ ] 7.3 `DataError` → `UiText` messages; clear "willhaben changed its API" message.
- [ ] 7.4 "Open on willhaben" link per listing.
- [ ] 7.5 Backup / export of database + images as a zip.
- [ ] 7.6 Run the `kmp-crash-guard` and `flow-stability` skills on the finished flows.
- [ ] 7.7 Packaging: `nativeDistributions` DMG (macOS) first, app icon, version number; MSI/DEB later.
- [ ] 7.8 Check that `:androidApp` still compiles (no Android features).

## Phase 8 – Other marketplaces (last step)

Same local listings on Kleinanzeigen, Shpock and Facebook Marketplace. Each marketplace is a new
`MarketplaceAdapter` and a new tab in the editor; the listing core does not change.

- [ ] 8.1 Ask: order of the marketplaces.
- [ ] 8.2 Per marketplace, before code: analyse its web client like PLAN.md section 3 (login, endpoints, images, status, risks, terms); write the result into PLAN.md section 7; ask the open decisions.
- [ ] 8.3 Per marketplace: login + session storage (embedded browser like willhaben).
- [ ] 8.4 Per marketplace: field mapping (category, condition, location, delivery) and the editor tab.
- [ ] 8.5 Per marketplace: import my listings and match them to existing local listings (by title/images, confirm in UI).
- [ ] 8.6 Per marketplace: publish, push changes, images, status actions, renew if the marketplace has expiry.
- [ ] 8.7 Cross-marketplace actions: "publish to all selected marketplaces", "mark sold everywhere" (deactivate/delete on the other marketplaces when sold on one).
- [ ] 8.8 Kleinanzeigen: check whether an Austrian postcode is accepted (Kleinanzeigen is Germany-only).
- [ ] 8.9 Facebook Marketplace: browser automation only; decide via interview whether the block risk is acceptable before building it.
