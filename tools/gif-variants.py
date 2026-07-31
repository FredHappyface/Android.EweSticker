#!/usr/bin/env python3
"""Generate GIF variants of animated webp stickers.

WhatsApp accepts animated webp as a true sticker via its image/webp.wasticker
mimetype, but Instagram and Teams advertise neither webp nor any sticker
mimetype -- only image/gif. So for those apps the only way to keep a sticker
moving is to hand them a GIF of the same artwork.

This walks a sticker tree, finds the webp files that actually carry animation
(an ANMF frame chunk), and writes a sibling <name>.gif next to each one.
EweSticker's importer treats a .gif sitting beside a same-named .webp as a
format variant rather than as a separate sticker, so the keyboard picks
whichever one the target app can render. Static stickers are left alone --
there is nothing to animate.

Run it again after downloading new packs; existing variants are skipped unless
the source is newer, so it is cheap to re-run.

Usage:
    gif-variants.py <dir>                    convert a local directory in place
    gif-variants.py --device [SERIAL]        pull from, convert, push back to a phone
    gif-variants.py <dir> --max-size 320     downscale to cap GIF file size
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: pip install Pillow")

DEVICE_STICKER_DIR = "/sdcard/Stickers"
# GIF stores delays in centiseconds, so sub-10ms precision is unrepresentable.
MIN_GIF_DELAY_MS = 20


def iter_riff_chunks(data: bytes):
    """Yield (fourcc, payload_offset, payload_len) for each chunk in a RIFF file."""
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        return
    pos = 12
    end = min(len(data), 8 + int.from_bytes(data[4:8], "little"))
    while pos + 8 <= end:
        fourcc = data[pos : pos + 4]
        size = int.from_bytes(data[pos + 4 : pos + 8], "little")
        yield fourcc, pos + 8, size
        pos += 8 + size + (size & 1)  # chunks are padded to even length


def frame_durations(path: Path) -> list[int]:
    """Per-frame durations in ms, read straight from the webp ANMF chunks.

    An ANMF payload starts with frame x/y/width/height as 24-bit values, then a
    24-bit duration, so the duration sits at a fixed offset of 12 bytes.
    """
    data = path.read_bytes()
    durations = []
    for fourcc, offset, _size in iter_riff_chunks(data):
        if fourcc == b"ANMF":
            durations.append(int.from_bytes(data[offset + 12 : offset + 15], "little"))
    return durations


def is_animated(path: Path) -> bool:
    return bool(frame_durations(path))


def quantize(frame: Image.Image, colors: int, has_alpha: bool) -> Image.Image:
    """Convert an RGBA frame to a palettised frame GIF can store.

    GIF transparency is a single palette index, so when the artwork has an alpha
    channel one slot is reserved for it and every pixel below the halfway point
    is snapped to fully transparent. That hard cutoff is what produces fringing
    on soft edges; it is a limit of the format, not of this conversion.
    """
    if not has_alpha:
        return frame.convert("RGB").quantize(colors=colors)

    palettised = frame.convert("RGB").quantize(colors=min(colors, 255))
    transparent = frame.getchannel("A").point(lambda a: 255 if a < 128 else 0)
    palettised.paste(255, transparent)
    return palettised


def convert(src: Path, dest: Path, max_size: int, colors: int, every: int) -> tuple[int, int]:
    """Write an animated GIF of src to dest. Returns (frame count, byte size)."""
    durations = frame_durations(src)
    image = Image.open(src)

    frames: list[Image.Image] = []
    kept_durations: list[int] = []
    has_alpha = False
    for index in range(0, getattr(image, "n_frames", 1), every):
        image.seek(index)
        frame = image.convert("RGBA")
        if frame.getchannel("A").getextrema()[0] < 255:
            has_alpha = True
        if max(frame.size) > max_size:
            scale = max_size / max(frame.size)
            new_size = (round(frame.width * scale), round(frame.height * scale))
            frame = frame.resize(new_size, Image.LANCZOS)
        frames.append(frame)
        # Dropped frames' time has to be absorbed by the frame that replaces them,
        # otherwise decimating the animation also speeds it up.
        window = durations[index : index + every]
        kept_durations.append(sum(window) if window else 0)

    palettised = [quantize(frame, colors, has_alpha) for frame in frames]
    delays = [max(d, MIN_GIF_DELAY_MS) for d in kept_durations if d] or [100] * len(palettised)

    save_options = {
        "save_all": True,
        "append_images": palettised[1:],
        "duration": delays,
        "loop": 0,
        "disposal": 2,  # restore to background between frames
        "optimize": False,
    }
    if has_alpha:
        save_options["transparency"] = 255

    palettised[0].save(dest, **save_options)
    return len(palettised), dest.stat().st_size


def convert_tree(root: Path, max_size: int, colors: int, every: int, force: bool) -> int:
    converted = 0
    skipped_static = 0
    for src in sorted(root.rglob("*.webp")):
        dest = src.with_suffix(".gif")
        if not is_animated(src):
            skipped_static += 1
            continue
        if dest.exists() and not force and dest.stat().st_mtime >= src.stat().st_mtime:
            print(f"  = {src.parent.name}/{src.name} (variant up to date)")
            continue
        try:
            frames, size = convert(src, dest, max_size, colors, every)
        except Exception as exc:  # a single bad sticker should not abort the run
            print(f"  ! {src.parent.name}/{src.name}: {exc}", file=sys.stderr)
            continue
        print(f"  + {src.parent.name}/{src.name} -> {frames} frames, {size // 1024}KB")
        converted += 1
    print(f"\n{converted} GIF variant(s) written; {skipped_static} static sticker(s) left alone.")
    return converted


def adb(serial: str | None, *args: str) -> str:
    command = ["adb"]
    if serial:
        command += ["-s", serial]
    result = subprocess.run(command + list(args), capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def run_on_device(serial: str | None, remote: str, max_size: int, colors: int,
                  every: int, force: bool) -> None:
    workdir = Path(tempfile.mkdtemp(prefix="gif-variants-"))
    try:
        print(f"Pulling {remote} ...")
        adb(serial, "pull", remote, str(workdir / "Stickers"))
        local = workdir / "Stickers"
        if not local.is_dir():
            sys.exit(f"nothing pulled from {remote}")

        if not convert_tree(local, max_size, colors, every, force):
            print("Nothing to push.")
            return

        print("\nPushing GIF variants back ...")
        for gif in sorted(local.rglob("*.gif")):
            target = f"{remote}/{gif.parent.name}/{gif.name}"
            adb(serial, "push", str(gif), target)
            print(f"  -> {target}")
        print("\nDone. Re-import your sticker folder in EweSticker to pick these up.")
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("directory", nargs="?", type=Path, help="local sticker tree to convert")
    parser.add_argument("--device", nargs="?", const="", metavar="SERIAL",
                        help="operate on a connected phone over adb")
    parser.add_argument("--remote", default=DEVICE_STICKER_DIR,
                        help=f"sticker directory on the phone (default: {DEVICE_STICKER_DIR})")
    # GIF is far bulkier than webp for the same artwork, so the defaults trade a
    # little fidelity for a file size that is sane to send in a chat: a 512px
    # 39-frame sticker is 4.6MB at full size and 1.9MB at these settings.
    parser.add_argument("--max-size", type=int, default=320,
                        help="cap the longest edge, downscaling to shrink the GIF (default: 320)")
    parser.add_argument("--colors", type=int, default=128,
                        help="palette size per frame, 2-256 (default: 128)")
    parser.add_argument("--every", type=int, default=1, metavar="N",
                        help="keep only every Nth frame; 2 roughly halves the size (default: 1)")
    parser.add_argument("--force", action="store_true", help="rewrite variants that already exist")
    args = parser.parse_args()

    if not 2 <= args.colors <= 256:
        parser.error("--colors must be between 2 and 256")
    if args.every < 1:
        parser.error("--every must be 1 or greater")

    if args.device is not None:
        run_on_device(args.device or None, args.remote.rstrip("/"), args.max_size,
                      args.colors, args.every, args.force)
    elif args.directory:
        if not args.directory.is_dir():
            sys.exit(f"not a directory: {args.directory}")
        convert_tree(args.directory, args.max_size, args.colors, args.every, args.force)
    else:
        parser.error("pass a directory or --device")


if __name__ == "__main__":
    main()
