//[app](../../../index.md)/[com.fredhappyface.ewesticker](../index.md)/[StickerSender](index.md)/[doFallbackCommitContent](do-fallback-commit-content.md)

# doFallbackCommitContent

[androidJvm]\
private suspend fun [doFallbackCommitContent](do-fallback-commit-content.md)(file: [File](https://developer.android.com/reference/kotlin/java/io/File.html))

In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you, Signal) create a temporary png and send that. In the event that png is not supported, alert the user.

## Parameters

androidJvm

| | |
|---|---|
| file | : File |
