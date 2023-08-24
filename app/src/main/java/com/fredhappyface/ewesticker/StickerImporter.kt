package com.fredhappyface.ewesticker

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.documentfile.provider.DocumentFile
import com.fredhappyface.ewesticker.utilities.Toaster
import com.fredhappyface.ewesticker.utilities.Utils
import com.google.android.material.progressindicator.LinearProgressIndicator
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
	private val progressBar: LinearProgressIndicator,
) {
	private val supportedMimes = Utils.getSupportedMimes()
	private val packSizes: MutableMap<String, Int> = mutableMapOf()
	private var totalStickers = 0

	private val mainHandler = Handler(Looper.getMainLooper())

	private fun updateProgressBar(currentProgress: Int, totalStickers: Int) {
		val progressPercentage = (currentProgress.toFloat() / totalStickers.toFloat()) * 100
		progressBar.progress = progressPercentage.toInt()
	}


	/**
	 * Used by the ACTION_OPEN_DOCUMENT_TREE handler function to copy stickers from a
	 * stickerDirPath to the application internal storage for access later on by the
	 * keyboard
	 *
	 * @param stickerDirPath a URI to the stickers directory to import into EweSticker
	 */
	suspend fun importStickers(stickerDirPath: String): Int {
		File(context.filesDir, "stickers").deleteRecursively()
		withContext(Dispatchers.Main) {
			progressBar.visibility = View.VISIBLE
			progressBar.isIndeterminate = true
		}

		val leafNodes = fileWalk(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
		if (leafNodes.size > MAX_FILES) {
			toaster.setState(1)
		}

		withContext(Dispatchers.Main) {
			progressBar.isIndeterminate = false
		}


		// Perform concurrent file copy operations
		withContext(Dispatchers.IO) {
			leafNodes.take(MAX_FILES).mapIndexed { index, file ->
				async {
					importSticker(file)
					mainHandler.post {
						updateProgressBar(index + 1, leafNodes.size)
					}
				}
			}.awaitAll()
		}

		withContext(Dispatchers.Main) {
			progressBar.visibility = View.GONE
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
				withContext(Dispatchers.IO) {
					inputStream.close()
				}
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
		val stack = ArrayDeque<DocumentFile?>()

		rootNode?.let { stack.addLast(it) }

		while (stack.isNotEmpty() && leafNodes.size < MAX_FILES) {
			val currentFile = stack.removeLast()

			currentFile?.listFiles()?.forEach { file ->
				if (file.isFile) {
					leafNodes.add(file)
					totalStickers++

					if (leafNodes.size >= MAX_FILES) {
						return leafNodes
					}
				} else if (file.isDirectory) {
					stack.addLast(file)
				}
			}
		}
		return leafNodes
	}
}
