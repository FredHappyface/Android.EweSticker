//[app](../../index.md)/[com.fredhappyface.ewesticker](index.md)

# Package com.fredhappyface.ewesticker

## Types

| Name | Summary |
|---|---|
| [Cache](-cache/index.md) | [androidJvm]<br>class [Cache](-cache/index.md)(size: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Basically this behaved like an ordered set with some maximum capacity. When this capacity is exceeded an element is removed from the start |
| [ImageKeyboard](-image-keyboard/index.md) | [androidJvm]<br>class [ImageKeyboard](-image-keyboard/index.md) : [InputMethodService](https://developer.android.com/reference/kotlin/android/inputmethodservice/InputMethodService.html)<br>ImageKeyboard class inherits from the InputMethodService class - provides the keyboard functionality |
| [MainActivity](-main-activity/index.md) | [androidJvm]<br>class [MainActivity](-main-activity/index.md) : [AppCompatActivity](https://developer.android.com/reference/kotlin/androidx/appcompat/app/AppCompatActivity.html)<br>MainActivity class inherits from the AppCompatActivity class - provides the settings view |
| [StickerImporter](-sticker-importer/index.md) | [androidJvm]<br>class [StickerImporter](-sticker-importer/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](-toaster/index.md))<br>The StickerImporter class includes a helper function to import stickers from a user-selected stickerDirPath (see importStickers). The class requires the application baseContext and an instance of Toaster (in turn requiring the application baseContext) |
| [StickerPack](-sticker-pack/index.md) | [androidJvm]<br>class [StickerPack](-sticker-pack/index.md)(packDir: [File](https://developer.android.com/reference/kotlin/java/io/File.html))<br>Helper class to provide pack-related information A "Pack" is informally represented as a File |
| [StickerSender](-sticker-sender/index.md) | [androidJvm]<br>class [StickerSender](-sticker-sender/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](-toaster/index.md), internalDir: [File](https://developer.android.com/reference/kotlin/java/io/File.html), currentInputConnection: [InputConnection](https://developer.android.com/reference/kotlin/android/view/inputmethod/InputConnection.html)?, currentInputEditorInfo: [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?, compatCache: [Cache](-cache/index.md), imageLoader: ImageLoader)<br>The StickerSender Class used to contain all of the methods used for sending a sticker to an InputConnection |
| [Toaster](-toaster/index.md) | [androidJvm]<br>class [Toaster](-toaster/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html))<br>The Toaster class provides a simplified interface to android.widget.Toast. Pass in the android.content.Context to the constructor and call the 'toast' function (others as below) toaster.state keeps track of an error state or similar. |
| [Utils](-utils/index.md) | [androidJvm]<br>object [Utils](-utils/index.md)<br>Class to provide utils that are shared across ewesticker. |

## Properties

| Name | Summary |
|---|---|
| [MAX_FILES](-m-a-x_-f-i-l-e-s.md) | [androidJvm]<br>private const val [MAX_FILES](-m-a-x_-f-i-l-e-s.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 4096 |
| [MAX_PACK_SIZE](-m-a-x_-p-a-c-k_-s-i-z-e.md) | [androidJvm]<br>private const val [MAX_PACK_SIZE](-m-a-x_-p-a-c-k_-s-i-z-e.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 128 |
