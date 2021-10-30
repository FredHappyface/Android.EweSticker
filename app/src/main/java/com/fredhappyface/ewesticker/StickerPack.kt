package com.fredhappyface.ewesticker

import java.io.File

/**
 * Helper class to provide pack-related information
 * A "Pack" is informally represented as a File
 */
class StickerPack(packDir: File) {
	private val mStickers: Array<File>? =
		packDir.listFiles { obj: File -> obj.isFile }?.sortedArray()

	/**
	 * Note: When MainActivity copies files over, it filters out all non-supported files (i.e. any
	 * file that is not supported as well as directories). Because of this there is no extra filter
	 * in this function. The exception is the base directory, which is handled in the constructor.
	 *
	 * @return Array of Files corresponding to all stickers found in this pack
	 */
	val stickerList: Array<File>
		get() = mStickers ?: arrayOf()

	/**
	 * Provides a sticker to use as the pack-nav container thumbnail.
	 * Currently just takes the first element, but could theoretically include any selection logic.
	 *
	 * @return File that should be used for thumbnail
	 */
	val thumbSticker: File
		get() = mStickers!![0]
}
