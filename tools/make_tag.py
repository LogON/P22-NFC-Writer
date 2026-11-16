#!/usr/bin/env python3
"""
Roll tag generator for Nelko P21 / Olympia P22 (NTAG213 / NTAG203).

The format was reconstructed from Labelnise (com.ezink.data.ConsumablesPaperBean.parse).
There is no encryption or signature: the printer reads pages 4..7 of the tag itself and
sends the app 16 bytes over BLE: [status] + 13 bytes from the tag + CRC16/MODBUS (computed
by the printer, not stored on the tag).

Examples:
  # white 14x40 with gap (like the original roll)
  python make_tag.py --width 14 --length 40 -o white_14x40.nfc
  # blue
  python make_tag.py --width 14 --length 40 --color blue -o blue_14x40.nfc
  # continuous 14 mm tape (like Olympia 6312)
  python make_tag.py --continuous --width 14 -o cont_14.nfc
  # only pages 4..7 for writing to a blank NTAG213 (it has its own UID)
  python make_tag.py --width 12 --length 40 --pages
"""
import argparse
import json
import os
import sys

COLORS = {"transparent": 2, "white": 3, "pink": 4, "blue": 5, "yellow": 6,
          "round_white": 7, "round_transparent": 8}
PAPER_TYPES = {"continuous": 0, "gap": 1, "blank": 2, "tattoo": 3,
               "single": 4, "fold": 5, "hole": 6}


def crc16_modbus(data: bytes) -> bytes:
    c = 0xFFFF
    for b in data:
        c ^= b
        for _ in range(8):
            c = (c >> 1) ^ 0xA001 if c & 1 else c >> 1
    return bytes([c >> 8, c & 0xFF])  # byte order as in the app (big-endian)


def from_catalog(name: str) -> bytes:
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "catalog_P21_P22.json")
    with open(path, encoding="utf-8") as f:
        rows = json.load(f)
    hits = [r for r in rows if r["name"].lower() == name.lower()] or \
           [r for r in rows if name.lower() in r["name"].lower()]
    if len(hits) != 1:
        sys.exit("Found %d entries: %s" % (len(hits), ", ".join(r["name"] for r in hits[:20])))
    print("Catalog:", hits[0]["name"], hits[0]["code"])
    return bytes.fromhex(hits[0]["code"])


def build_record(a) -> bytes:
    """12 bytes = tag pages 5..7."""
    if a.code:
        rec = bytes.fromhex(a.code)
        if len(rec) != 12:
            sys.exit("--code must be 12 bytes (24 hex digits)")
        return rec
    if a.catalog:
        return from_catalog(a.catalog)
    design = a.design if a.design is not None else COLORS[a.color]
    if a.continuous:
        version = 0x22          # v2 ("third RFID version"): type in the low nibble of byte 5, byte 6 = gap
        type_byte = 0x00        # cable=0, type=0 (continuous)
        gap_byte = 0x00
        length = a.length if a.length is not None else 10
    else:
        version = 0x12          # v1 ("second RFID version")
        type_byte = PAPER_TYPES[a.type]
        gap_byte = 0x12         # the app does not read this byte in v1; copied from the original
        length = a.length if a.length is not None else 40
    # 14 mm is encoded with version 0x12 (width in k11, k10=0F).
    # 12 and 15 mm use the old format 0x02 in the catalog: width in k10, k11=0F.
    w10, w11 = 0x0F, a.width & 0xFF
    if not a.continuous and a.width != 14:
        version, w10, w11 = 0x02, a.width & 0xFF, 0x0F
    return bytes([
        0x01,                   # k0  p5b0  not read by the app, 01 on every roll
        version,                # k1  p5b1  high nibble = encoding version, low nibble = "industry"/company
        design & 0xFF,          # k2  p5b2  colour / design number (GAPxx in the catalog)
        0x00, 0x03,             # k3,k4     batch (consumablesBatch)
        type_byte,              # k5  p6b1  paper type
        gap_byte,               # k6  p6b2  gap (v2/v3 only)
        0x12,                   # k7  p6b3  circleOutSize (outer roll diameter?)
        a.total & 0xFF,         # k8  p7b0  consumablesTotalLength (roll length, 0x15=21)
        length & 0xFF,          # k9  p7b1  label length, mm
        w10,                    # k10 p7b2  width for format 0x02, otherwise 0F
        w11,                    # k11 p7b3  width for formats 0x12/0x22, otherwise 0F
    ])


def uid_pages(uid7: bytes):
    bcc0 = 0x88 ^ uid7[0] ^ uid7[1] ^ uid7[2]
    bcc1 = uid7[3] ^ uid7[4] ^ uid7[5] ^ uid7[6]
    return [bytes([uid7[0], uid7[1], uid7[2], bcc0]),
            bytes(uid7[3:7]),
            bytes([bcc1, 0x48, 0x00, 0x00])]


def flipper_file(uid7: bytes, rec: bytes) -> str:
    pages = uid_pages(uid7)
    pages.append(bytes.fromhex("E1101200"))           # CC: NDEF 144 bytes
    pages.append(bytes.fromhex("0103A00C"))           # 0C = record length (the app checks ==12)
    pages += [rec[i:i + 4] for i in range(0, 12, 4)]  # pages 5..7
    pages += [bytes(4)] * (40 - len(pages))
    pages += [bytes.fromhex(h) for h in ("000000BD", "040000FF", "00050000", "FFFFFFFF", "00000000")]
    h = lambda b: " ".join(f"{x:02X}" for x in b)
    out = ["Filetype: Flipper NFC device", "Version: 4",
           "Device type: NTAG/Ultralight", f"UID: {h(uid7)}",
           "ATQA: 00 44", "SAK: 00", "Data format version: 2",
           "NTAG/Ultralight type: NTAG213", f"Signature: {h(bytes(32))}",
           "Mifare version: 00 04 04 02 01 00 0F 03",
           "Counter 0: 0", "Tearing 0: 00", "Counter 1: 0", "Tearing 1: 00",
           "Counter 2: 0", "Tearing 2: 00",
           f"Pages total: {len(pages)}", f"Pages read: {len(pages)}"]
    out += [f"Page {i}: {h(p)}" for i, p in enumerate(pages)]
    out.append("Failed authentication attempts: 0")
    return "\n".join(out) + "\n"


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--code", help="ready-made 12-byte code (hex), e.g. from catalog_P21_P22.json")
    p.add_argument("--catalog", help="catalog label name, e.g. 'GAP22' or '15x40mm GAP White'")
    p.add_argument("--design", type=lambda s: int(s, 0), help="raw k2 byte (design number), e.g. 0x22")
    p.add_argument("--width", type=int, default=14, help="width, mm (12/14/15)")
    p.add_argument("--length", type=int, help="label length, mm (default 40; 10 for tape)")
    p.add_argument("--color", choices=COLORS, default="white")
    p.add_argument("--type", choices=PAPER_TYPES, default="gap", help="paper type for v1")
    p.add_argument("--continuous", action="store_true", help="continuous tape (format 0x22)")
    p.add_argument("--total", type=int, default=0x15, help="roll length (byte k8), default 21")
    p.add_argument("--uid", help="7-byte UID in hex; random by default")
    p.add_argument("--pages", action="store_true", help="print only pages 4..7 for writing")
    p.add_argument("-o", "--out", help="Flipper .nfc output file")
    a = p.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    rec = build_record(a)
    resp = bytes([0x04, 0x0C]) + rec   # what the printer will send to the app (byte 0 = printer status)
    resp += crc16_modbus(resp)
    print("Record (pages 5..7):", rec.hex(" ").upper())
    print("Paper code for the app:", rec.hex().upper())
    print("Expected printer reply to 1B 21 6F 0D 0A:", resp.hex(" ").upper())

    if a.pages:
        pg = [bytes.fromhex("0103A00C")] + [rec[i:i + 4] for i in range(0, 12, 4)]
        for i, b in enumerate(pg, start=4):
            print(f"Page {i}: {b.hex(' ').upper()}")
        return

    uid = bytes.fromhex(a.uid) if a.uid else bytes([0x53]) + os.urandom(6)
    if len(uid) != 7:
        sys.exit("UID must be 7 bytes")
    text = flipper_file(uid, rec)
    if a.out:
        with open(a.out, "w", newline="\n") as f:
            f.write(text)
        print("Written:", a.out)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
