//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[StickerImporter](index.md)

# StickerImporter

[androidJvm]\
class [StickerImporter](index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](../../com.fredhappyface.ewesticker.utilities/-toaster/index.md), progressBar: LinearProgressIndicator)

The StickerImporter class includes a helper function to import stickers from a user-selected stickerDirPath (see importStickers). The class requires the application baseContext and an instance of Toaster (in turn requiring the application baseContext)

## Constructors

| | |
|---|---|
| [StickerImporter](-sticker-importer.md) | [androidJvm]<br>constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](../../com.fredhappyface.ewesticker.utilities/-toaster/index.md), progressBar: LinearProgressIndicator) |

## Functions

| Name | Summary |
|---|---|
| [importStickers](import-stickers.md) | [androidJvm]<br>suspend fun [importStickers](import-stickers.md)(stickerDirPath: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>Used by the ACTION_OPEN_DOCUMENT_TREE handler function to copy stickers from a stickerDirPath to the application internal storage for access later on by the keyboard |
