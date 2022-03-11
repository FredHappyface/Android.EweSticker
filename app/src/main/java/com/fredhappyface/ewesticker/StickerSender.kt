package com.fredhappyface.ewesticker

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The StickerSender Class used to contain all of the methods used for sending a sticker to an
 * InputConnection
 *
 * @property context: application baseContext
 * @property toaster: an instance of Toaster (used to store an error state for later reporting to the
 * user)
 * @property internalDir: the internal /stickers directory used when creating a compat sticker
 * @property currentInputConnection: the currentInputConnection. i.e. the input field that the
 * keyboard is going to send a sticker to
 * @property currentInputEditorInfo: currentInputEditorInfo. i.e. info on the input field that the
 * keyboard is going to send a sticker to
 * @property compatCache: used to track previous x converted compat stickers
 * @property imageLoader: coil imageLoader object used to convert a sticker file to a drawable ready
 * for writing to a compat sticker
 */
class StickerSender(
	private val context: Context,
	private val toaster: Toaster,
	private val internalDir: File,
	private val currentInputConnection: InputConnection?,
	private val currentInputEditorInfo: EditorInfo?,
	private val compatCache: Cache,
	private val imageLoader: ImageLoader,
) {

	private val supportedMimes =
		Utils.getSupportedMimes()
			.filter { isCommitContentSupported(this.currentInputEditorInfo, it) }

	/**
	 * Start the process of sending a sticker when the sticker is tapped in the
	 * keyboard. If the sticker type is not supported by the InputConnection then
	 * doFallbackCommitContent, otherwise doCommitContent
	 *
	 * @param file
	 */
	fun sendSticker(file: File) {
		val stickerType = Utils.getMimeType(file)
		if (stickerType == null || stickerType !in this.supportedMimes) {
			CoroutineScope(Dispatchers.Main).launch { doFallbackCommitContent(file) }
			return
		}
		doCommitContent(stickerType, file)
	}

	/**
	 * In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you,
	 * Signal) create a temporary png and send that. In the event that png is not supported, alert
	 * the user.
	 *
	 * @param file: File
	 */
	private suspend fun doFallbackCommitContent(file: File) {
		// PNG might not be supported
		if ("image/png" !in this.supportedMimes) {
			this.toaster.toast(context.getString(R.string.fallback_040, file.extension))
			return
		}
		// Create a new compatSticker and convert the sticker to png
		val compatStickerName = file.hashCode().toString()
		val compatSticker = File(this.internalDir, "__compatSticker__/$compatStickerName.png")
		if (!compatSticker.exists()) {
			// If the sticker doesn't exist then create
			compatSticker.parentFile?.mkdirs()
			try {
				val request =
					ImageRequest.Builder(this.context)
						.data(file)
						.target { result ->
							val bitmap = result.toBitmap()
							bitmap.compress(
								Bitmap.CompressFormat.PNG, 90, FileOutputStream(compatSticker)
							)
						}
						.build()
				this.imageLoader.execute(request)
			} catch (ignore: IOException) {
				this.toaster.toast(this.context.getString(R.string.fallback_041))
			}
		}
		// Send the compatSticker!
		doCommitContent("image/png", compatSticker)
		// Remove old stickers
		val remSticker = this.compatCache.add(compatStickerName)
		remSticker?.let { File(this.internalDir, "__compatSticker__/$remSticker.png").delete() }
	}

	/**
	 * Send a sticker file to a InputConnectionCompat
	 *
	 * @param mimeType String
	 * @param file File
	 */
	private fun doCommitContent(mimeType: String, file: File) {
		// ContentUri, ClipDescription, linkUri
		val inputContentInfoCompat =
			InputContentInfoCompat(
				FileProvider.getUriForFile(
					this.context,
					"com.fredhappyface.ewesticker.inputcontent",
					file
				),
				ClipDescription(file.name, arrayOf(mimeType)),
				null
			)
		// InputConnection, EditorInfo, InputContentInfoCompat, int flags, null opts
		if (this.currentInputConnection == null || this.currentInputEditorInfo == null) return
		InputConnectionCompat.commitContent(
			this.currentInputConnection,
			this.currentInputEditorInfo,
			inputContentInfoCompat,
			InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
			null
		)
	}

	/**
	 * Check if the sticker is supported by the receiver
	 *
	 * @param editorInfo: EditorInfo - the editor/ receiver
	 * @param mimeType: String - the image mimetype
	 * @return boolean - is the mimetype supported?
	 */
	private fun isCommitContentSupported(editorInfo: EditorInfo?, mimeType: String?): Boolean {
		editorInfo?.packageName ?: return false
		mimeType ?: return false
		this.currentInputConnection ?: return false
		EditorInfoCompat.getContentMimeTypes(editorInfo).forEach {
			if (ClipDescription.compareMimeTypes(mimeType, it)) {
				return true
			}
		}
		return false
	}
}
