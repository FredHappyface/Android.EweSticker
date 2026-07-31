# The webp → GIF pipeline

Apps that advertise `image/gif` but no webp mimetype — Instagram, Teams — can only show an animated
sticker if it is handed to them as a GIF. Android provides neither half of that conversion: nothing
in the public SDK yields individual frames of an animated webp (`ImageDecoder` gives you an
`AnimatedImageDrawable` you can only play), and there is no GIF encoder at all.

So both halves are implemented here. `tools/gif-variants.py` is the desktop reference and the
quality bar; the on-device path is measured against it.

## Decoding — `WebpAnimation.kt`

Rather than bundling a second copy of libwebp (Fresco's `animated-webp` pulls its entire image
pipeline in beside Coil, ~3MB), this walks the webp RIFF container directly and re-wraps each `ANMF`
frame as a standalone one-image webp, which `BitmapFactory` decodes with the platform's own decoder.

Frames are then composited onto a canvas honouring the container's blend and disposal flags.

**Verified byte-exact**: re-muxed frame 0 of a real sticker decoded to a total absolute difference of
**0** against the reference decoder.

`isAnimated()` is the cheap probe used throughout. It walks chunk headers with `RandomAccessFile`,
seeking past payloads. It previously searched a fixed 64-byte prefix for `ANIM`, which is wrong: an
ICC profile between `VP8X` and `ANIM` pushes the marker out of the window, and the sticker is then
silently treated as still.

## Encoding — `GifEncoder.kt`

GIF89a, no Android dependencies (hence unit-testable on the JVM):

- **Median cut** per frame, then **Floyd–Steinberg** dithering — deliberately the same pairing Pillow
  uses in the reference, so output stays comparable.
- **Local colour table per frame**, since a palette chosen for the whole animation wastes entries.
- **LZW** with the standard clear/EOI handling.

### Two mistakes worth remembering

**The LZW code-width off-by-one.** A decoder learns each dictionary entry one code *later* than the
encoder creates it, so its table lags by one. Widening the code width when the encoder's own counter
reaches the ceiling starts emitting wider codes one code before the decoder starts reading them, and
the whole stream desynchronises. Every GIF the first version produced was undecodable. The condition
is `nextCode > (1 shl codeSize)`, not `>= `.

**The palette lookup cache.** Caching nearest-colour results on a 5-bits-per-channel key was ~4×
faster but cost **1.3 dB**, because rounding the query throws away exactly the small differences
dithering works in. Removed; the scan is exhaustive on purpose.

### The sort that dominated everything

`sortByChannel` was an insertion sort, justified by a comment claiming boxes are small. That is wrong
for the first split, which covers *every distinct colour in the frame* — around 6,100 for a
photographic sticker. Replaced with a counting sort over the 8-bit key.

Both sorts are stable, so the box boundaries and resulting palette are identical. Measured on the
same input:

| | Time | Output |
|---|---|---|
| Insertion sort | 12,256 ms | 2,060,088 bytes |
| Counting sort | 1,397 ms | 2,060,088 bytes |

**Byte-identical**, ~10× faster. On device, regenerating all 53 animated stickers went from **247s to
32s**.

## Resampling — `ImageResampler.kt`

Separable Lanczos-3 on ARGB, premultiplied so colour cannot bleed out of transparent pixels.
`Bitmap.createScaledBitmap` only offers bilinear, which is too few taps to shrink cleanly and lands
visibly short of the reference.

## Quality against the reference

Same sticker, 39 frames, 320px, PSNR measured against identical ground-truth frames:

| | Size | PSNR |
|---|---|---|
| Desktop Pillow (the reference) | 1899 KB | 36.48 dB |
| On-device, full pipeline | 2015 KB | 36.35 dB |
| On-device, encoder only | 2011 KB | 36.37 dB |

0.13 dB and 6% size apart — parity. That the full pipeline (36.35) matches encoder-only on
Pillow-resized frames (36.37) shows the Lanczos resampler is effectively identical to Pillow's.

## Settings

`MAX_EDGE = 320`, `COLORS = 128`, matching the reference defaults. Full 512px is over 4MB per
sticker, too heavy to send. These are compile-time constants; exposing them was considered and
rejected as knob-adding whose only feedback loop is re-running a multi-minute conversion.

## Conversion happens in two places

- **At import**, for everything missing a variant, behind the "Make GIF versions" preference.
- **At send time**, for one sticker, when a GIF-only app needs one that does not exist yet. Only
  stickers actually sent get converted this way.

Both go through `GifVariantGenerator.convert`, which writes a uniquely-named temp file and renames.
A fixed `.part` name was unsafe once both paths existed: two conversions of the same sticker opened
the same file, the second truncating what the first had written, and the first then renamed a
NUL-holed GIF into place and sent it.
