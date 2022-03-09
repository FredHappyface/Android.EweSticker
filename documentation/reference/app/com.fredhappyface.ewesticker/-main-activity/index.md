//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[MainActivity](index.md)

# MainActivity

[androidJvm]\
class [MainActivity](index.md) : [AppCompatActivity](https://developer.android.com/reference/kotlin/androidx/appcompat/app/AppCompatActivity.html)

MainActivity class inherits from the AppCompatActivity class - provides the settings view

## Constructors

| | |
|---|---|
| [MainActivity](-main-activity.md) | [androidJvm]<br>fun [MainActivity](-main-activity.md)() |

## Functions

| Name | Summary |
|---|---|
| [chooseDir](choose-dir.md) | [androidJvm]<br>fun [chooseDir](choose-dir.md)(view: [View](https://developer.android.com/reference/kotlin/android/view/View.html))<br>Called on button press to choose a new directory |
| [enableKeyboard](enable-keyboard.md) | [androidJvm]<br>fun [enableKeyboard](enable-keyboard.md)(view: [View](https://developer.android.com/reference/kotlin/android/view/View.html))<br>Called on button press to launch settings |
| [importStickers](import-stickers.md) | [androidJvm]<br>private fun [importStickers](import-stickers.md)(stickerDirPath: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Import files from storage to internal directory |
| [onCreate](on-create.md) | [androidJvm]<br>protected open override fun [onCreate](on-create.md)(savedInstanceState: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)?)<br>Sets up content view, shared prefs, etc. |
| [refreshStickerDirPath](refresh-sticker-dir-path.md) | [androidJvm]<br>private fun [refreshStickerDirPath](refresh-sticker-dir-path.md)()<br>Reads saved sticker dir path from preferences |
| [seekBar](seek-bar.md) | [androidJvm]<br>private fun [seekBar](seek-bar.md)(seekBar: [SeekBar](https://developer.android.com/reference/kotlin/android/widget/SeekBar.html), seekBarLabel: [TextView](https://developer.android.com/reference/kotlin/android/widget/TextView.html), sharedPrefKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), sharedPrefDefault: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), multiplier: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 1)<br>Add seekbar logic for each seekbar in the layout |
| [showChangedPrefText](show-changed-pref-text.md) | [androidJvm]<br>internal fun [showChangedPrefText](show-changed-pref-text.md)()<br>Reusable function to warn about changing preferences |
| [toggle](toggle.md) | [androidJvm]<br>private fun [toggle](toggle.md)(compoundButton: [CompoundButton](https://developer.android.com/reference/kotlin/android/widget/CompoundButton.html), sharedPrefKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), sharedPrefDefault: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false, callback: ([Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))<br>Add toggle logic for each toggle/ checkbox in the layout |

## Properties

| Name | Summary |
|---|---|
| [chooseDirResultLauncher](choose-dir-result-launcher.md) | [androidJvm]<br>private val [chooseDirResultLauncher](choose-dir-result-launcher.md): [ActivityResultLauncher](https://developer.android.com/reference/kotlin/androidx/activity/result/ActivityResultLauncher.html)&lt;[Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html)&gt;<br>Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to this.sharedPreferences and resets recentCache, compatCache |
| [contextView](context-view.md) | [androidJvm]<br>private lateinit var [contextView](context-view.md): [View](https://developer.android.com/reference/kotlin/android/view/View.html) |
| [sharedPreferences](shared-preferences.md) | [androidJvm]<br>private lateinit var [sharedPreferences](shared-preferences.md): [SharedPreferences](https://developer.android.com/reference/kotlin/android/content/SharedPreferences.html) |
| [toaster](toaster.md) | [androidJvm]<br>private lateinit var [toaster](toaster.md): [Toaster](../-toaster/index.md) |
