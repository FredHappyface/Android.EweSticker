//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[ImageKeyboard](index.md)

# ImageKeyboard

[androidJvm]\
class [ImageKeyboard](index.md) : [InputMethodService](https://developer.android.com/reference/kotlin/android/inputmethodservice/InputMethodService.html), [StickerClickListener](../../com.fredhappyface.ewesticker.utilities/-sticker-click-listener/index.md)

ImageKeyboard class inherits from the InputMethodService class - provides the keyboard functionality

## Constructors

| | |
|---|---|
| [ImageKeyboard](-image-keyboard.md) | [androidJvm]<br>constructor() |

## Functions

| Name | Summary |
|---|---|
| [onCreate](on-create.md) | [androidJvm]<br>open override fun [onCreate](on-create.md)()<br>When the activity is created... |
| [onCreateInputView](on-create-input-view.md) | [androidJvm]<br>open override fun [onCreateInputView](on-create-input-view.md)(): [View](https://developer.android.com/reference/kotlin/android/view/View.html)<br>When the keyboard is first drawn... |
| [onEvaluateFullscreenMode](on-evaluate-fullscreen-mode.md) | [androidJvm]<br>open override fun [onEvaluateFullscreenMode](on-evaluate-fullscreen-mode.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Disable full-screen mode as content will likely be hidden by the IME. |
| [onFinishInput](on-finish-input.md) | [androidJvm]<br>open override fun [onFinishInput](on-finish-input.md)()<br>When leaving some input field update the caches |
| [onStartInput](on-start-input.md) | [androidJvm]<br>open override fun [onStartInput](on-start-input.md)(info: [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?, restarting: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))<br>When entering some input field update the list of supported-mimes |
| [onStickerClicked](on-sticker-clicked.md) | [androidJvm]<br>open override fun [onStickerClicked](on-sticker-clicked.md)(sticker: [File](https://developer.android.com/reference/kotlin/java/io/File.html))<br>onStickerClicked |
| [onStickerLongClicked](on-sticker-long-clicked.md) | [androidJvm]<br>open override fun [onStickerLongClicked](on-sticker-long-clicked.md)(sticker: [File](https://developer.android.com/reference/kotlin/java/io/File.html))<br>onStickerLongClicked |
