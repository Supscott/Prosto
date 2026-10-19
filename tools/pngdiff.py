#!/usr/bin/env python3
"""Dependency-free PNG decode / compare / write for the Harness^2 gate.

Only what the gate needs: read 8-bit PNGs, report how much a new capture
deviates from a stored baseline, and write a diff image.

The headline metric is the *largest connected cluster of changed blocks*:
scattered per-run noise (a prop that moved) produces many tiny islands, while
a real change (lighting, geometry) produces one large contiguous region.

Usage:
    python3 tools/pngdiff.py BASE.png CURRENT.png [--out DIFF.png] [--threshold 8]
"""

import json
import struct
import sys
import zlib
from pathlib import Path

CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}


class PngError(Exception):
    pass


def read_png(path):
    """Returns (width, height, channels, raw pixel bytes)."""
    data = Path(path).read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise PngError(f"{path}: not a PNG file")

    pos = 8
    idat = bytearray()
    palette = b""
    width = height = depth = color = 0
    interlace = 0
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        ctype = data[pos + 4 : pos + 8]
        body = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif ctype == b"PLTE":
            palette = body
        elif ctype == b"IDAT":
            idat += body
        elif ctype == b"IEND":
            break

    if depth != 8:
        raise PngError(f"{path}: only 8-bit PNGs supported (got {depth})")
    if interlace != 0:
        raise PngError(f"{path}: interlaced PNGs not supported")
    if color not in CHANNELS:
        raise PngError(f"{path}: unsupported color type {color}")

    channels = CHANNELS[color]
    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    out = bytearray(height * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        ftype = raw[p]
        p += 1
        line = bytearray(raw[p : p + stride])
        p += stride
        if ftype == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
        elif ftype != 0:
            raise PngError(f"{path}: unknown filter type {ftype}")
        out[y * stride : (y + 1) * stride] = line
        prev = line

    if color == 3:
        out = _expand_palette(out, palette)
        channels = 3
    return width, height, channels, bytes(out)


def _expand_palette(pixels, palette):
    rgb = bytearray(len(pixels) * 3)
    for i, idx in enumerate(pixels):
        rgb[i * 3 : i * 3 + 3] = palette[idx * 3 : idx * 3 + 3]
    return bytes(rgb)


def write_png(path, width, height, channels, pixels):
    """Writes an 8-bit PNG (color types 0/2/4/6) with filter type 0 rows."""
    color = {1: 0, 2: 4, 3: 2, 4: 6}[channels]
    stride = width * channels
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        raw += pixels[y * stride : (y + 1) * stride]
    body = zlib.compress(bytes(raw), 6)

    def chunk(tag, payload):
        return (
            struct.pack(">I", len(payload))
            + tag
            + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        )

    Path(path).write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, color, 0, 0, 0))
        + chunk(b"IDAT", body)
        + chunk(b"IEND", b"")
    )


def _to_rgb(w, h, ch, pix):
    if ch == 3:
        return bytes(pix)
    out = bytearray(w * h * 3)
    for i in range(w * h):
        o = i * ch
        out[i * 3 : i * 3 + 3] = pix[o : o + 3]
    return bytes(out)


def _downscale(w, h, ch, pix, target_w):
    """Block-averages an image down to target_w, killing per-pixel noise."""
    if target_w >= w:
        return w, h, 3, _to_rgb(w, h, ch, pix)
    block = max(1, w // target_w)
    tw = w // block
    th = h // block
    out = bytearray(tw * th * 3)
    for ty in range(th):
        for tx in range(tw):
            rs = gs = bs = 0
            count = 0
            for y in range(ty * block, (ty + 1) * block):
                row = y * w * ch
                for x in range(tx * block, (tx + 1) * block):
                    o = row + x * ch
                    rs += pix[o]
                    gs += pix[o + 1]
                    bs += pix[o + 2]
                    count += 1
            o = (ty * tw + tx) * 3
            out[o] = rs // count
            out[o + 1] = gs // count
            out[o + 2] = bs // count
    return tw, th, 3, bytes(out)


def _clusters(mask, w, h):
    """Counts 4-connected clusters of changed blocks: (largest, count)."""
    seen = bytearray(w * h)
    largest = 0
    count = 0
    for start in range(w * h):
        if not mask[start] or seen[start]:
            continue
        count += 1
        size = 0
        stack = [start]
        seen[start] = 1
        while stack:
            idx = stack.pop()
            size += 1
            x = idx % w
            for nxt in (idx - 1, idx + 1, idx - w, idx + w):
                if nxt < 0 or nxt >= w * h or seen[nxt] or not mask[nxt]:
                    continue
                if abs(nxt % w - x) > 1:
                    continue
                seen[nxt] = 1
                stack.append(nxt)
        if size > largest:
            largest = size
    return largest, count


def compare(base_path, cur_path, threshold=8, target_w=160):
    """Compares two PNGs on a downscaled copy (noise-robust, structure-sensitive)."""
    bw, bh, bch, bpix = read_png(base_path)
    cw, chh, cch, cpix = read_png(cur_path)

    if (bw, bh) != (cw, chh):
        return {
            "status": "size_mismatch",
            "base_size": [bw, bh],
            "current_size": [cw, chh],
            "changed_ratio": 1.0,
            "mean_abs_delta": 255.0,
            "largest_cluster_ratio": 1.0,
            "cluster_count": 1,
        }

    tw, th, _, bsmall = _downscale(bw, bh, bch, bpix, target_w)
    _, _, _, csmall = _downscale(cw, chh, cch, cpix, target_w)

    changed = 0
    total_delta = 0
    pixels = tw * th
    mask = bytearray(pixels)
    for i in range(pixels):
        o = i * 3
        d = max(
            abs(bsmall[o] - csmall[o]),
            abs(bsmall[o + 1] - csmall[o + 1]),
            abs(bsmall[o + 2] - csmall[o + 2]),
        )
        total_delta += d
        if d > threshold:
            changed += 1
            mask[i] = 1

    largest, cluster_count = _clusters(mask, tw, th)
    return {
        "status": "compared",
        "width": bw,
        "height": bh,
        "compare_size": [tw, th],
        "pixels": pixels,
        "changed_pixels": changed,
        "changed_ratio": changed / pixels if pixels else 0.0,
        "mean_abs_delta": total_delta / pixels if pixels else 0.0,
        "largest_cluster_ratio": largest / pixels if pixels else 0.0,
        "cluster_count": cluster_count,
        "threshold": threshold,
    }


def diff_image(base_path, cur_path, out_path, threshold=8, target_w=160):
    """Writes a diff PNG at full resolution: dimmed base, red where blocks changed."""
    w, h, ch, bpix = read_png(base_path)
    _, _, cch, cpix = read_png(cur_path)
    tw, th, _, bsmall = _downscale(w, h, ch, bpix, target_w)
    _, _, _, csmall = _downscale(w, h, cch, cpix, target_w)

    block = max(1, w // tw)
    marks = set()
    for i in range(tw * th):
        o = i * 3
        d = max(
            abs(bsmall[o] - csmall[o]),
            abs(bsmall[o + 1] - csmall[o + 1]),
            abs(bsmall[o + 2] - csmall[o + 2]),
        )
        if d > threshold:
            marks.add((i % tw, i // tw))

    out = bytearray(w * h * 3)
    for ty in range(th):
        for tx in range(tw):
            changed = (tx, ty) in marks
            for y in range(ty * block, min(h, (ty + 1) * block)):
                row = y * w
                for x in range(tx * block, min(w, (tx + 1) * block)):
                    o = (row + x) * ch
                    if changed:
                        out[(row + x) * 3 : (row + x) * 3 + 3] = b"\xff\x20\x20"
                    else:
                        grey = (bpix[o] * 30 + bpix[o + 1] * 59 + bpix[o + 2] * 11) // 100
                        v = grey // 3
                        out[(row + x) * 3 : (row + x) * 3 + 3] = bytes((v, v, v))
    write_png(out_path, w, h, 3, bytes(out))


def main():
    args = sys.argv[1:]
    if len(args) < 2:
        print(__doc__)
        return 2
    base, cur = Path(args[0]), Path(args[1])
    out = None
    threshold = 8
    i = 2
    while i < len(args):
        if args[i].startswith("--out="):
            out = Path(args[i][6:])
        elif args[i].startswith("--threshold="):
            try:
                threshold = int(args[i][12:])
            except ValueError:
                threshold = 8
        i += 1

    metrics = compare(base, cur, threshold)
    if out and metrics.get("status") == "compared":
        diff_image(base, cur, out, threshold)
        metrics["diff_image"] = str(out)
    print(json.dumps(metrics, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
