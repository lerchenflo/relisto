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
  (`verticalId` 5 = Marktplatz, 3 = Auto & Motor, ...). Since 2026-10-19 the app imports every
  listing whose ad type has a known edit route (see "Other verticals" below); jobs are skipped.
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

## Other verticals (Auto & Motor, Immobilien)

Read from the web client's public bundles (`/bbx-adin/`, build `1064040-284338c6`) and the
my-adverts list on 2026-10-19.

Vertical ids (`verticalId`): `5` = BAP (Marktplatz), `3` = MOTOR (Auto & Motor), `2` = ESTATE
(Immobilien), `1` = JOB.

Ad type ids (`adTypeId`):

| Vertical | Ad types |
|---|---|
| ESTATE | `1`, `2`, `3`, `4`, `5`, `6`, `8`, `15`, `16` |
| MOTOR | `20` car (`MOTOR_CAR`), `21` motorbike & quad (`MOTOR_MC`), `25` van/truck (`MOTOR_VAN_TRUCK`), `26` caravan (`MOTOR_CARAVAN`) |
| BAP | `67` private (`BAP_SALESOBJECT`), `68` webstore (`BAP_WEBSTOREOBJECT`), `69` commercial (`BAP_SALESOBJECT_PRO`) |
| JOB | `401`, `402` |

Edit-form JSON per vertical, as the web client builds the route:

| Vertical / ad type | Route |
|---|---|
| BAP (`67`, `68`) | `GET /iad/bap/{adId}` |
| BAP commercial (`69`) | `GET /iad/bapcom/{adId}` |
| ESTATE | `GET /iad/estate/{adId}` |
| MOTOR | `GET /iad/motor/get/{adId}` |

Motor edit JSON (`GET /iad/motor/get/{adId}`, recorded 2026-10-19 for four motorbikes, `adTypeId` 21):
- Same text keys as the marketplace: `heading`, `description`, `price`, `postCode`, `locationId`,
  `location`, `endDate`, `adId`, `adUuid`, `adTypeId`, `productId` (old listings: `2443`), `verticalId` 3.
- No marketplace category: `categoryTreeId` is `0`, no `treeAttributes`. Vehicle data are plain keys
  (`make`, `model`, `year`, `month`, `mileage`, `engineEffect`, `engineVolume`, `fuel`, `color`,
  `vehicleType`, `nationalCode`, `equipment`, ...); `condition` is a code string (e.g. `"70"`).
- Also contains contact data (name, phone, email, street, IP address): never read or logged.
- Links: `selfLinkSave` (`/motor/save/mc`), `selfLinkEdit` (`/motor/edit/mc`), `selfLinkActivate`,
  `selfLinkDelete`, `imageList` / `imageOrder` (`/atimage/{adId}`), `advertURI` (`/motor/get/{adId}`).

Status action links seen in my-adverts (`advertEditStatusActionsList.contextLinkList`):
- `6` "Neu veröffentlichen": `/bap/copy/{adId}` or `/motor/copy/{adId}` on service `iad` (POST).
- `3` "Löschen": `/myatz/v1/bap/{adId}` or `/myatz/v1/motor/{adId}` on `publicapi`.
- The listing's `selfLink` is `/atverz/user/{userUuid}/ad/{adId}` for every vertical.

New motor listing template: `GET /iad/motor/{car|mc|van_truck|caravan}?productId=…`.

### Free products – `GET /webapi/iad/productgroups`

Public, but needs the `X-WH-Client` header (without it: `400`). Free products (`price` `0.00`)
per ad type, from `productContextLinkList.productContextLink[]`:

| Ad type | Product id | Runtime | Group note |
|---|---|---|---|
| `67` Marktplatz | `67` | 45 days | |
| `20` Gebrauchtwagen | `40020` | 7 days | "Kostenlos bis € 22.000" |
| `21` Motorrad & Quad | `40021` | 7 days | "Kostenlos bis € 8.000" |
| `25` Nutzfahrzeug & Pickup | `40025` | 7 days | |
| `26` Wohnwagen & Wohnmobil | `40026` | 7 days | |

Real estate and commercial marketplace groups have no free product. Older motor listings carry
`productId` `2443`.

### Renew flow of the web client (`/iad/myprofile/renewad`)

1. `POST` the status link `6` (copy) → new draft with a new `adId`. The "my adverts" page then sets
   `productId` to a product of the listing's ad type from `/iad/productgroups/{loginId}` (replacing the
   copied one) and `POST`s the draft to its `presave` link.
2. The form opens at `/iad/anzeigenaufgabe/motor?adId=…` (marketplace: `/iad/anzeigenaufgabe/marktplatz?adId=…`).
3. Motor only, "pakete" step: bundles from `GET /iad/upselling/{adId}`; choosing one is
   `PUT /iad/upselling/new/{adId}` (the free bundle's button reads "Jetzt veröffentlichen").
4. Reload the listing; if there is no `paymentURI` link and `selfLinkActivate` exists, `PUT` activate.

The app never calls the upselling endpoints: it copies, presaves, finishes, reloads and stops
when `paymentURI` is present.
