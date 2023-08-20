package com.fredhappyface.ewesticker

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fredhappyface.ewesticker.utilities.Toaster
import com.fredhappyface.ewesticker.utilities.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val MAX_FILES = 4096
private const val MAX_PACK_SIZE = 128
private const val BUFFER_SIZE = 64 * 1024  // 64 KB

/**
 * The StickerImporter class includes a helper function to import stickers from a user-selected
 * stickerDirPath (see importStickers). The class requires the application baseContext and an
 * instance of Toaster (in turn requiring the application baseContext)
 *
 * @property context: application baseContext
 * @property toaster: an instance of Toaster (used to store an error state for later reporting to the
 * user)
 */
class StickerImporter(
	private val context: Context,
	private val toaster: Toaster,
) {
	private val supportedMimes = Utils.getSupportedMimes()
	private val packSizes: MutableMap<String, Int> = mutableMapOf()
	private var totalStickers = 0


	/**
	 * Used by the ACTION_OPEN_DOCUMENT_TREE handler function to copy stickers from a
	 * stickerDirPath to the application internal storage for access later on by the
	 * keyboard
	 *
	 * @param stickerDirPath a URI to the stickers directory to import into EweSticker
	 */
	suspend fun importStickers(stickerDirPath: String): Int {
		File(context.filesDir, "stickers").deleteRecursively()
		val leafNodes = fileWalk(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
		if (leafNodes.size > MAX_FILES) {
			toaster.setState(1)
		}

		// Perform concurrent file copy operations
		withContext(Dispatchers.IO) {
			leafNodes.take(MAX_FILES).map { file ->
				async { importSticker(file) }
			}.awaitAll()
		}

		return leafNodes.size
	}

	/**
	 * Copies stickers from source to internal storage
	 *
	 * @param sticker sticker to copy over
	 *
	 * @return 1 if sticker imported successfully else 0
	 */
	private suspend fun importSticker(sticker: DocumentFile) {
		val parentDir = sticker.parentFile?.name ?: "__default__"
		val packSize = packSizes[parentDir] ?: 0
		if (packSize > MAX_PACK_SIZE) {
			toaster.setState(2)
			return
		}
		if (sticker.type !in supportedMimes) {
			toaster.setState(3)
			return
		}
		packSizes[parentDir] = packSize + 1

		val contentResolver = context.contentResolver
		try {
			val inputStream = contentResolver.openInputStream(sticker.uri)
			if (inputStream != null) {
				val destSticker = File(context.filesDir, "stickers/$parentDir/${sticker.name}")
				destSticker.parentFile?.mkdirs()

				withContext(Dispatchers.IO) {
					inputStream.buffered(BUFFER_SIZE).use { input ->
						destSticker.outputStream().buffered(BUFFER_SIZE).use { output ->
							input.copyTo(output)
						}
					}
				}
				inputStream.close()
				totalStickers++
			}
		} catch (_: IOException) {
		}
	}

	/**
	 * Get a MutableSet of DocumentFiles from a root node
	 *
	 * @param rootNode parent dir to get all files from
	 * @return MutableSet<DocumentFile> set of files
	 */
	private fun fileWalk(rootNode: DocumentFile?): Set<DocumentFile> {
		val leafNodes = mutableSetOf<DocumentFile>()
		if (rootNode == null || totalStickers >= MAX_FILES) {
			return leafNodes
		}
		rootNode.listFiles().forEach { file ->
			if (file.isFile) {
				leafNodes.add(file)
				totalStickers++
			} else if (file.isDirectory) {
				leafNodes.addAll(fileWalk(file))
			}
			if (totalStickers >= MAX_FILES) {
				return@forEach
			}
		}
		return leafNodes
	}
}
