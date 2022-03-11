//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[StickerSender](index.md)

# StickerSender

[androidJvm]\
class [StickerSender](index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](../-toaster/index.md), internalDir: [File](https://developer.android.com/reference/kotlin/java/io/File.html), currentInputConnection: [InputConnection](https://developer.android.com/reference/kotlin/android/view/inputmethod/InputConnection.html)?, currentInputEditorInfo: [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?, compatCache: [Cache](../-cache/index.md), imageLoader: ImageLoader)

The StickerSender Class used to contain all of the methods used for sending a sticker to an InputConnection

## Constructors

| | |
|---|---|
| [StickerSender](-sticker-sender.md) | [androidJvm]<br>fun [StickerSender](-sticker-sender.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](../-toaster/index.md), internalDir: [File](https://developer.android.com/reference/kotlin/java/io/File.html), currentInputConnection: [InputConnection](https://developer.android.com/reference/kotlin/android/view/inputmethod/InputConnection.html)?, currentInputEditorInfo: [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?, compatCache: [Cache](../-cache/index.md), imageLoader: ImageLoader) |

## Functions

| Name | Summary |
|---|---|
| [doCommitContent](do-commit-content.md) | [androidJvm]<br>private fun [doCommitContent](do-commit-content.md)(mimeType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), file: [File](https://developer.android.com/reference/kotlin/java/io/File.html))<br>Send a sticker file to a InputConnectionCompat |
| [doFallbackCommitContent](do-fallback-commit-content.md) | [androidJvm]<br>private suspend fun [doFallbackCommitContent](do-fallback-commit-content.md)(file: [File](https://developer.android.com/reference/kotlin/java/io/File.html))<br>In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you, Signal) create a temporary png and send that. In the event that png is not supported, alert the user. |
| [isCommitContentSupported](is-commit-content-supported.md) | [androidJvm]<br>private fun [isCommitContentSupported](is-commit-content-supported.md)(editorInfo: [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?, mimeType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Check if the sticker is supported by the receiver |
| [sendSticker](send-sticker.md) | [androidJvm]<br>fun [sendSticker](send-sticker.md)(file: [File](https://developer.android.com/reference/kotlin/java/io/File.html))<br>Start the process of sending a sticker when the sticker is tapped in the keyboard. If the sticker type is not supported by the InputConnection then doFallbackCommitContent, otherwise doCommitContent |

## Properties

| Name | Summary |
|---|---|
| [compatCache](compat-cache.md) | [androidJvm]<br>private val [compatCache](compat-cache.md): [Cache](../-cache/index.md)<br>: used to track previous x converted compat stickers |
| [context](context.md) | [androidJvm]<br>private val [context](context.md): [Context](https://developer.android.com/reference/kotlin/android/content/Context.html)<br>: application baseContext |
| [currentInputConnection](current-input-connection.md) | [androidJvm]<br>private val [currentInputConnection](current-input-connection.md): [InputConnection](https://developer.android.com/reference/kotlin/android/view/inputmethod/InputConnection.html)?<br>: the currentInputConnection. i.e. the input field that the keyboard is going to send a sticker to |
| [currentInputEditorInfo](current-input-editor-info.md) | [androidJvm]<br>private val [currentInputEditorInfo](current-input-editor-info.md): [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?<br>: currentInputEditorInfo. i.e. info on the input field that the keyboard is going to send a sticker to |
| [imageLoader](image-loader.md) | [androidJvm]<br>private val [imageLoader](image-loader.md): ImageLoader<br>: coil imageLoader object used to convert a sticker file to a drawable ready for writing to a compat sticker |
| [internalDir](internal-dir.md) | [androidJvm]<br>private val [internalDir](internal-dir.md): [File](https://developer.android.com/reference/kotlin/java/io/File.html)<br>: the internal /stickers directory used when creating a compat sticker |
| [supportedMimes](supported-mimes.md) | [androidJvm]<br>private val [supportedMimes](supported-mimes.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt; |
| [toaster](toaster.md) | [androidJvm]<br>private val [toaster](toaster.md): [Toaster](../-toaster/index.md)<br>: an instance of Toaster (used to store an error state for later reporting to the user) |
