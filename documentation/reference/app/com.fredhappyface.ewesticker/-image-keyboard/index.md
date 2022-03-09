//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[ImageKeyboard](index.md)

# ImageKeyboard

[androidJvm]\
class [ImageKeyboard](index.md) : [InputMethodService](https://developer.android.com/reference/kotlin/android/inputmethodservice/InputMethodService.html)

ImageKeyboard class inherits from the InputMethodService class - provides the keyboard functionality

## Constructors

| | |
|---|---|
| [ImageKeyboard](-image-keyboard.md) | [androidJvm]<br>fun [ImageKeyboard](-image-keyboard.md)() |

## Functions

| Name | Summary |
|---|---|
| [addPackButton](add-pack-button.md) | [androidJvm]<br>private fun [addPackButton](add-pack-button.md)(tag: [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)): [ImageButton](https://developer.android.com/reference/kotlin/android/widget/ImageButton.html) |
| [createPackIcons](create-pack-icons.md) | [androidJvm]<br>private fun [createPackIcons](create-pack-icons.md)()<br>Create the pack icons (image buttons) that when tapped switch the pack (switchPackLayout) |
| [createPackLayout](create-pack-layout.md) | [androidJvm]<br>private fun [createPackLayout](create-pack-layout.md)(stickers: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[File](https://developer.android.com/reference/kotlin/java/io/File.html)&gt;): [FrameLayout](https://developer.android.com/reference/kotlin/android/widget/FrameLayout.html)<br>Create the pack layout (called by switchPackLayout if the FrameLayout is not cached) |
| [createPartialPackLayout](create-partial-pack-layout.md) | [androidJvm]<br>private fun [createPartialPackLayout](create-partial-pack-layout.md)(): [Pair](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)&lt;[FrameLayout](https://developer.android.com/reference/kotlin/android/widget/FrameLayout.html), [GridLayout](https://developer.android.com/reference/kotlin/androidx/gridlayout/widget/GridLayout.html)&gt;<br>Create the initial pack layout (the pack container and the grid) |
| [onCreate](on-create.md) | [androidJvm]<br>open override fun [onCreate](on-create.md)()<br>When the activity is created... |
| [onCreateInputView](on-create-input-view.md) | [androidJvm]<br>open override fun [onCreateInputView](on-create-input-view.md)(): [View](https://developer.android.com/reference/kotlin/android/view/View.html)<br>When the keyboard is first drawn... |
| [onEvaluateFullscreenMode](on-evaluate-fullscreen-mode.md) | [androidJvm]<br>open override fun [onEvaluateFullscreenMode](on-evaluate-fullscreen-mode.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Disable full-screen mode as content will likely be hidden by the IME. |
| [onFinishInput](on-finish-input.md) | [androidJvm]<br>open override fun [onFinishInput](on-finish-input.md)()<br>When leaving some input field update the caches |
| [onStartInput](on-start-input.md) | [androidJvm]<br>open override fun [onStartInput](on-start-input.md)(info: [EditorInfo](https://developer.android.com/reference/kotlin/android/view/inputmethod/EditorInfo.html)?, restarting: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))<br>When entering some input field update the list of supported-mimes |
| [switchPackLayout](switch-pack-layout.md) | [androidJvm]<br>private fun [switchPackLayout](switch-pack-layout.md)(packName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Swap the pack layout every time a pack is selected. If already cached use that otherwise create the pack layout |

## Properties

| Name | Summary |
|---|---|
| [activePack](active-pack.md) | [androidJvm]<br>private var [activePack](active-pack.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [compatCache](compat-cache.md) | [androidJvm]<br>private var [compatCache](compat-cache.md): [Cache](../-cache/index.md) |
| [fullIconSize](full-icon-size.md) | [androidJvm]<br>private var [fullIconSize](full-icon-size.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 0 |
| [iconSize](icon-size.md) | [androidJvm]<br>private var [iconSize](icon-size.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 0 |
| [iconsPerX](icons-per-x.md) | [androidJvm]<br>private var [iconsPerX](icons-per-x.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 0 |
| [imageContainerCache](image-container-cache.md) | [androidJvm]<br>private var [imageContainerCache](image-container-cache.md): [HashMap](https://developer.android.com/reference/kotlin/java/util/HashMap.html)&lt;[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), [FrameLayout](https://developer.android.com/reference/kotlin/android/widget/FrameLayout.html)&gt; |
| [internalDir](internal-dir.md) | [androidJvm]<br>private lateinit var [internalDir](internal-dir.md): [File](https://developer.android.com/reference/kotlin/java/io/File.html) |
| [keyboardHeight](keyboard-height.md) | [androidJvm]<br>private var [keyboardHeight](keyboard-height.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 0 |
| [keyboardRoot](keyboard-root.md) | [androidJvm]<br>private lateinit var [keyboardRoot](keyboard-root.md): [ViewGroup](https://developer.android.com/reference/kotlin/android/view/ViewGroup.html) |
| [loadedPacks](loaded-packs.md) | [androidJvm]<br>private lateinit var [loadedPacks](loaded-packs.md): [HashMap](https://developer.android.com/reference/kotlin/java/util/HashMap.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [StickerPack](../-sticker-pack/index.md)&gt; |
| [packContent](pack-content.md) | [androidJvm]<br>private lateinit var [packContent](pack-content.md): [ViewGroup](https://developer.android.com/reference/kotlin/android/view/ViewGroup.html) |
| [packsList](packs-list.md) | [androidJvm]<br>private lateinit var [packsList](packs-list.md): [ViewGroup](https://developer.android.com/reference/kotlin/android/view/ViewGroup.html) |
| [recentCache](recent-cache.md) | [androidJvm]<br>private var [recentCache](recent-cache.md): [Cache](../-cache/index.md) |
| [sharedPreferences](shared-preferences.md) | [androidJvm]<br>private lateinit var [sharedPreferences](shared-preferences.md): [SharedPreferences](https://developer.android.com/reference/kotlin/android/content/SharedPreferences.html) |
| [stickerSender](sticker-sender.md) | [androidJvm]<br>private lateinit var [stickerSender](sticker-sender.md): [StickerSender](../-sticker-sender/index.md) |
| [toaster](toaster.md) | [androidJvm]<br>private lateinit var [toaster](toaster.md): [Toaster](../-toaster/index.md) |
| [totalIconPadding](total-icon-padding.md) | [androidJvm]<br>private var [totalIconPadding](total-icon-padding.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 0 |
| [vertical](vertical.md) | [androidJvm]<br>private var [vertical](vertical.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false |
