package com.fredhappyface.ewesticker.utilities

import java.io.File

/**
 * What the imported library actually contains.
 *
 * @property packs Int number of sticker packs
 * @property stickers Int number of stickers across all packs
 * @property animated Int how many of those stickers carry animation
 * @property withVariant Int how many animated stickers have a GIF version ready to send
 * @property stickerBytes Long disk used by the imported stickers
 * @property variantBytes Long disk used by the generated GIF versions
 */
data class LibraryStats(
	val packs: Int,
	val stickers: Int,
	val animated: Int,
	val withVariant: Int,
	val stickerBytes: Long,
	val variantBytes: Long,
) {
	/** Animated stickers that would arrive as a still image in a GIF-only app right now */
	val missingVariants: Int
		get() = animated - withVariant

	val isEmpty: Boolean
		get() = stickers == 0
}

/**
 * Measures the imported library.
 *
 * The settings screen previously reported a path, a date and one total for a library of several
 * hundred stickers and about a hundred megabytes, which said nothing about how much of it would
 * actually animate where the user sends it, or where the space had gone.
 *
 * Not cheap enough for the main thread: it stats every sticker and reads the first bytes of each to
 * tell animated from still.
 */
object LibraryScanner {
	fun scan(filesDir: File): LibraryStats {
		val stickersDir = File(filesDir, "stickers")
		val variantsDir = File(filesDir, "variants")

		var packs = 0
		var stickers = 0
		var animated = 0
		var withVariant = 0
		var stickerBytes = 0L

		stickersDir.listFiles()
			?.filter { it.isDirectory && !it.name.contains("__compatSticker__") }
			?.forEach { pack ->
				val files = pack.listFiles()?.filter { it.isFile } ?: emptyList()
				if (files.isEmpty()) {
					return@forEach
				}
				packs++
				for (sticker in files) {
					stickers++
					stickerBytes += sticker.length()
					if (WebpAnimation.isAnimated(sticker)) {
						animated++
						if (File(variantsDir, "${pack.name}/${sticker.nameWithoutExtension}.gif").isFile) {
							withVariant++
						}
					}
				}
			}

		val variantBytes = variantsDir.walkTopDown()
			.filter { it.isFile }
			.sumOf { it.length() }

		return LibraryStats(
			packs = packs,
			stickers = stickers,
			animated = animated,
			withVariant = withVariant,
			stickerBytes = stickerBytes,
			variantBytes = variantBytes,
		)
	}

	/** Delete every generated GIF, returning how much space that reclaimed */
	fun deleteVariants(filesDir: File): Long {
		val variantsDir = File(filesDir, "variants")
		val freed = variantsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
		variantsDir.deleteRecursively()
		return freed
	}

	/** Bytes as something readable next to a sticker count */
	fun formatBytes(bytes: Long): String {
		// Thresholds sit just below each boundary so that a value which would round up to 1024 of a
		// unit is promoted instead of being rendered as "1024 MB".
		val kb = 1024.0
		val mb = kb * 1024
		val gb = mb * 1024
		return when {
			bytes >= gb * 0.9995 -> "%.1f GB".format(bytes / gb)
			bytes >= mb * 0.995 -> "%.0f MB".format(bytes / mb)
			bytes >= kb * 0.995 -> "%.0f KB".format(bytes / kb)
			else -> "$bytes B"
		}
	}
}
