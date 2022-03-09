//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[MainActivity](index.md)/[seekBar](seek-bar.md)

# seekBar

[androidJvm]\
private fun [seekBar](seek-bar.md)(seekBar: [SeekBar](https://developer.android.com/reference/kotlin/android/widget/SeekBar.html), seekBarLabel: [TextView](https://developer.android.com/reference/kotlin/android/widget/TextView.html), sharedPrefKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), sharedPrefDefault: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), multiplier: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 1)

Add seekbar logic for each seekbar in the layout

## Parameters

androidJvm

| | |
|---|---|
| seekBar | SeekBar |
| seekBarLabel | TextView - the label with a value updated when the progress is changed |
| sharedPrefKey | String - Id/Key of the SharedPreferences to update |
| sharedPrefDefault | Int - default value |
| multiplier | Int - multiplier (used to update SharedPreferences and set the seekBarLabel) |
