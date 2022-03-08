//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[Cache](index.md)

# Cache

[androidJvm]\
class [Cache](index.md)(size: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))

Basically this behaved like an ordered set with some maximum capacity. When this capacity is exceeded an element is removed from the start

## Constructors

| | |
|---|---|
| [Cache](-cache.md) | [androidJvm]<br>fun [Cache](-cache.md)(size: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 30) |

## Functions

| Name | Summary |
|---|---|
| [add](add.md) | [androidJvm]<br>fun [add](add.md)(elem: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>Logic to add an element |
| [fromSharedPref](from-shared-pref.md) | [androidJvm]<br>fun [fromSharedPref](from-shared-pref.md)(raw: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>convert from a string (shared-pref) to this |
| [get](get.md) | [androidJvm]<br>fun [get](get.md)(idx: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Get an element |
| [toFiles](to-files.md) | [androidJvm]<br>fun [toFiles](to-files.md)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[File](https://developer.android.com/reference/kotlin/java/io/File.html)&gt;<br>convert this to a array of files |
| [toSharedPref](to-shared-pref.md) | [androidJvm]<br>fun [toSharedPref](to-shared-pref.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>convert this to a string to write to shared-pref |
