//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[StickerPack](index.md)

# StickerPack

[androidJvm]\
class [StickerPack](index.md)(packDir: [File](https://developer.android.com/reference/kotlin/java/io/File.html))

Helper class to provide pack-related information A "Pack" is informally represented as a File

## Constructors

| | |
|---|---|
| [StickerPack](-sticker-pack.md) | [androidJvm]<br>fun [StickerPack](-sticker-pack.md)(packDir: [File](https://developer.android.com/reference/kotlin/java/io/File.html)) |

## Properties

| Name | Summary |
|---|---|
| [stickerList](sticker-list.md) | [androidJvm]<br>val [stickerList](sticker-list.md): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[File](https://developer.android.com/reference/kotlin/java/io/File.html)&gt;<br>Note: When MainActivity copies files over, it filters out all non-supported files (i.e. any file that is not supported as well as directories). Because of this there is no extra filter in this function. The exception is the base directory, which is handled in the constructor. |
| [thumbSticker](thumb-sticker.md) | [androidJvm]<br>val [thumbSticker](thumb-sticker.md): [File](https://developer.android.com/reference/kotlin/java/io/File.html)<br>Provides a sticker to use as the pack-nav container thumbnail. Currently just takes the first element, but could theoretically include any selection logic. |
