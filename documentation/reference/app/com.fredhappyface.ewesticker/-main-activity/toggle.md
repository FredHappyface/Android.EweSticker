//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[MainActivity](index.md)/[toggle](toggle.md)

# toggle

[androidJvm]\
private fun [toggle](toggle.md)(compoundButton: [CompoundButton](https://developer.android.com/reference/kotlin/android/widget/CompoundButton.html), sharedPrefKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), sharedPrefDefault: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false, callback: ([Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))

Add toggle logic for each toggle/ checkbox in the layout

## Parameters

androidJvm

| | |
|---|---|
| compoundButton | CompoundButton |
| sharedPrefKey | String - Id/Key of the SharedPreferences to update |
| sharedPrefDefault | Boolean - default value (default=false) |
| callback | (Boolean) -> Unit - Add custom behaviour with a callback - for instance to disable some options |
