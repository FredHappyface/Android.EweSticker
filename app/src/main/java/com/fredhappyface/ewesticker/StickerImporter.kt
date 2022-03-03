package com.fredhappyface.ewesticker

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.file.Files

private const val MAX_FILES = 4096
private const val MAX_PACK_SIZE = 128

/**
 * TODO
 *
 * @property context
 * @property toaster
 */
class StickerImporter(
	private val context: Context,
	private val toaster: Toaster,
) {
	private val supportedMimes = Utils.getSupportedMimes()
	private var filesLeft = MAX_FILES
	private var packSizes: MutableMap<String, Int> = mutableMapOf()
	private var totalStickers = 0

	/**
	 * TODO
	 *
	 * @param stickerDirPath
	 */
	fun importStickers(stickerDirPath: String): Int {
		File(this.context.filesDir, "stickers").deleteRecursively()
		val leafNodes =
			fileWalk(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
		if (leafNodes.size > MAX_FILES) {
			this.toaster.setState(1)
		}
		for (file in leafNodes.take(MAX_FILES)) {
			importSticker(file)
		}
		return this.totalStickers
	}

	/**
	 * Copies stickers from source to internal storage
	 *
	 * @param sticker sticker to copy over
	 *
	 * @return 1 if sticker imported successfully else 0
	 */
	private fun importSticker(sticker: DocumentFile) {
		// Exit if sticker is unsupported or if pack size > MAX_PACK_SIZE
		val parentDir = sticker.parentFile?.name ?: "__default__"
		val packSize = this.packSizes[parentDir] ?: 0
		if (packSize > MAX_PACK_SIZE) {
			this.toaster.setState(2); return
		}
		if (sticker.type !in this.supportedMimes) {
			this.toaster.setState(3); return
		}
		this.packSizes[parentDir] = packSize + 1
		// Copy sticker to app storage
		val destSticker = File(this.context.filesDir, "stickers/${parentDir}/${sticker.name}")
		destSticker.parentFile?.mkdirs()
		try {
			val inputStream = context.contentResolver.openInputStream(sticker.uri)
			Files.copy(inputStream, destSticker.toPath())
			inputStream?.close()
		} catch (e: java.lang.Exception) {
		}
		this.totalStickers++
	}

	/**
	 * Get a MutableSet of DocumentFiles from a root node
	 *
	 * @param rootNode parent dir to get all files from
	 * @return MutableSet<DocumentFile> set of files
	 */
	private fun fileWalk(rootNode: DocumentFile?): MutableSet<DocumentFile> {
		val leafNodes = mutableSetOf<DocumentFile>()
		if (rootNode == null || this.filesLeft < 0) {
			return leafNodes
		}
		val files = rootNode.listFiles()
		for (file in files) {
			if (file.isFile) leafNodes.add(file)
			if (file.isDirectory) leafNodes.addAll(fileWalk(file))
		}
		this.filesLeft -= files.size
		return leafNodes
	}
}