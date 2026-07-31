# Sending a sticker

Everything here was established by reading what apps actually advertise on a real device, not from
documentation. `adb logcat -s EweSticker` prints the advertised list on every field focus:

```
Connecting to com.whatsapp which supports
[image/gif, video/x.looping_mp4, image/jpeg, image/jpg, image/png, image/webp.wasticker]
```

## The mechanism

A keyboard sends media through `InputConnectionCompat.commitContent`, which hands the target app a
content URI plus a mimetype. The target app decides what that is. It will only accept mimetypes it
declared in `EditorInfo.contentMimeTypes`.

An IME **cannot** produce a WhatsApp sticker-pack sticker; that is a separate API requiring a
`StickerContentProvider` and an "Add to WhatsApp" flow. What it can do is described below.

## What the four target apps advertise

| App | Advertises | Result |
|---|---|---|
| `com.whatsapp`, `com.whatsapp.w4b` | `image/gif`, `video/x.looping_mp4`, `image/jpeg`, `image/jpg`, `image/png`, **`image/webp.wasticker`** | A real, animated sticker |
| `com.instagram.android` | `image/png`, `image/jpeg`, `image/gif` | GIF animates; no sticker concept |
| `com.microsoft.teams` | `image/png`, `image/gif`, `image/jpg`, `image/jpeg` | GIF animates |

**Neither WhatsApp advertises plain `image/webp`.** It exposes the proprietary
`image/webp.wasticker` instead, and only content committed under *that* mimetype is treated as a
sticker. Commit a webp as `image/webp` and it fails the mimetype check entirely, falling through to
a flattened PNG or a share sheet — which is what "my animated sticker arrives as a big photo" was.

Instagram is a dead end for animation despite advertising `image/gif`: it accepts the commit and
then declines to insert it, steering the user to its own GIPHY picker. Verified — the commit returns
success and nothing arrives. Teams does render the GIF.

## The ladder in `StickerSender.resolveCommit`

In order:

1. **Exact match**, including wildcards. `image/*` counts as accepting the original — without this,
   an app advertising both `image/*` and `image/gif` would be handed a downscaled 128-colour GIF
   when it would happily have taken the full-quality webp.
2. **A vendor `image/webp.*` mimetype**, matched by prefix rather than hardcoding `wasticker`, so
   another app's equivalent is picked up for free.
3. **A GIF variant**, when the target takes `image/gif` and one exists in `variants/`.
4. **On demand conversion** — if the sticker is animated, the target takes GIF, and no variant
   exists, convert it now (`convertVariantOnDemand`). Serialised per destination so two taps do not
   convert twice into the same file; failures are negatively cached.
5. **PNG fallback**, at source resolution, cached in `stickers/__compatSticker__/`. Toasts when this
   drops animation, distinguishing "this app cannot show animation" from "no GIF version yet".
6. **Share sheet**, scoped to the target package, carrying the original file untouched.

## Traps

- **`FileProvider` roots.** `res/xml/file_paths.xml` must list every tree a committed file can come
  from. Adding `variants/` was forgotten once and `getUriForFile` threw, killing the keyboard
  mid-send. `doCommitContent` now catches that and degrades instead of crashing.
- **A missing file still commits successfully.** `getUriForFile` does not check existence and
  `commitContent` reports success, so a phantom path arrives as nothing at all. This is why
  `createCompatSticker` verifies the file is non-empty before caching or returning it.
- **The sender outlives its field.** An on-demand conversion takes seconds, during which the user
  can leave. `ImageKeyboard` calls `abandon()` on the outgoing sender so a late completion cannot
  commit into a dead connection or throw a share sheet over whatever the user moved on to.
- **Nothing heavy on the main thread.** The PNG encode used to run inside a Coil `target` callback,
  which Coil invokes on `Main.immediate` — a full-resolution encode on the thread drawing the
  keyboard, on every cache miss.
