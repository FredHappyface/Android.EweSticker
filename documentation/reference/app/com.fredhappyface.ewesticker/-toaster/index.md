//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[Toaster](index.md)

# Toaster

[androidJvm]\
class [Toaster](index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html))

The Toaster class provides a simplified interface to android.widget.Toast. Pass in the android.content.Context to the constructor and call the 'toast' function (others as below) toaster.state keeps track of an error state or similar.

## Constructors

| | |
|---|---|
| [Toaster](-toaster.md) | [androidJvm]<br>fun [Toaster](-toaster.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html)) |

## Functions

| Name | Summary |
|---|---|
| [setState](set-state.md) | [androidJvm]<br>fun [setState](set-state.md)(state: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Set the state to some integer value |
| [toast](toast.md) | [androidJvm]<br>fun [toast](toast.md)(string: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Call toaster.toast with some string to always create a toast notification. Context is set when Toaster is instantiated. Duration is determined based on text length |
| [toastOnState](toast-on-state.md) | [androidJvm]<br>fun [toastOnState](toast-on-state.md)(strings: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;)<br>Call toaster.toastOnState with an array of messages to create a toast notification. Context is set when Toaster is instantiated. Duration is determined based on text length. The message is selected based on the state (which can be set in a callback function or elsewhere |
