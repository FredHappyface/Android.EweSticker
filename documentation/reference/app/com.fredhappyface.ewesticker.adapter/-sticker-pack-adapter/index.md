//[app](../../../index.md)/[com.fredhappyface.ewesticker.adapter](../index.md)/[StickerPackAdapter](index.md)

# StickerPackAdapter

[androidJvm]\
class [StickerPackAdapter](index.md)(iconSize: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), stickers: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[File](https://developer.android.com/reference/kotlin/java/io/File.html)&gt;, listener: [StickerClickListener](../../com.fredhappyface.ewesticker.utilities/-sticker-click-listener/index.md), gestureDetector: [GestureDetector](https://developer.android.com/reference/kotlin/android/view/GestureDetector.html)) : [RecyclerView.Adapter](https://developer.android.com/reference/kotlin/androidx/recyclerview/widget/RecyclerView.Adapter.html)&lt;[StickerPackViewHolder](../../com.fredhappyface.ewesticker.view/-sticker-pack-view-holder/index.md)&gt;

## Constructors

| | |
|---|---|
| [StickerPackAdapter](-sticker-pack-adapter.md) | [androidJvm]<br>constructor(iconSize: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), stickers: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[File](https://developer.android.com/reference/kotlin/java/io/File.html)&gt;, listener: [StickerClickListener](../../com.fredhappyface.ewesticker.utilities/-sticker-click-listener/index.md), gestureDetector: [GestureDetector](https://developer.android.com/reference/kotlin/android/view/GestureDetector.html)) |

## Functions

| Name | Summary |
|---|---|
| [getItemCount](get-item-count.md) | [androidJvm]<br>open override fun [getItemCount](get-item-count.md)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [onBindViewHolder](on-bind-view-holder.md) | [androidJvm]<br>open override fun [onBindViewHolder](on-bind-view-holder.md)(holder: [StickerPackViewHolder](../../com.fredhappyface.ewesticker.view/-sticker-pack-view-holder/index.md), position: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
| [onCreateViewHolder](on-create-view-holder.md) | [androidJvm]<br>open override fun [onCreateViewHolder](on-create-view-holder.md)(parent: [ViewGroup](https://developer.android.com/reference/kotlin/android/view/ViewGroup.html), viewType: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [StickerPackViewHolder](../../com.fredhappyface.ewesticker.view/-sticker-pack-view-holder/index.md) |
