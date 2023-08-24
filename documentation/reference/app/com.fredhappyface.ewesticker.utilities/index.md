//[app](../../index.md)/[com.fredhappyface.ewesticker.utilities](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [Cache](-cache/index.md) | [androidJvm]<br>class [Cache](-cache/index.md)(capacity: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 30)<br>Basically this behaved like an ordered set with some maximum capacity. When this capacity is exceeded an element is removed from the start |
| [SharedPrefHelper](-shared-pref-helper/index.md) | [androidJvm]<br>object [SharedPrefHelper](-shared-pref-helper/index.md) |
| [StickerClickListener](-sticker-click-listener/index.md) | [androidJvm]<br>interface [StickerClickListener](-sticker-click-listener/index.md) |
| [StickerSender](-sticker-sender/index.md) | [androidJvm]<br>class [StickerSender](-sticker-sender/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), toaster: [Toaster](-toaster/index.md), internalDir: [File](https://developer.android.com/reference/kotlin/java/io/File.html), currentInputConnection: [InputConnection](https://developer.android.com/reference/kotlin/android/view/inputmethod/InputConnection.html)?, currentInputEditorInfo: [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?, compatCache: [Cache](-cache/index.md), imageLoader: ImageLoader)<br>The StickerSender Class used to contain all of the methods used for sending a sticker to an InputConnection |
| [Toaster](-toaster/index.md) | [androidJvm]<br>class [Toaster](-toaster/index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html))<br>The Toaster class provides a simplified interface to android.widget.Toast. Pass in the android.content.Context to the constructor and call the 'toast' function (others as below) toaster.state keeps track of an error state or similar. |
| [Utils](-utils/index.md) | [androidJvm]<br>object [Utils](-utils/index.md)<br>Class to provide utils that are shared across ewesticker. |
