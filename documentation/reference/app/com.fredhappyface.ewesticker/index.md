//[app](../../index.md)/[com.fredhappyface.ewesticker](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [ImageKeyboard](-image-keyboard/index.md) | [androidJvm]<br>class [ImageKeyboard](-image-keyboard/index.md) : [InputMethodService](https://developer.android.com/reference/kotlin/android/inputmethodservice/InputMethodService.html), [StickerClickListener](../com.fredhappyface.ewesticker.utilities/-sticker-click-listener/index.md)<br>ImageKeyboard class inherits from the InputMethodService class - provides the keyboard functionality |
| [MainActivity](-main-activity/index.md) | [androidJvm]<br>class [MainActivity](-main-activity/index.md) : [AppCompatActivity](https://developer.android.com/reference/kotlin/androidx/appcompat/app/AppCompatActivity.html)<br>MainActivity class inherits from the AppCompatActivity class - provides the settings view |
| [StickerImporter](-sticker-importer/index.md) | [androidJvm]<br>class [StickerImporter](-sticker-importer/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](../com.fredhappyface.ewesticker.utilities/-toaster/index.md), progressBar: LinearProgressIndicator)<br>The StickerImporter class includes a helper function to import stickers from a user-selected stickerDirPath (see importStickers). The class requires the application baseContext and an instance of Toaster (in turn requiring the application baseContext) |

## Functions

| Name | Summary |
|---|---|
| [trimString](trim-string.md) | [androidJvm]<br>fun [trimString](trim-string.md)(str: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>trimString |
