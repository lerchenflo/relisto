# Relisto – To-do list

Open work only, top to bottom. Done work is in the progress log in [CLAUDE.md](CLAUDE.md);
background and details: [PLAN.md](PLAN.md). Product and scope decisions are asked in an interview
(AskUserQuestion) before they are made.

**Main goal:** handle listing text and images locally → push them to willhaben → keep existing
willhaben listings up to date → renew expired listings. **Last phase:** same listings on other marketplaces.
**Release:** the first production release comes after Phases 5–7 (everything except other marketplaces).

## All willhaben verticals (user request 2026-10-19)

Import and renew listings of every willhaben vertical, not only the private marketplace (`adTypeId` 67).
The user's account has Auto & Motor listings (`adTypeId` 21, product 2443, motorbikes).

- [ ] V.4 Live test with "Beta RR 50" (chosen 2026-10-19; the user starts it in the app, auto renew
  stays on for all listings). Motor JSON is captured and
  uses the marketplace text keys (see `docs/willhaben-api.md`). Watch: `presave` link in the motor
  copy, product 40021 accepted, `paymentURI`; free motor listings run 7 days only.

## Next session

- [ ] N.2 Live test of "Neu veröffentlichen" with "Rasentraktor Kart" (willhaben id 704262322); check the
  captured responses in `docs/api-samples/` step by step (mask contact data, never print it).

## Multi-selection (user request 2026-10-18)

- [ ] M.1 Checkbox for every listing (today only expired ones), "select all visible", clear selection.
- [ ] M.2 Bulk actions on the selection: renew expired (exists for expired ones), archive / unarchive,
  delete with one confirmation that asks each time: only locally, or locally and on willhaben
  (status action `3` + delete reason; decided 2026-10-19).
- [ ] M.3 Bulk deactivate / activate / reserved / sold on willhaben (after 5.3).
- [ ] M.4 Bulk publish to all marketplaces (after Phase 6 and Phase 8).

## Phase 5 – Update existing willhaben listings and status actions

- [ ] 5.1 Push changes (text, price, attributes): reload `GET /iad/bap/{adId}`, conflict check against
  the snapshot, `AdJsonPatcher`, `PUT` link `selfLinkEdit`, save the new snapshot.
- [ ] 5.2 Push images: temp upload + `order-and-associate` (exists in the republish flow), `DELETE`
  unused temp images; document image limits.
- [ ] 5.3 Status actions from `advertEditStatusActionsList`: deactivate (`2`), activate (`4`),
  reserved on/off, sold (+ sold reasons), delete (`3`, + delete reasons). Hide paid actions.
- [ ] 5.4 Delivery options: `PUT /p2p-aggregator/restapi/v2/p2pp/options/delivery` (and `/iad/bap/edit` if needed).
- [ ] 5.5 Conflict dialog: local vs. willhaben side by side, "keep local" / "take willhaben".
- [ ] 5.6 Renew: "expiring soon" filter; a failed activate after finish leaves a draft on willhaben –
  resume it instead of copying again.
- [ ] 5.7 Confirmation dialogs for destructive actions; activity log for every write.
- [ ] 5.8 Verify each action's HTTP method and body against the real responses; update `docs/willhaben-api.md`.

## Phase 6 – Create and publish new willhaben listings

- [ ] 6.1 `GET /iad/productgroups`: pick the free private marketplace product (`adTypeId` 67, price 0); abort if none.
- [ ] 6.2 Template `GET /iad/bap?productId=67` (verify; inferred endpoint).
- [ ] 6.3 Fill the template with `AdJsonPatcher`; `presave` → store `remoteId` at once, state `PUBLISHING`.
- [ ] 6.4 Images, `selfLinkFinish`, delivery options, reload, stop on `paymentURI`, `selfLinkActivate`
  (same steps as `WillhabenRepublisher`; share the code).
- [ ] 6.5 Publish progress dialog with step list; resume an interrupted publish from the stored `remoteId`.
- [ ] 6.6 First real publish with an item the user wants to sell.

## Phase 7 – Polish and release

- [ ] 7.1 Activity log panel with filter per listing and marketplace.
- [ ] 7.2 Import: mark listings that are gone on willhaben.
- [ ] 7.3 Clear "willhaben changed its API" message everywhere (`PublishError.MissingLink` exists).
- [ ] 7.4 "Open on willhaben" link per listing.
- [ ] 7.5 Backup / export of database + images as a zip.
- [ ] 7.6 Window: remember size/position, app icon.
- [ ] 7.7 Parallel image uploads (today one request at a time).
- [ ] 7.8 Document session lifetime, CSRF cookie timing and silent re-login in `docs/willhaben-api.md`.
- [ ] 7.9 Run the `kmp-crash-guard` and `flow-stability` skills on the finished flows.
- [ ] 7.10 Production release: from here on never drop the database (Room migrations only).
- [ ] 7.11 Throttle requests to willhaben so the API is not abused and the account is not banned:
  pause between requests (import detail GETs, image downloads/uploads, bulk and auto renew), at most
  one request at a time, back off on `429`/`503`; keep 7.7 (parallel uploads) within that limit.

## Phase 8 – Other marketplaces (last step)

Same local listings on Kleinanzeigen, Shpock and Facebook Marketplace. Each marketplace is a new
`MarketplaceAdapter` (interface to be extracted from the willhaben code) and a new tab in the editor.

- [ ] 8.1 Ask: order of the marketplaces.
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
