# Nelko P21 / Olympia P22 roll tag: what Labelnise revealed

*Русская версия: [TAG_FORMAT.ru.md](TAG_FORMAT.ru.md)*

The format was reconstructed from the decompiled Labelnise app (jadx 1.5.1) and the app's server API.
The label catalog was exported from the app's server into `tools/catalog_P21_P22.json` (56 entries). The 55 entries for P22 ship with the app in `app/src/main/assets/labels.json`.

## Key points

1. **No encryption and no signature.** The tag is an NTAG203/213; the payload is stored in plain form in pages 4..7.
   `libencryption.so` / `READ_DECRYPTION` are only used for the ink cartridges of the Pocket inkjet printers.
2. **The printer reads the tag.** The app sends `1B 21 6F 0D 0A` over BLE. The printer replies with 16 bytes:
   `[status] [0C] [12 bytes of pages 5..7] [CRC16/MODBUS, big-endian]`. The CRC is computed by the printer.
3. **The app takes the label's look, size and background from the server catalog, not from the bytes.**
   The 12 bytes, as hex, serve as a *code*. The app requests
   `GET https://app.labelnize.com/api/label/getLabelByCode/<code>` (no authorization required) and receives a template:
   name, width and length, gap, background image (`bg`, i.e. the "design"), type.
   The app parses the bytes itself (`ConsumablesPaperBean.parse`) **only if the code is not in the catalog**
   or there is no network (in that case it first checks the local `LabelPaperDB` database).
   This explains the results of trial and error: any code outside the catalog yields a "New label" with dimensions
   from the local parser, which has its own quirks (see below). A code that happens to match another catalog entry
   gets that entry's dimensions.
4. Without a recognized tag, the app only shows a warning ("Unable to recognize original paper…")
   and lets you continue. It does not change the resolution, so that restriction lives in the printer firmware.

## What the bytes mean (from the catalog and the parse code)

Code = `k0 k1 k2 k3 k4 k5 k6 k7 k8 k9 k10 k11` = pages 5..7.

| k | field | what the catalog shows |
|---|---|---|
| 0 | — | always `01` |
| 1 | format | `12` — 14 mm with gap; `02` — 12 and 15 mm (old format); `22` — continuous tape and cable labels |
| 2 | **design/colour number** | `02` transparent, `03` white, `04`/`05`/`06` pink/blue/yellow (GAP03/05/04), `09`…`46` — decorative GAP06…GAP43. Values run like BCD (`09, 10, 11…19, 20…`; `0A`–`0F` are skipped) |
| 3–4 | batch | always `00 03` |
| 5 | type | `01` — with gap, `00` — continuous tape |
| 6 | gap | `12` for labels, `00` for tape, `05` for the cable label |
| 7 | — | always `12` |
| 8 | — | always `15` |
| 9 | length, mm | `1E`=30, `28`=40, `32`=50, `4B`=75, `0A`=tape |
| 10 | width (format `02`) | `0C`=12, `0F`=15; `0F` in other formats |
| 11 | width (formats `12`/`22`) | `0E`=14, `0C`=12 (tape); always `0F` in format `02` |

**Why the width came out wrong.** For 12 and 15 mm the catalog uses format `k1=02`: the width is in **k10** and k11 = `0F`.
For 14 mm it uses format `12`: the width is in **k11** and k10 = `0F`. If you change the width in the "wrong" byte or
change k1, the app reads 15 mm instead of 14, and so on.

**Why the colour did not change.** Colour and design are set only by k2, and it is a catalog entry number, not a colour.
For example, `01 12 22 …` is GAP42, not some "colour 0x22". When parsing locally (code not in the catalog), the app
only knows values 2..8 and shows everything else as white.

**Cable labels.** There are none for P22 in the catalog: every entry has `isCable=0`. The only similar one is `14x75+47mm`
(code `0122470003010512154B0F0E`, a flag label with a 47 mm tail), but it is listed only for P31S/P21.
Labelnise treats a label as a cable label if, in format `0x22`/`0x3x`, the high nibble of k5 is 5–8
(tail direction: 5 up, 6 right, 7 down, 8 left). No known roll is encoded this way.

**Previews.** Every catalog entry has an `image` field: a link to a picture of the roll on `oss.nelko.net`
(colour, design, shape). The app downloads them from the server at runtime and caches them; Nelko's images
themselves are not stored in this repository.

**Shapes.** The P21/P22 catalog currently has no non-rectangular/shaped label with an RFID code.
`getLabelPaperShapeList` returns only `Circle`. The shaped designs in the catalog are 14x40 rectangles with a
background image (`hasBg=1`). The die-cut shape is defined by the paper itself; the tag does not encode it.

## Server API (`https://app.labelnize.com/api`)

Works without authorization. Use JSON POST where POST is needed (the server does not accept form-urlencoded).

| Endpoint | Returns |
|---|---|
| `GET /label/getLabelByCode/{code}` | the template for a tag code |
| `POST /label/getLabelList` `{"pageNo":1,"pageSize":1000,"dev":"P22"}` | the whole catalog with codes → `tools/catalog_P21_P22.json` |
| `GET /label/getTypeList/P22` | categories: 12x40, 14x30, 14x40, 14x50, 14x75, 15x40, Continuous |
| `GET /templateVip/getDeviceList` | per-model config: dpi, sizes, paper types |

Empty for P21/P22: `getLabelPaperSetList` and the troubleshooting list `qa/getTroubleRemovalList`.
Everything else is accounts, templates, AI images and subscriptions, unrelated to tags.

### P22 config from the server (`getDeviceList`)

* `dpi: 203`, `maxWidth: 12` — the printable band is 12 mm ≈ 96 dots regardless of tape width.
* `paperType: "0,1,2"` (continuous, with gap, blank), `paperMinLength: 10`, `paperMaxLength: 1000`
  (75 for P21).
* `printingHeightList` — the actual printable length: 30→25, 40→34.5, 50→44.5, 75→69.5 mm
  (40→35.5 for P21). That is a 2.75 mm margin on each side: the app crops anything longer.
* `isEncrypt: 1` — for P22 the app asks the printer for the tag (`isPaperEncrypt`). Despite the name this is not
  encryption, just a "model with RFID" flag.
* `printingNonPaperNotify: 0` — the non-original paper warning is disabled for P22; printing proceeds without asking.
* `rfidProtocol: 1`, `paperCode: 011203000301121215280F0E` — the default paper is 14x40 White.

## How to get the label you want

Use the code of an **existing catalog entry**; do not assemble the bytes by hand:

```
python tools/make_tag.py --catalog GAP22 -o gap22.nfc
python tools/make_tag.py --catalog "15x40mm GAP White" -o w15.nfc
python tools/make_tag.py --code 010204000301121215280C0F --pages      # 12x40 pink
python tools/make_tag.py --width 14 --length 75 --color white -o w14x75.nfc
```

Without `--code` and `--catalog` the script picks the format from the width (12/15 → `02`, 14 → `12`). All tested
combinations land exactly on catalog entries, and `--color blue` with the blue roll's UID matches
`docs/dumps/012.nfc` byte for byte.

Tested:
* tags written with catalog codes are recognized by the printer and work as expected;
* decorative rolls (GAPxx) work too: the app picks up the matching design from the catalog.

Not verified:
* what the firmware checks when it lowers the resolution. The safest option is catalog codes: they are exactly
  the values the firmware sees on genuine rolls.
