//[app](../../../index.md)/[com.fredhappyface.ewesticker.model](../index.md)/[StickerPack](index.md)/[stickerList](sticker-list.md)

# stickerList

[androidJvm]\
val [stickerList](sticker-list.md): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[File](https://developer.android.com/reference/kotlin/java/io/File.html)&gt;

Note: When MainActivity copies files over, it filters out all non-supported files (i.e. any file that is not supported as well as directories). Because of this there is no extra filter in this function. The exception is the base directory, which is handled in the constructor.

#### Return

Array of Files corresponding to all stickers found in this pack
