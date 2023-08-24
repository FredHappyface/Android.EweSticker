//[app](../../../index.md)/[com.fredhappyface.ewesticker.utilities](../index.md)/[Toaster](index.md)/[toastOnState](toast-on-state.md)

# toastOnState

[androidJvm]\
fun [toastOnState](toast-on-state.md)(strings: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;)

Call toaster.toastOnState with an array of messages to create a toast notification. Context is set when Toaster is instantiated. Duration is determined based on text length. The message is selected based on the state (which can be set in a callback function or elsewhere

#### Parameters

androidJvm

| | |
|---|---|
| strings | : Array<String>. Array of potential messages to output. |
