# Relisto – To-do list

Open work only, top to bottom. Done work is in the progress log in [CLAUDE.md](CLAUDE.md);
background and details: [PLAN.md](PLAN.md). Product and scope decisions are asked in an interview
(AskUserQuestion) before they are made.

**Main goal:** handle listing text and images locally → push them to willhaben → keep existing
willhaben listings up to date → renew expired listings. **Last phase:** same listings on other marketplaces.
**Release:** the first production release comes after Phases 5–7 (everything except other marketplaces).

## Next session (needs the user)

- [ ] N.1 User test of the new UI on desktop and Android: phone editor top bar, photo picker, login hint,
  push button and conflict dialog, "Läuft ab" filter, bulk push and willhaben menu in the selection bar,
  activity log ("Verlauf"), publish dialog with steps.
- [ ] N.2 Live test with "Rasentraktor Kart" (willhaben id 704262322): "Neu veröffentlichen", then a small
  text change pushed (5.1/5.2), then deactivate + activate (5.3). Check the captured responses in
  `docs/api-samples/` step by step (mask contact data, never print it). **Deactivate the listing again at
  the end** (the user does not want offers).
- [ ] N.3 Live checks while doing N.2: does `PUT selfLinkEdit` work and send the listing back to review;
  image limits (count, size, formats); then update `docs/willhaben-api.md` (5.8).
- [ ] N.4 Phase 6 live: verify that `GET /iad/bap?productId=67` is the template and that `categoryTreeId` +
  `categoryPath.path` select the category (6.2); first real publish only with an item the user wants to sell,
  asked first (6.6). Deactivate again if it is only a test.

## Phase 5 – open

- [ ] 5.4 Delivery options: `PUT /p2p-aggregator/restapi/v2/p2pp/options/delivery` (and `/iad/bap/edit` if
  needed). Needs a captured request from the web client first (body shape unknown).
- [ ] 5.8 Verify each action's HTTP method and body against real responses; update `docs/willhaben-api.md`.

## Phase 7 – Polish and release

- [ ] 7.5 Backup / export of database + images as a zip (desktop save dialog, Android `CreateDocument`).
- [ ] 7.8 Document session lifetime, CSRF cookie timing and silent re-login in `docs/willhaben-api.md` (live).
- [ ] 7.9 Run the `kmp-crash-guard` and `flow-stability` skills on the finished flows.
- [ ] 7.10 Production release: from here on never drop the database (Room migrations only).
- [ ] 7.12 UX pass with the `mobile-ux-conventions` skill over all screens and dialogs (button order,
  touch targets, empty/loading states), checked on the Android emulator too.
- [ ] 7.13 Clear all compile warnings (`./gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin
  :androidApp:compileDebugKotlin`), remove unused resources (e.g. `import_running`).
- [ ] 7.14 Architecture cleanup against the KMP skills (module structure, data layer, Koin, MVI, navigation,
  error handling); e.g. `marketplace/data` depends on `marketplace/willhaben/data`, `Daos.kt` holds every DAO.

## Multi-selection

- [ ] M.4 Bulk publish to all marketplaces (after Phase 8).

## Phase 8 – Other marketplaces (last step)

Same local listings on Kleinanzeigen, Shpock and Facebook Marketplace. Each marketplace is a new
`MarketplaceAdapter` (interface to be extracted from the willhaben code) and a new tab in the editor.

- [ ] 8.1 Ask: order of the marketplaces (2026-10-22: "decide later").
- [ ] 8.2 Per marketplace, before code: analyse its web client like PLAN.md section 3; write the result
  into PLAN.md section 7; ask the open decisions.
- [ ] 8.3 Per marketplace: login + session storage (embedded browser like willhaben); login/logout in the
  settings and the market's logo in the "logged in" row below the home header (like willhaben).
- [ ] 8.4 Per marketplace: field mapping (category, condition, location, delivery) and the editor tab.
- [ ] 8.5 Per marketplace: import my listings and match them to existing local listings (confirm in UI).
- [ ] 8.6 Per marketplace: publish, push changes, images, status actions, renew if the marketplace has expiry.
- [ ] 8.7 Cross-marketplace actions: "publish to all", "mark sold everywhere".
- [ ] 8.8 Kleinanzeigen: check whether an Austrian postcode is accepted (Kleinanzeigen is Germany-only).
- [ ] 8.9 Facebook Marketplace: browser automation only; decide via interview whether the block risk is acceptable.
