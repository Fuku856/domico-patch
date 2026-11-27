#!/usr/bin/env python3
"""
デバッグ用に APK のパッケージ名を差し替える(公式版・通常パッチ版と並べて入れるため)。

バイナリ AndroidManifest.xml の文字列プールだけを書き換える。apktool による
manifest/resources の再エンコードは一部端末で Invalid apk を誘発するため使わず、
resources.arsc・dex・他エントリはバイト維持する(patch_apk.py と同じ方針)。

書き換える文字列:
  - OLD と完全一致するもの (manifest の package 属性)
  - "OLD." で始まるもの (provider の authorities、独自 permission 名など)
    → 同じ authorities/permission を持つ別署名アプリが入っていると
      INSTALL_FAILED_CONFLICTING_PROVIDER / DUPLICATE_PERMISSION になるため。
  公式 manifest のコンポーネント名はすべて vn.com.bravesoft... / androidx... の
  絶対名で、"OLD." で始まるクラス名は無い(1.5.6 で確認)。

--label を渡すと <application android:label> をリテラル文字列に置き換え、
ランチャー上で通常版と見分けられるようにする。

使い方(単体):
  python scripts/rename_package.py --in a.apk --out b.apk \
      --old jp.co.kyoritsu.domico --new jp.co.kyoritsu.domico.debug [--label "Domico (debug)"]
"""
import argparse
import struct
import sys
import zipfile

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass

RES_XML_TYPE = 0x0003
RES_STRING_POOL_TYPE = 0x0001
RES_XML_RESOURCE_MAP_TYPE = 0x0180
RES_XML_START_ELEMENT_TYPE = 0x0102
UTF8_FLAG = 0x100
ATTR_LABEL = 0x01010001
TYPE_STRING = 0x03


def log(m):
    print(f"[rename_package] {m}", flush=True)


# ---- string pool ------------------------------------------------------------

def _read_len8(b, o):
    n = b[o]
    if n & 0x80:
        return ((n & 0x7F) << 8) | b[o + 1], o + 2
    return n, o + 1


def _read_len16(b, o):
    n = struct.unpack_from("<H", b, o)[0]
    if n & 0x8000:
        return ((n & 0x7FFF) << 16) | struct.unpack_from("<H", b, o + 2)[0], o + 4
    return n, o + 2


def _enc_len8(n):
    if n > 0x7FFF:
        raise ValueError("string too long for UTF-8 pool")
    return bytes([0x80 | (n >> 8), n & 0xFF]) if n > 0x7F else bytes([n])


def _enc_len16(n):
    if n > 0x7FFF:
        return struct.pack("<HH", 0x8000 | (n >> 16), n & 0xFFFF)
    return struct.pack("<H", n)


def parse_pool(chunk):
    """chunk(ヘッダ込みの bytes) -> (strings, flags, styles_raw, style_count)"""
    (_t, hsize, size, count, style_count, flags, s_start, st_start) = struct.unpack_from(
        "<HHIIIIII", chunk, 0
    )
    offsets = struct.unpack_from(f"<{count}I", chunk, hsize)
    utf8 = bool(flags & UTF8_FLAG)
    strings = []
    for off in offsets:
        o = s_start + off
        if utf8:
            _, o = _read_len8(chunk, o)          # UTF-16 換算の長さ(読み捨て)
            blen, o = _read_len8(chunk, o)
            strings.append(chunk[o:o + blen].decode("utf-8", "surrogatepass"))
        else:
            ulen, o = _read_len16(chunk, o)
            strings.append(chunk[o:o + ulen * 2].decode("utf-16-le", "surrogatepass"))
    styles_raw = b""
    if style_count:
        # style オフセット表 + style 本体をそのまま保持(参照は文字列インデックスなので不変)
        st_off_tbl = chunk[hsize + count * 4: hsize + count * 4 + style_count * 4]
        styles_raw = (st_off_tbl, chunk[st_start:size])
    return strings, flags, styles_raw, style_count


def build_pool(strings, flags, styles_raw, style_count):
    utf8 = bool(flags & UTF8_FLAG)
    data = bytearray()
    offsets = []
    for s in strings:
        offsets.append(len(data))
        if utf8:
            raw = s.encode("utf-8", "surrogatepass")
            u16len = len(s.encode("utf-16-le", "surrogatepass")) // 2
            data += _enc_len8(u16len) + _enc_len8(len(raw)) + raw + b"\x00"
        else:
            raw = s.encode("utf-16-le", "surrogatepass")
            data += _enc_len16(len(raw) // 2) + raw + b"\x00\x00"
    while len(data) % 4:
        data += b"\x00"
    hsize = 28
    count = len(strings)
    st_tbl = styles_raw[0] if style_count else b""
    s_start = hsize + count * 4 + len(st_tbl)
    st_start = s_start + len(data) if style_count else 0
    st_body = styles_raw[1] if style_count else b""
    size = s_start + len(data) + len(st_body)
    head = struct.pack(
        "<HHIIIIII", RES_STRING_POOL_TYPE, hsize, size, count, style_count,
        flags & ~0x1,  # SORTED フラグは並びを保証できないので落とす
        s_start, st_start,
    )
    return head + struct.pack(f"<{count}I", *offsets) + st_tbl + bytes(data) + st_body


# ---- manifest ---------------------------------------------------------------

def _chunks(b, start, end):
    o = start
    while o < end:
        t, hs, sz = struct.unpack_from("<HHI", b, o)
        yield o, t, hs, sz
        o += sz


def rename_manifest(axml, old, new, label=None):
    t, hs, total = struct.unpack_from("<HHI", axml, 0)
    if t != RES_XML_TYPE:
        raise SystemExit("AndroidManifest.xml がバイナリ XML ではありません")
    chunks = list(_chunks(axml, hs, total))
    pool_off = next((o for o, ct, _, _ in chunks if ct == RES_STRING_POOL_TYPE), None)
    if pool_off is None:
        raise SystemExit("manifest に文字列プールがありません")
    pool_size = struct.unpack_from("<I", axml, pool_off + 4)[0]
    strings, flags, styles_raw, style_count = parse_pool(axml[pool_off:pool_off + pool_size])

    changed = []
    for i, s in enumerate(strings):
        if s == old or s.startswith(old + "."):
            strings[i] = new + s[len(old):]
            changed.append(f"{s} -> {strings[i]}")
    if not any(c.startswith(old + " ->") for c in changed):
        raise SystemExit(f"manifest に package={old} が見つかりません")

    body = bytearray(axml[pool_off + pool_size:total])
    if label is not None:
        resmap = []
        for o, ct, chs, csz in chunks:
            if ct == RES_XML_RESOURCE_MAP_TYPE:
                resmap = list(struct.unpack_from(f"<{(csz - chs) // 4}I", axml, o + chs))
        label_idx = len(strings)
        strings.append(label)
        base = pool_off + pool_size
        done = False
        for o, ct, chs, csz in chunks:
            if ct != RES_XML_START_ELEMENT_TYPE or o < base:
                continue
            name_idx = struct.unpack_from("<I", axml, o + chs + 4)[0]
            if strings[name_idx] != "application":
                continue
            a_start, a_size, a_count = struct.unpack_from("<HHH", axml, o + chs + 8)
            for k in range(a_count):
                ao = o + chs + a_start + k * a_size
                an = struct.unpack_from("<I", axml, ao + 4)[0]
                if an < len(resmap) and resmap[an] == ATTR_LABEL:
                    rel = ao - base
                    struct.pack_into("<I", body, rel + 8, label_idx)          # rawValue
                    struct.pack_into("<HBBI", body, rel + 12, 8, 0, TYPE_STRING, label_idx)
                    done = True
            break
        if not done:
            raise SystemExit("<application android:label> が見つかりません")
        changed.append(f"application label -> {label!r}")

    new_pool = build_pool(strings, flags, styles_raw, style_count)
    head = axml[hs:pool_off]
    out_total = hs + len(head) + len(new_pool) + len(body)
    out = struct.pack("<HHI", RES_XML_TYPE, hs, out_total) + head + new_pool + bytes(body)
    return out, changed


def rename_apk(in_apk, out_apk, old, new, label=None):
    """APK をコピーし AndroidManifest.xml だけ差し替える(他エントリは圧縮種別ごと維持)。"""
    with zipfile.ZipFile(in_apk) as zin, zipfile.ZipFile(out_apk, "w") as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "AndroidManifest.xml":
                data, changed = rename_manifest(data, old, new, label)
                for c in changed:
                    log(f"  {c}")
            zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
            zi.compress_type = item.compress_type
            zi.external_attr = item.external_attr
            zi.internal_attr = item.internal_attr
            zi.create_system = item.create_system
            zout.writestr(zi, data)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--in", dest="inp", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--old", required=True)
    ap.add_argument("--new", required=True)
    ap.add_argument("--label")
    a = ap.parse_args()
    rename_apk(a.inp, a.out, a.old, a.new, a.label)
    log(f"OK {a.out}")


if __name__ == "__main__":
    main()
