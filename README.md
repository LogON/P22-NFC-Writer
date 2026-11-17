# P22 NFC Writer

*Русская версия: [README.ru.md](README.ru.md)*

Android app that writes the NFC tag of **Olympia P22 / Nelko P21** label rolls onto blank
**NTAG213** tags (NTAG203 also works; the printer rejects NTAG216). It lets you re-label or
reuse rolls by writing the "paper code" the printer reads to identify the loaded roll
(size, colour, design).

> **Disclaimer.** This is an independent, unofficial interoperability tool. It is **not affiliated
> with or endorsed by** Nelko, Olympia, Labelnise/Labelnize or JieLi. Product names and trademarks
> belong to their owners and are used only to describe compatibility. This repository contains **no
> vendor firmware, software or images** — roll preview images are fetched at runtime from the
> vendor's public server and cached on the device. The tag/catalog data here (codes, sizes, names)
> is factual interoperability data. Use at your own risk, only with rolls and hardware you own.

## Install

Download the APK from [Releases](../../releases) and install it (allow installing from unknown
sources if Android asks). You need an Android 9+ phone with NFC.

## Usage

1. Pick a **Size** and a **Color / design**, or type a 24-hex-digit code into the **Paper code** field.
2. Hold a blank NTAG213 to the phone. The app writes pages 4–7 and reads them back to verify.
3. To see the code of an existing roll, tick **Read tag instead of writing** and hold the roll's tag
   to the phone.

## How it works

The tag stores a 12-byte **paper code** (pages 5–7), preceded by page 4 `01 03 A0 0C`. There is no
encryption and no checksum on the tag. The printer reads the tag, and the official Labelnise app looks
the code up in its server catalog to get the size, gap and background design. That is why this app
**writes catalog codes** instead of assembling the bytes field by field.

- Labels listed in the catalog only for other models are marked, e.g. `[P31S,P21]`.
- The catalog is loaded from the Labelnise server on launch and cached; offline the app uses the
  cached copy, then the bundled `app/src/main/assets/labels.json`. Roll previews are downloaded
  from the server and cached.
- Under the **Paper code** field the app shows the decoded size and whether the code is in the catalog.

Full format: [docs/TAG_FORMAT.md](docs/TAG_FORMAT.md). Example roll dumps: [docs/dumps](docs/dumps)
(contributed by [mchangrh](https://github.com/merlinschumacher/nelko-p21-print/issues/2#issuecomment-3873826757)
under the MIT license, see [#1](https://github.com/LogON/P22-NFC-Writer/issues/1)).

## Build from source

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Signed releases are built by
GitHub Actions, see [docs/RELEASING.md](docs/RELEASING.md).

## Tool: make_tag.py

Generates a Flipper Zero `.nfc` file or prints pages 4–7 from the catalog:

```bash
python tools/make_tag.py --catalog GAP22 -o gap22.nfc
python tools/make_tag.py --code 010204000301121215280C0F --pages
```

`tools/catalog_P21_P22.json` is a factual dump of the public catalog (codes, sizes, names, image URLs).

## License

[MIT](LICENSE).
