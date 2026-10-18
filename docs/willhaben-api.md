# willhaben API – confirmed facts

Recorded with a real login on 2026-10-18. Only listing data is documented here; profile and
contact data are never captured, logged or documented.

## Session

- `GET /webapi/userprofile-service/userprofile/me`: `200` = logged in (profile in `userData`,
  the app only reads `firstName`/`lastName` for display), `204` = logged out.
- Login runs through `sso.willhaben.at` (Keycloak, client `bbx-bff`) and ends on
  `https://www.willhaben.at/iad/myprofile/myadverts`.

## My listings – `GET /webapi/myatz/atverz/showpagedadsfromuser?page=1&rows=30&vertical=bap`

- `vertical=bap` does not filter: `verticalList[]` always contains all verticals
  (`verticalId` 5 = Marktplatz, 3 = Auto & Motor, ...). The app keeps `adTypeId == 67`.
- `totalNumberOfAdvertDetails`: total over all verticals.
- `verticalList[].advertDetails[]`:
  - `id` (string), `uuid`, `adTypeId`, `productId`, `verticalId`
  - `description`: the listing title
  - `startDate`, `endDate`: ISO-8601 with offset (`2023-10-08T11:44:00+02:00`)
  - `advertStatus`: `{ id: "passive", description: "Passiv", statusId: 61 }`
  - `advertEditStatusActionsList`: `{ statusId: 61, statusName: "Abgelaufen", contextLinkList.contextLink[] }`;
    seen action ids: `6` "Neu veröffentlichen", `3` "Löschen", `requestBillLink` (paid, ignored)
  - `advertImageList.advertImage[]`: `mainImageUrl`, `referenceImageUrl`, `thumbnailImageUrl`
    (all `null` for expired listings)
  - `attributes.attribute[]`: `{ name, values[] }`, e.g. `PRICE`, `PRICE_FOR_DISPLAY`
  - `contextLinkList.contextLink[]`: `selfLink`, `iadAdvertLink` (`/iad/object?adId=…`), `adDetailLink`, `categoryTree`

## One listing (edit form) – `GET /webapi/iad/bap/{adId}`

Listing fields used by the app:

| Key | Meaning |
|---|---|
| `heading` | title |
| `description` | description as HTML (only `<br />` seen) |
| `price` | number in euro (`200.0`) |
| `postCode`, `locationId`, `location` | postcode, willhaben location id, location label |
| `categoryTreeId` | leaf category tree id |
| `categoryPath.path[]` | `{ label, code }` from top to leaf |
| `treeAttributes[]` | `{ attributeNode { code }, selectedAttributeValueNodes[] { code } }`; `Zustand` (neu, MintCondition, gebraucht, defekt), `Uebergabe` (PICKUP, Versand) |
| `endDate` | expiry, ISO-8601 (`2023-10-08T09:44:00.000+00:00`) |
| `adId`, `adUuid`, `adTypeId`, `productId` | ids |

The same JSON also holds contact data of the seller; the app stores the JSON locally as the base
for later edits but never reads, logs or captures those fields.

Context links of a listing: `selfLinkEdit` (`/bap/edit`), `selfLinkSave` (`/bap/save`),
`selfLinkActivate` (`/bap/activate/{id}`), `selfLinkDelete`, `imageList` (`/atimage/{id}`),
`imageOrder` (`/atimage/{id}/order`), `moveImageToPosition`, `swapImages`, `categoryTree`.
