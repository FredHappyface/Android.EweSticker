//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[StickerImporter](index.md)

# StickerImporter

[androidJvm]\
class [StickerImporter](index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](../-toaster/index.md))

The StickerImporter class includes a helper function to import stickers from a user-selected stickerDirPath (see importStickers). The class requires the application baseContext and an instance of Toaster (in turn requiring the application baseContext)

## Constructors

| | |
|---|---|
| [StickerImporter](-sticker-importer.md) | [androidJvm]<br>fun [StickerImporter](-sticker-importer.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](../-toaster/index.md)) |

## Functions

| Name | Summary |
|---|---|
| [fileWalk](file-walk.md) | [androidJvm]<br>private fun [fileWalk](file-walk.md)(rootNode: [DocumentFile](https://developer.android.com/reference/kotlin/androidx/documentfile/provider/DocumentFile.html)?): [MutableSet](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-set/index.html)&lt;[DocumentFile](https://developer.android.com/reference/kotlin/androidx/documentfile/provider/DocumentFile.html)&gt;<br>Get a MutableSet of DocumentFiles from a root node |
| [importSticker](import-sticker.md) | [androidJvm]<br>private fun [importSticker](import-sticker.md)(sticker: [DocumentFile](https://developer.android.com/reference/kotlin/androidx/documentfile/provider/DocumentFile.html))<br>Copies stickers from source to internal storage |
| [importStickers](import-stickers.md) | [androidJvm]<br>fun [importStickers](import-stickers.md)(stickerDirPath: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>Used by the ACTION_OPEN_DOCUMENT_TREE handler function to copy stickers from a stickerDirPath to the application internal storage for access later on by the keyboard |

## Properties

| Name | Summary |
|---|---|
| [context](context.md) | [androidJvm]<br>private val [context](context.md): [Context](https://developer.android.com/reference/kotlin/android/content/Context.html)<br>: application baseContext |
| [filesLeft](files-left.md) | [androidJvm]<br>private var [filesLeft](files-left.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [packSizes](pack-sizes.md) | [androidJvm]<br>private var [packSizes](pack-sizes.md): [MutableMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt; |
| [supportedMimes](supported-mimes.md) | [androidJvm]<br>private val [supportedMimes](supported-mimes.md): [MutableList](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt; |
| [toaster](toaster.md) | [androidJvm]<br>private val [toaster](toaster.md): [Toaster](../-toaster/index.md)<br>: an instance of Toaster (used to store an error state for later reporting to the user) |
| [totalStickers](total-stickers.md) | [androidJvm]<br>private var [totalStickers](total-stickers.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 0 |
