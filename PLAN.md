# Relisto – Project Plan

Status: planning · Created: 2026-10-18 · Ordered work list: [TODO.md](TODO.md)

A Kotlin Multiplatform (Compose Multiplatform) desktop app to manage my own willhaben.at
marketplace listings. All listings live in a local database. Each listing has a title,
a description, a price and a set of images. The app talks to willhaben the same way the willhaben
website does, so listings can be imported, edited, published and have their status changed from the app.

**Main goal:** handle the text and images of my listings locally, push them to willhaben, keep the
listings that are already on willhaben up to date, and renew expired listings. **Last phase:** use
the same local listings for other marketplaces (Kleinanzeigen, Shpock, Facebook Marketplace).

---

## 1. Goal and scope

### In scope
- Local database of listings: title, description, price, images (plus the extra fields willhaben
  needs to publish: category, condition, location, delivery options).
- Fully usable offline: create, edit, duplicate, archive listings without a willhaben login.
- willhaben login inside the app (my own account only).
- Import all my existing willhaben listings into the local database (all verticals since 2026-10-19).
- Push local changes (text, price, images) to an existing willhaben listing.
- Create and publish a new free private marketplace listing from a local entry.
- Status actions on existing listings: deactivate, activate, mark reserved, remove reserved,
  mark sold, delete.
- Renew ("refresh") expired listings: willhaben listings run 45 days; the app shows which listings
  have expired and republishes them (only when free).
- Last phase: publish and update the same local listings on Kleinanzeigen, Shpock and
  Facebook Marketplace (see section 7).

### Out of scope (decision 2026-10-18)
- All paid extras: upselling / "Zusatzprodukte", TOP-Anzeige, bumps, product carts, payments,
  invoices / bill requests.
- ~~Verticals other than the private marketplace~~ (changed 2026-10-19, see below). Still out
  of scope unless decided later: creating or editing listings outside the private marketplace
  (cars/motorbikes with Eurotax, real estate, commercial marketplace `adTypeId` 69, boats) and
  job listings.
- Chat/messages, search alerts, browsing other people's listings.
- Paid "push to top" (bump) and fake bumps by deleting and re-creating listings.
- Android features: the wizard's `:androidApp` module stays in the project and must keep
  compiling, but features are built and tested for desktop first. Android-specific parts
  (login WebView, storage paths) are not planned yet. No iOS.

### Scope change (decision 2026-10-19)
- All verticals are in scope for import and renew ("Import + renew now"): Auto & Motor,
  real estate and the commercial marketplace are imported like marketplace listings, and
  expired ones are renewed when willhaben offers a free product for their ad type. Jobs are
  skipped. Details: `docs/willhaben-api.md`, section "Other verticals".

---

## 2. Feasibility summary

**Yes, the web client's server communication can be rebuilt.** Analysis on 2026-10-18 of the
public JavaScript bundles of willhaben.at and a few unauthenticated read-only requests shows:

- The website is a set of Next.js apps. The listing-management app is `bbx-adin`
  (`/bbx-adin/_next/...`, build `1064040-284338c6`), the search app is `bbx-search`
  (build `1066399-71e7607a`).
- All data calls go to a backend-for-frontend (BFF) at `https://www.willhaben.at/webapi`. The BFF
  proxies to the internal REST API `https://api.willhaben.at/restapi/v2` (visible in the `uri`
  fields of API responses).
- The API is JSON over HTTPS and hypermedia-driven: responses contain a `contextLinkList` with
  named links (`presave`, `imageList`, `selfLinkEdit`, `selfLinkActivate`, ...). The client follows
  those links instead of building URLs. This makes the flows easy to replicate and fairly robust.
- Authentication is a cookie session on the BFF (`BBX_JSESSIONID`) created by a Keycloak
  OpenID Connect login at `sso.willhaben.at`, plus a CSRF token cookie that must be echoed as a header.
- The hard part is **login**: the Keycloak login page sets a browser-fingerprint cookie and can
  show a Google reCAPTCHA v2. A pure HTTP login is therefore fragile. Plan: log in through an
  embedded browser window, then reuse the session cookies with a normal HTTP client.

No login was done during the analysis. Everything that needs a session is marked
"to verify". There is no separate API spike (decision 2026-10-18): each endpoint is verified the
first time it is implemented (Phases 3–6), with the debug response capture described in 4.6.

---

## 3. willhaben API analysis

### 3.1 Base URL, headers, cookies

| Item | Value |
|---|---|
| Base URL | `https://www.willhaben.at/webapi` |
| Upstream (not called directly) | `https://api.willhaben.at/restapi/v2` |
| `Accept` | `application/json` |
| `Content-Type` | `application/json` when the body is a JSON string (not for multipart uploads) |
| `X-WH-Client` | `api@willhaben.at;responsive_web;server;1.0.0;desktop` (last part = screen size: `desktop`, `tablet`, `phone`) |
| CSRF | For every non-GET request: header `x-bbx-csrf-token` = value of cookie `x-bbx-csrf-token` |
| Session cookie | `BBX_JSESSIONID` (BFF session) |
| Other cookies | `IADVISITOR` (visitor id), `x-bbx-application-token`, `iad-maintenance-ignore` (not needed) |
| `User-Agent` | Send a normal desktop browser UA (configurable) |

Link resolution rule (from the web client): a context link has `serviceName` and `relativePath`.
The URL is `{base}/{serviceName}{relativePath}`, for example `serviceName = "iad"`,
`relativePath = "/bap"` → `https://www.willhaben.at/webapi/iad/bap`.
Exception for image ordering: when `serviceName` is `iad`, use `iad-upload` instead (see 3.4).

Error responses are JSON. The web client reads either `{ title, message, ... }` or
`{ errorMessage }`. A `401` means the session is missing or expired.

### 3.2 Login (Keycloak OIDC behind the BFF)

| Item | Value |
|---|---|
| Start login | `GET https://www.willhaben.at/webapi/oauth2/authorization/sso` (the page `/iad/myprofile/login` redirects there; the web app also uses `/webapi/sso/login?r=<returnPath>`) |
| Identity provider | Keycloak, issuer `https://sso.willhaben.at/auth/realms/willhaben` |
| Client | `client_id=bbx-bff`, authorization code flow with PKCE (`S256`), confidential client (secret is on the BFF) |
| Callback | `https://www.willhaben.at/webapi/oauth2/code/sso` (BFF sets the session cookie) |
| Login form | `POST .../login-actions/authenticate?session_code=…&execution=…&client_id=bbx-bff&tab_id=…` with fields `username`, `password`, `rememberMe` |
| Anti-bot | `fingerprint-utils.js` (FingerprintJS 3.3.6) writes cookies `wh-fp` / `wh-ofp`; `login-rc.js` renders reCAPTCHA v2 when the server asks for it |
| "Stay logged in" | `rememberMe` checkbox → long-lived Keycloak SSO cookies on `sso.willhaben.at` |
| Silent re-login | web app uses `/webapi/sso/login?background-login`; with valid SSO cookies the Keycloak step completes without a form |
| Logout | `GET /webapi/logout?redirect_uri=https://www.willhaben.at/iad/logout` |
| Check session | `GET /webapi/userprofile-service/userprofile/me` → `204` when logged out, profile JSON (incl. `loginId`) when logged in |

Consequences for the app:
- We cannot run our own OAuth client (the client is confidential and its redirect URI is fixed).
- We do not store the password. The user logs in on the real willhaben login page inside an
  embedded browser, which handles fingerprinting, reCAPTCHA and 2FA naturally.
- After login we read the cookies of `www.willhaben.at` and `sso.willhaben.at` from the embedded
  browser's cookie store (including HttpOnly cookies) and give them to the HTTP client.
- When the BFF session expires (`401`), first try a silent re-login with the stored SSO cookies;
  only if that fails, open the login window again.

### 3.3 Endpoint catalogue (marketplace only)

Legend: **V** = seen in the JS bundle and checked with an unauthenticated request,
**B** = seen in the JS bundle, needs a session (verify when implementing), **I** = inferred (verify when implementing).

Public reference data (no login needed):

| Method | Path (relative to `/webapi`) | Purpose | |
|---|---|---|---|
| GET | `/iad/productgroups` (or `/iad/productgroups/{loginId}`) | Products per vertical. Free private marketplace = `adTypeId` 67, product id `67`, `price 0.00`, runtime 45 days, link `{serviceName: iad, relativePath: /bap}` | V |
| GET | `/iad/categorytree/withattributes/67` | Full marketplace category tree with attributes | V |
| GET | `/iad/bap/categorysuggestion/attributetree?title={title}&adTypeId=67&productId=67` | Category suggestions for a title | V |
| GET | `/iad/locations/{postcode}?isoCountryCode=AT` | Postcode → `{ locations: [{ id, description }] }` | V |

Account and listing reads:

| Method | Path | Purpose | |
|---|---|---|---|
| GET | `/userprofile-service/userprofile/me` | Session check, `loginId` | V (204 logged out) |
| GET | `/myatz/atverz/showpagedadsfromuser?rows=&page=&keyword=&statusFilters=&sort=&sortOrder=&vertical=bap` | My listings, paged. Response: `verticalList[].advertDetails[]`, `totalNumberOfAdvertDetails`. Each advert has `id`, `uuid`, `adTypeId`, `verticalId`, `advertEditStatusActionsList.contextLinkList` | V (401 logged out) |
| GET | `/iad/bap/{adId}` | Full editable listing JSON incl. `contextLinkList` | B |
| GET | `/iad/user/statistics` | Account statistics (optional) | B |

Create and edit:

| Method | Path / link | Body | Purpose | |
|---|---|---|---|---|
| GET | `/iad/bap?productId=67` | – | Empty listing template with `contextLinkList` (`presave`, ...) | I |
| POST | link `presave` | listing JSON | Creates the draft. Returns `adId`, `adUuid`, `productId`, new `contextLinkList` | B |
| POST | link `presaveAndConvert` | listing JSON | Draft save when the category is changed | B |
| POST | link `selfLinkFinish` (fallback `selfLinkSave`) | listing JSON | Submits the new listing | B |
| GET | `/iad/bap/{adId}` | – | Reload; must contain `selfLinkActivate` and no `paymentURI` for a free listing | B |
| PUT | link `selfLinkActivate` | – | Publishes the listing (web then shows `/iad/anzeigenaufgabe/fertig`) | B |
| PUT | link `selfLinkEdit` | listing JSON | Saves changes to an existing listing | B |
| PUT | `/p2p-aggregator/restapi/v2/p2pp/options/delivery` | `{ adId, deliveryOptions }` | Delivery options (pickup / shipping / PayLivery) | B |
| PUT | `/iad/bap/edit` | JSON (shape TBD) | Used by the "Versandoptionen" edit page | B |

Images:

| Method | Path | Body | Purpose | |
|---|---|---|---|---|
| POST | `/iad-upload/atimage/temp` | multipart, field `file` | Upload one image, returns a temp image with `reference` | B |
| DELETE | `/iad-upload/atimage/{reference}` | – | Discard an unused temp image | B |
| PUT | `/{svc}{imageList.relativePath}/order-and-associate` (`svc` = `iad-upload` when the link's `serviceName` is `iad`) | `{ "advertImage": [{ "reference": "…" }, …], "floorPlans": [] }` | Sets the full, ordered image list of a listing (new temp refs + existing refs) and returns the final list | B |

The web client uploads at most 4 images in parallel.

Status actions (my-listings page): each advert in `showpagedadsfromuser` has
`advertEditStatusActionsList.contextLinkList`. The link `id` tells the action, the URL comes from
the link. HTTP methods as used by the web client (verify per action when implementing Phase 5):

| Link id | Action | Method |
|---|---|---|
| `1` | Edit (navigate to edit form) | – |
| `2` | Deactivate | PUT |
| `3` | Delete | DELETE |
| `4` | Activate | PUT |
| `5` | Finalize (draft not yet published) | – (continue create flow) |
| `6` | Republish (expired listing) | POST, then the normal `presave` → finish → activate flow |
| `setAdSold` | Mark sold | PUT |
| `markAdReserved` / `removeMarkAdReserved` | Reserved on/off | PUT |
| `changeDeliveryOptions` | Edit delivery options | – (page) |
| `editRejectedAd` | Fix a rejected listing | – (edit flow) |
| `bulkDeleteGroupedAdsFromUser` | Bulk delete | DELETE, body `{ bulkChangeAdIds: [...] }`, response `{ failedAdIds: [...] }` |
| bulk activate / deactivate | Bulk status change | PUT, body `{ bulkChangeAdIds: [...] }` |
| `upsellingsActiveAd`, `requestBillLink` | Paid extras / invoices | **ignored (out of scope)** |

Delete/sold reason dialogs: `GET`/`POST /iad/deletereasons?adId={id}` and
`GET`/`POST /iad/soldreasons?adId={id}` (B). Before republishing, the web client checks in
`/iad/productgroups/{loginId}` whether the product is paid; if paid, it shows the upselling dialog.
The app instead refuses and tells the user.

Explicitly ignored endpoints: `/iad/upselling/*`, `/iad/products/privates/*` (carts, checkout),
`/iad/payment/*`, `/iad-upload/atimage/convert-pdf`, `/chat-api/*`, `/ad-search/*`, `/interceptor/*`, `/iad/logevent/*`.
Since 2026-10-19, `/iad/motor/*`, `/iad/estate/*` and `/iad/bapcom/*` are used for reading and
renewing other verticals (edit routes and copy links in `docs/willhaben-api.md`); creating new
listings there, Eurotax lookups and upselling stay unused.

### 3.4 Flow: create and publish a new listing

1. `GET /iad/productgroups` → find marketplace group `adTypeId` 67 with a product whose
   `price == 0`; keep its `id` (`productId`) and link (`/bap`). Abort if no free product exists.
2. `GET /iad/bap?productId=67` → listing template JSON with `contextLinkList`.
3. Fill the template: category (`categoryPath`, `treeAttributes`), title, description, price,
   condition, location, contact settings. Category suggestions:
   `/iad/bap/categorysuggestion/attributetree?title=…`.
4. `POST` link `presave` with the JSON → `adId`, `adUuid`, new `contextLinkList`.
   Store `adId` locally now, so an interrupted publish can resume.
5. Upload each image: `POST /iad-upload/atimage/temp` (max 4 in parallel).
6. `PUT …/order-and-associate` with the ordered list of temp references (link `imageList`).
7. `POST` link `selfLinkFinish` (or `selfLinkSave`) with the full JSON.
8. Optional: `PUT /p2p-aggregator/restapi/v2/p2pp/options/delivery`.
9. `GET /iad/bap/{adId}`. If a `paymentURI` link is present → stop, tell the user (paid, out of scope).
   Else `PUT` link `selfLinkActivate`.
10. The listing goes through willhaben's review; the status is read back on the next import.

### 3.5 Flow: edit an existing listing

1. `GET /iad/bap/{adId}` → current JSON + `selfLinkEdit` + `imageList`.
2. Conflict check: compare with the snapshot stored at the last sync (see 4.5).
3. Patch only the fields the app manages into the fresh JSON (title, description, price, ...).
4. `PUT` link `selfLinkEdit` with the patched JSON.
5. If images changed: upload new images as temp, then `PUT …/order-and-associate` with the full new
   order (existing references + new temp references); `DELETE` temp images that were not used.
6. Save the response as the new snapshot.

### 3.6 Listing JSON shape

The listing JSON is server-driven and generic: an attribute list (`attribute` entries with an
`attributeKey`), `categoryPath`, `treeAttributes` (`[{ attributeNode: { code }, selectedAttributeValueNodes: [{ code }] }]`),
`advertImage`, address/location fields and `contextLinkList`. The exact field names for title,
description, price, condition, location and delivery are read from the first captured responses
in Phase 4 (import) and documented in `docs/willhaben-api.md`.

Design decision: **treat the listing JSON as an opaque `JsonObject` and patch it.** The app always
starts from JSON that the server sent (template or current listing), changes only the managed
fields, and sends it back. Unknown fields survive untouched. This keeps the app working when
willhaben adds fields.

### 3.7 Open questions (answer while implementing; phase in brackets)

Answers go into `docs/willhaben-api.md`.

1. [6] Is `GET /iad/bap?productId=67` the template endpoint? (Inferred from the product link and
   the same pattern for cars, `/iad/motor/car?productId=…`.)
2. [4] Exact JSON keys for title, description, price, condition, postcode/location, contact, delivery.
3. [5] Which status actions need a request body; do delete/sold require a reason first?
4. [3] Session lifetime of `BBX_JSESSIONID`; does silent re-login with SSO cookies work over plain HTTP?
5. [3] Is the CSRF cookie set right after login or only after the first GET?
6. [5] Image limits: max count per listing, max file size, accepted formats.
7. [4] Does `showpagedadsfromuser` expect `vertical=bap` repeated or comma separated?
   **Answered:** the parameter does not filter; the list always contains all verticals.
8. [4/5] How are expired listings shown (status values), and is republish free for marketplace?

### 3.8 Risks and mitigations

| Risk | Mitigation |
|---|---|
| willhaben terms forbid robots/crawlers copying content and bulk messages. Automating my own account is not explicitly allowed either; the account could be flagged | Only my own listings, only user-triggered actions, no background loops, low request rate, normal browser behaviour. Accept the risk consciously |
| Unofficial API changes without notice | Follow context links instead of hard-coded URLs, opaque JSON patching, lenient deserialization, clear "willhaben API changed" error when an expected link is missing, log the build ids |
| Login blocked by reCAPTCHA/fingerprinting | Real login page in an embedded browser (JavaFX WebView), so fingerprint and reCAPTCHA run in a real browser engine |
| Rate limiting / bot detection | Max 1 normal request at a time, max 4 parallel image uploads, back-off on `429`/`503` |
| Untested write endpoints (no spike) damage a real listing | Implement reads first (Phase 4). Test every write endpoint first on one listing chosen for testing, with confirmation dialogs; keep the previous snapshot so content can be restored |
| Duplicate or test listings break willhaben's listing rules | First publish test with a real item I want to sell; never re-create listings just to push them to the top |
| Session cookies are credentials | Store encrypted, never log cookie values, "log out" clears everything |

---

## 4. App architecture

### 4.1 Tech stack

All versions: latest stable at setup time, kept in `gradle/libs.versions.toml` (verify before sync).

| Concern | Choice |
|---|---|
| Java | Java 25 everywhere, same as the SchneaggchatV3 project: `jvmToolchain(25)` and `java { sourceCompatibility/targetCompatibility = VERSION_25 }` in `:shared`, `:desktopApp` and `:androidApp`; Gradle daemon on JetBrains Runtime 25 (`gradle/gradle-daemon-jvm.properties`); Gradle 9.8.0, Kotlin 2.4.20, AGP 9.4.1 |
| UI | Compose Multiplatform (desktop first, Android module kept), Material 3 |
| Architecture | MVI (State / Action / Event, ViewModel, Root/Screen split) |
| DI | Koin |
| Local DB | Room KMP + `sqlite-bundled` driver, KSP |
| Preferences | DataStore (settings, defaults) |
| Networking | Ktor client (OkHttp engine), content negotiation with kotlinx.serialization (`ignoreUnknownKeys`, `isLenient`), HttpCookies with a custom persistent storage, logging with redaction |
| Login browser | JavaFX WebView (WebKit) in a Swing window. KCEF/Chromium was dropped: JetBrains Runtime (needed by hot reload) ships its own `libjcef` that crashes KCEF. No manual cookie paste |
| Secret storage | KSafe (KMP encrypted key-value store) for the session cookies – check JVM desktop support in Phase 1 |
| Images | Coil 3 for display, FileKit for the file picker, Compose drag-and-drop for dropping files, `javax.imageio` for resize/thumbnails |
| Navigation | Navigation 3 with `@Serializable` routes |
| Errors | `Result<T, DataError>`, `UiText` |
| Packaging | Compose `nativeDistributions` (DMG first, MSI/DEB later) |

### 4.2 Module and package layout

Gradle modules as created by the KMP wizard (decision 2026-10-18):

| Module | Content |
|---|---|
| `:shared` | All app code: features, data, UI. Targets `jvm()` (desktop) and `android`. |
| `:desktopApp` | `main.kt` (window) and `compose.desktop` packaging only |
| `:androidApp` | Wizard's Android entry point, kept compiling, no features planned yet |

Inside `:shared`, features are packages with `domain/`, `data/`, `presentation/`. Code goes to
`commonMain` where possible; desktop-only code (app data dir, login window, image tools) to
`jvmMain`; Android `actual`s to `androidMain`.

Marketplaces are adapters behind one interface, so other marketplaces (section 7) plug in
without changing the listing core. willhaben is the first adapter.

```
shared/src/
  commonMain/kotlin/com/lerchenflo/relisto/
    app/                    App.kt, theme, navigation/ (Route.kt, Navigator)
    listings/
      domain/               Listing, ListingImage, Price, Condition, ListingRepository
      data/                 ListingEntity, ListingImageEntity, ListingDao, mappers, RoomListingRepository
      presentation/         list/ (ListingListViewModel, screen), editor/ (ListingEditorViewModel, screen)
    marketplace/
      domain/               Marketplace (enum), MarketplaceAdapter (interface), Publication, SyncState,
                            RemoteStatus, StatusAction, PublishProgress, PublicationRepository
      data/                 ListingPublicationEntity, PublicationImageEntity, PublicationDao, PublicationRepositoryImpl
      presentation/         import/, publish/ (progress dialogs), actions/, marketplace tabs in the editor
      willhaben/
        data/               WillhabenApi, dto/, AdJsonPatcher, ContextLinks, mappers, WillhabenAdapter
        auth/               PersistentCookiesStorage, WillhabenSessionRepository, SilentReLogin
        presentation/       login/ (login window host), account chip, willhaben fields (category, attributes, delivery)
    settings/               defaults (postcode, condition, delivery), data folder
    activity/               sync/activity log
    datasource/
      database/             AppDatabase, migrations
      network/              HttpClientFactory, safeCall, CSRF + X-WH-Client plugin
      preferences/          DataStore factory, PreferenceManager
    sharedUi/               image grid, price field, status chips, dialogs
    utilities/              UiText, price formatting (de-AT), hashing
    di/                     Koin modules
  jvmMain/kotlin/com/lerchenflo/relisto/
    platform/               AppDirs (actual), ImageTools, login window
  androidMain/kotlin/com/lerchenflo/relisto/
    platform/               Android actuals (minimal stubs until Android features are planned)
desktopApp/src/main/kotlin/com/lerchenflo/relisto/
  main.kt                   window
```

Root package: `com.lerchenflo.relisto`. App display name: "Relisto".

### 4.3 Data model (Room)

Generic from the start (decision 2026-10-18): the listing table holds only marketplace-neutral
content; everything marketplace-specific lives in one publication row per listing and marketplace.

`ListingEntity` (content, marketplace-neutral)
- `id: Long` (PK, auto)
- `title: String`, `description: String`
- `priceCents: Long?` (euro cents; null = not set)
- `condition: String?` (neutral enum: new, like new, used, defective – mapped per marketplace)
- `postcode: String?`
- `archived: Boolean`
- `createdAt`, `updatedAt` (epoch millis)

`ListingImageEntity`
- `id: Long` (PK), `listingId: Long` (FK → listing, cascade delete), `position: Int`
- `fileName: String` (file in the app image folder, named by SHA-256)
- `sha256: String`, `width: Int`, `height: Int`

`ListingPublicationEntity` (one row per listing and marketplace; unique `listingId + marketplace`)
- `id: Long` (PK), `listingId: Long` (FK → listing, cascade delete)
- `marketplace: Marketplace` (`WILLHABEN`, later `KLEINANZEIGEN`, `SHPOCK`, `FACEBOOK`)
- `remoteId: String?`, `remoteUuid: String?`, `remoteUrl: String?`
- `remoteStatus: RemoteStatus?` (active, inactive, reserved, sold, expired, draft, in review, rejected – willhaben values from Phase 4)
- `expiresAt: Long?` (willhaben: 45 days runtime; used for the "expired / expiring" filter and renew)
- `marketplaceFieldsJson: String?` (marketplace-specific fields, for willhaben: category path, tree attributes, location id, delivery options)
- `remoteSnapshotJson: String?` (last listing JSON from the marketplace, base for patching)
- `remoteSnapshotHash: String?`
- `syncState: SyncState`, `lastSyncedAt: Long?`, `lastError: String?`

`PublicationImageEntity` (which local image is which remote image)
- `publicationId: Long` (FK, cascade), `imageId: Long` (FK → listing image, cascade)
- `remoteReference: String?`, `remoteUrl: String?`

`ActivityLogEntity`
- `id`, `listingId: Long?`, `marketplace: Marketplace?`, `timestamp`, `action`, `success: Boolean`, `message: String?`

Settings (DataStore): default postcode, default condition, default delivery options, window
size/position, last import time.

### 4.4 Image storage

- App data folder: macOS `~/Library/Application Support/Relisto/`, Windows
  `%APPDATA%\Relisto\`, Linux `~/.local/share/relisto/`.
- `images/` holds a copy of every imported image, named `<sha256>.<ext>`; the DB stores only the
  file name. Same image added twice = one file.
- `thumbs/` holds small previews for the list.
- Before upload: optional downscale (long side ≤ 2048 px, JPEG) – limits confirmed in Phase 5.
- Orphan cleanup on start: delete files that no image row references.

### 4.5 Sync model

The local database is the master for content I edit. Each marketplace is the master for its
status (review, active, expired, sold) and its listing ids. The sync state is kept per
publication (listing + marketplace).

`SyncState`:

| State | Meaning |
|---|---|
| `LOCAL_ONLY` | Never published |
| `SYNCED` | Local content equals the last snapshot |
| `LOCAL_CHANGES` | Edited locally after the last sync |
| `REMOTE_CHANGED` | willhaben content differs from the snapshot, no local edits (changed on the website) |
| `CONFLICT` | Both changed – user picks "keep local" or "take willhaben" |
| `PUBLISHING` | Create flow started (adId may exist); can be resumed |
| `ERROR` | Last sync step failed; message in the activity log |

Conflict detection uses `remoteSnapshotHash` of the managed fields only (title, description,
price, images, category, location). All writes are user-triggered (button per listing, or
"sync all changed"); no background polling.

### 4.6 Network layer

- `HttpClientFactory`: base URL, default headers (`Accept`, `X-WH-Client`, `User-Agent`), timeouts.
- Custom Ktor plugin: for non-GET requests add `x-bbx-csrf-token` from the cookie store.
- `PersistentCookiesStorage` (implements Ktor `CookiesStorage`), encrypted at rest.
- `safeCall { }` maps exceptions and status codes to `DataError.Network`:
  `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `TOO_MANY_REQUESTS`, `SERVER_ERROR`,
  `NO_INTERNET`, `SERIALIZATION`, plus app-specific `API_CHANGED` (expected context link missing)
  and `PAID_PRODUCT` (`paymentURI` present).
- Willhaben error bodies (`title`/`message`/`errorMessage`) are kept and shown to the user.
- On `401`: one silent re-login attempt, then the request is repeated once; otherwise
  "login required" state.
- Throttling: a single request queue (`Mutex`/semaphore of 1) plus a separate limit of 4 for
  image uploads; exponential back-off with jitter for `429`/`503`.
- Logging: method, path, status, duration. Cookie and CSRF values are always redacted.
- Debug response capture (replaces the skipped API spike): in debug runs, every willhaben request
  and response body is written to `docs/api-samples/<timestamp>_<method>_<path>.json` with
  cookies and personal data removed. These samples are the source for DTOs and for
  `docs/willhaben-api.md`. The folder is in `.gitignore`.

### 4.7 Login in the app

1. "Log in to willhaben" opens a separate window with a JavaFX WebView at
   `https://www.willhaben.at/iad/myprofile/login?r=/iad/myprofile/myadverts`.
2. The user logs in on the real page (reCAPTCHA / 2FA work as in a browser). "Eingeloggt bleiben"
   should stay checked.
3. When the window URL reaches `https://www.willhaben.at/iad/myprofile/myadverts`, read all
   cookies for `www.willhaben.at` and `sso.willhaben.at`, store them, close the window.
4. `GET /userprofile-service/userprofile/me` confirms the session; the account chip shows the user.
5. The stored cookies (incl. the login server's "stay logged in" cookies) are preloaded into the
   WebView, so a later login window often completes without
   typing the password (SSO "remember me").

### 4.8 UI

Single main window, master–detail:

- **Top bar**: New listing, Import from willhaben, Sync changed, account chip (login state).
- **Left pane – listing list**: cover thumbnail, title, price, willhaben status chip, sync badge;
  search field; filters (status, sync state, archived); multi-select for bulk status actions.
- **Right pane – editor**:
  - Title (live character counter), description (multi-line), price (de-AT format `1.234,50 €`).
  - Image grid: add via file picker or drag-and-drop, reorder by drag, remove, first image = cover.
  - Condition and postcode (marketplace-neutral).
  - One tab per marketplace (only "willhaben" until Phase 8): category (suggestions from the title +
    tree picker), required category attributes, location, delivery options, remote status, expiry date.
  - Actions per marketplace tab: Publish / Push changes, status actions (deactivate, activate,
    reserved, sold, delete), Renew (expired listings), "Open on willhaben" link.
- **Renew view**: filter "expired / expiring soon" with a bulk "Renew" button.
- **Dialogs**: publish progress (step list), confirmation for destructive actions, conflict
  resolution (side-by-side local vs. willhaben).
- **Settings**: defaults, data folder, re-login, log out / clear session.
- **Activity log** panel: what was sent to willhaben and the result.

### 4.9 Security

- No password is stored; only session cookies, encrypted.
- Cookie and CSRF values never appear in logs or in the activity log.
- Log out calls `/webapi/logout`, clears the cookie store and the WebView data folder.
- Recorded API samples (`docs/api-samples/`) contain personal data → listed in `.gitignore`.

---

## 5. Phases

Detailed, ordered checklist: [TODO.md](TODO.md).

| Phase | Goal | Result |
|---|---|---|
| 1 | Project bootstrap | Empty desktop app with DI, DB, network, navigation, error handling |
| 2 | Local listings | Fully working offline listing manager (title, description, price, images, category, condition/attributes, location, delivery) |
| 3 | willhaben session | JavaFX WebView login window, KSafe cookie storage, session check, silent re-login, logout |
| 4 | Import (read only) | All my marketplace listings imported with images and status; JSON shapes documented |
| 5 | Edit and status actions | Push edits and images; deactivate/activate/reserve/sold/delete/republish |
| 6 | Create and publish | New free marketplace listing published from a local entry |
| 7 | Polish and packaging | Conflict UI, activity log, settings, error texts, DMG |
| 8 | Other marketplaces | Same local listings published and updated on Kleinanzeigen, Shpock, Facebook Marketplace |

No separate API spike (decision 2026-10-18). Phase 2 gives a useful app even if willhaben blocks
the API approach. Reads (Phase 4) come before writes (Phases 5–6), so the captured read responses
define the JSON shapes before anything is sent to willhaben.

## 6. Decisions

Made (all 2026-10-18, confirmed by me in the interview):
- Desktop first. Wizard modules kept: `:shared`, `:desktopApp`, `:androidApp` (Android kept, no
  Android features planned yet).
- Java 25 everywhere incl. Android, same settings as SchneaggchatV3; Gradle daemon on
  JetBrains Runtime 25.
- Project notes and progress log in `CLAUDE.md`.
- "Refresh" listings on willhaben = renew expired listings (no bumps).
- Last phase: other marketplaces Kleinanzeigen, Shpock, Facebook Marketplace.
- Generic data model now: content in `ListingEntity`, marketplace data in `ListingPublicationEntity`.
- No paid extras, payments, upselling; private marketplace only (verticals widened on 2026-10-19, see below).
- Root package `com.lerchenflo.relisto`, app name "Relisto".
- Login only through an embedded browser (now JavaFX WebView, see 4.1); no manual cookie paste; no password storage.
- Session cookies stored with KSafe.
- Local DB is master for content (conflict dialog when both sides changed); willhaben is master
  for status.
- Editor manages title, description, price, images, category, condition + category attributes,
  location, delivery options.
- No separate API spike; endpoints are verified while implementing, reads before writes.

- Every push to `main` creates a release (DMG, MSI, DEB, RPM, Arch, APK), like SchneaggchatV3.
- Android APKs are signed with a sideload key committed to the repo (no release keys to manage).
- Test-driven development for all logic.

Technical defaults (decided during implementation):
- Listing JSON is patched, not rebuilt.
- Every edit is saved immediately (no save button); text fields are filled from the database
  only once so database updates never overwrite typing.
- Images: native AWT file dialog and drag-and-drop on desktop, stored by SHA-256 hash; Coil
  scales thumbnails, no separate thumbnail folder.
- willhaben condition ("Zustand") and handover ("Übergabe") attributes are derived from the
  neutral condition and the delivery options, so the willhaben tab does not show them.
- Android uses stubs for image import and file picker until Android features are planned.
- All willhaben verticals are imported and renewed (2026-10-19, interview). The ad type is stored
  in the willhaben fields JSON (`adTypeId`, no schema change; `null` = marketplace). The free
  product per ad type comes from `/iad/productgroups`; without a free product the app refuses
  before copying. For non-marketplace JSON only keys that already exist are patched.

Open (asked via interview when the phase starts):
- Exact attribute fields shown per category (Phase 2/4, after the first import samples).
- Which listing to use for the first write tests (Phase 5).
- Order of the other marketplaces and per-marketplace login approach (Phase 8).

---

## 7. Other marketplaces (Phase 8)

Goal: the same local listing (title, description, price, images, condition, postcode) is
published and kept up to date on more marketplaces. Each marketplace is one `MarketplaceAdapter`
implementation with its own login, field mapping and publication row.

`MarketplaceAdapter` (sketch):
- `login()` / `sessionState()` / `logout()`
- `importMyListings()` → remote listings for matching and import
- `publish(listing, publication)` / `pushChanges(listing, publication)`
- `statusActions(publication)` / `runStatusAction(publication, action)`
- `renew(publication)` (if the marketplace has expiry)
- `requiredFields(listing)` → extra fields the editor tab must show (category, attributes, ...)

Before each marketplace is built, it gets its own analysis like section 3 (web traffic, login,
endpoints, risks), written into the plan and asked through the interview.

| Marketplace | Known so far | Main risks |
|---|---|---|
| Kleinanzeigen (kleinanzeigen.de) | No public API for private sellers; web app to analyse | Listings need a German location (check against an Austrian postcode); bot protection; terms |
| Shpock | No public API; web and app clients to analyse | Unofficial API changes; login flow unknown |
| Facebook Marketplace | No API for private listings; only browser automation in the embedded browser | Meta terms forbid automation, high risk of account block; frequent UI changes |
