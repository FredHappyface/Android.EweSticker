# Code map

File → responsibility. Read this before grepping; scope edits to the files named here.

Upstream is [FredHappyface/Android.EweSticker](https://github.com/FredHappyface/Android.EweSticker).
This is a personal fork; everything marked **(fork)** does not exist upstream.

## Entry points

| File | Responsibility |
|---|---|
| `ImageKeyboard.kt` | The `InputMethodService`. Owns the keyboard view, pack bar, search, preview, gestures, drag-to-resize height, and the reload-on-change mechanism **(fork)**. The biggest file; most keyboard behaviour lives here. |
| `MainActivity.kt` | Settings screen. Directory picking, import triggers, the Library card **(fork)**, option toggles, log export. |

## Sending a sticker

| File | Responsibility |
|---|---|
| `utilities/StickerSender.kt` | Decides *what file* to send and *under which mimetype*, then commits it. Holds the fallback ladder and the on-demand GIF conversion **(fork)**. See `docs/sending.md`. |
| `utilities/Utils.kt` | Mimetype from file extension, and the list of formats the app accepts on import. |
| `utilities/Cache.kt` | Fixed-capacity ordered set, persisted to prefs. Two instances: recents, and converted PNG fallbacks. |
| `utilities/StickerKeySet.kt` | **(fork)** A named set of stickers held as relative `pack/filename` keys so it survives a re-import. Backs favourites; `resolve()` prunes keys whose files are gone. |
| `res/xml/file_paths.xml` | `FileProvider` roots. Both `stickers/` and `variants/` **(fork)** must be listed or committing throws. |

## Importing

| File | Responsibility |
|---|---|
| `utilities/StickerImporter.kt` | Plans and performs the import. Incremental and non-destructive **(fork)**. See `docs/import.md`. |
| `utilities/SourceListing.kt` | **(fork)** Walks the SAF tree with one cursor per directory, yielding name/mime/size/mtime, and reports whether the walk was complete. |
| `utilities/ImportManifest.kt` | **(fork)** Persisted record of what was copied, so the next import can tell changed from unchanged. |
| `utilities/LibraryScanner.kt` | **(fork)** Measures the imported library for the Library card, and deletes generated GIFs. |

Deleting a single sticker lives in `ImageKeyboard.removeEverywhere`, with the source-side work in
`SourceListing.removeDocument` and `SourceListing.deletePackIfEmpty`. See "Deleting" in
`docs/import.md` — it is the only code that removes files from the user's own storage.

## GIF generation

See `docs/gif-pipeline.md` for how these fit together and the quality numbers.

| File | Responsibility |
|---|---|
| `utilities/GifVariantGenerator.kt` | **(fork)** Orchestrates webp → GIF conversion across cores; one conversion per sticker, temp file then rename. |
| `utilities/WebpAnimation.kt` | **(fork)** Demuxes an animated webp by hand and hands over composited frames. Also `isAnimated`, the cheap animation probe used all over. |
| `utilities/GifEncoder.kt` | **(fork)** GIF89a writer: median cut, Floyd–Steinberg dithering, LZW. No Android dependencies, so it is unit-testable on the JVM. |
| `utilities/ImageResampler.kt` | **(fork)** Lanczos-3 resize on ARGB. Exists because `Bitmap.createScaledBitmap` only does bilinear, which loses against the desktop reference. |
| `tools/gif-variants.py` | **(fork)** Desktop reference implementation and the quality benchmark the on-device encoder is measured against. |

## Keyboard view

| File | Responsibility |
|---|---|
| `adapter/AllPacksAdapter.kt` | **(fork)** Every pack in one scrollable list with a heading per pack. |
| `adapter/StickerPackAdapter.kt` | A single pack, or search results. |
| `adapter/StickerBinding.kt` | **(fork)** Shared sticker cell binding, so the two adapters cannot drift apart. |
| `view/StickerPackViewHolder.kt`, `view/PackHeaderViewHolder.kt` | Cell and heading holders. |
| `model/StickerPack.kt` | A pack directory: its stickers and its thumbnail. |
| `res/layout/keyboard_layout.xml` | Drag handle (`resizeHandle`), then the pack bar (`topHScrollView` + `packsList`), above a `packContent` container that everything else is swapped into. |
| `res/drawable/resize_grabber.xml` | **(fork)** The grab indicator inside the resize handle. Uses the `outline` role so it reads as chrome, not as something tappable. |
| `res/layout/pack_header.xml` | **(fork)** Divider and pack name inside the combined list. |
| `res/layout/sticker_card.xml` | One cell. Shared by the grid, the search strip and the pack bar, so a change here shows up in all three. |
| `res/layout/sticker_preview.xml` | **(fork)** The long-press surface: delete and star controls, the enlarged sticker, and a scrim behind it. |

### Theming **(fork)**

| File | Responsibility |
|---|---|
| `res/values-v31/`, `res/values-night-v31/` | Material You. Maps `bg`/`fg`/`accent` and the surface roles onto the `system_neutral*`/`system_accent*` palette, so the keyboard follows the wallpaper on Android 12+. The files in `values/` are the pre-31 fallback. |
| `res/drawable/pack_selected.xml` | The filled container behind the selected pack icon. Selection used to be a colour filter over the sticker artwork, which recoloured the image. |
| `res/drawable/sticker_ripple.xml` | Rounded ripple used by every tappable sticker and preview control. |
| `res/drawable/preview_surface.xml` | Rounded surface behind the long-press preview. |

Colour roles beyond `bg`/`fg`/`accent`: `surface_bar` (the pack bar), `selection_container`,
`outline` (dividers), `ripple`, `scrim`. Add new roles in all four value directories or the v31
variant silently falls back.

## Support

| File | Responsibility |
|---|---|
| `utilities/Toaster.kt` | Toast wrapper; also queues messages the importer emits. |
| `utilities/StartLogger.kt` | xlog setup. Logs go to `filesDir/logs/<date>`; `adb logcat -s EweSticker` is the fastest way to watch. |
| `utilities/StickerClickListener.kt` | Callback interface the keyboard implements. |

## Tests

JVM unit tests only — `./gradlew testDebugUnitTest`. They cover the pieces with no Android
dependencies, which is deliberately where the fiddly byte-level code lives.

| File | Responsibility |
|---|---|
| `test/utilities/GifEncoderTest.kt` | Encoder round trips, transparency, palette budget, delays. |
| `test/utilities/GifReader.kt` | A minimal GIF decoder used only by the tests, because `javax.imageio` is not on the Android unit-test classpath. |
| `test/utilities/ImageResamplerTest.kt` | Flat colour, alpha bleed, gradients, up/downscale. |

**Do not run `connectedAndroidTest`.** It uninstalls the app when it finishes, taking the imported
library, the generated GIFs and every preference with it. There is an upstream `ScreenshotTest` in
`androidTest/` that will do this if invoked.

## Storage layout

```
filesDir/
  stickers/<pack>/<name>.webp     imported stickers, what the grid shows
  stickers/__compatSticker__/     cached PNG fallbacks (never pruned as a pack)
  variants/<pack>/<name>.gif      GIF versions, generated or imported
  import-manifest.tsv             what the last import copied (fork)
  logs/<date>                     xlog output
```

Preferences live in two files: `backup_prefs` (options, the `favourites` set, and the
`libraryVersion` counter) and the default preferences (sticker directory, caches, `activePack`).

Nothing in app storage is irreplaceable *except* `variants/`: a generated GIF has no counterpart in
the sticker folder, so anything that deletes it must be able to regenerate it or must not run.
