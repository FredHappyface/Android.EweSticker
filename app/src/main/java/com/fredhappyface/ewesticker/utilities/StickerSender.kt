package com.fredhappyface.ewesticker.utilities

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
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
import com.fredhappyface.ewesticker.R
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

	private val supportedMimes: List<String> by lazy {
		Utils.getSupportedMimes()
			.filter { isCommitContentSupported(this.currentInputEditorInfo, it) }
	}

	private fun showToast(message: String) {
		CoroutineScope(Dispatchers.Main).launch {
			toaster.toast(message)
		}
	}

	/**
	 * In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you,
	 * Signal) create a temporary png and send that. In the event that png is not supported, alert
	 * the user.
	 *
	 * @param file: File
	 */
	private suspend fun createCompatSticker(file: File): File? {
		val compatStickerName = file.hashCode().toString()
		val compatSticker = File(internalDir, "__compatSticker__/$compatStickerName.png")

		if (!compatSticker.exists()) {
			compatSticker.parentFile?.mkdirs()
			try {
				val request = ImageRequest.Builder(context)
					.data(file)
					.target { result ->
						val bitmap = result.toBitmap()
						bitmap.compress(
							Bitmap.CompressFormat.PNG,
							90,
							FileOutputStream(compatSticker)
						)
					}
					.build()
				imageLoader.execute(request)
			} catch (ignore: IOException) {
				showToast(context.getString(R.string.fallback_041))
				return null
			}
		}

		compatCache.add(compatStickerName)?.let {
			File(internalDir, "__compatSticker__/$it.png").delete()
		}

		return compatSticker
	}

	fun sendSticker(file: File) {
		val stickerType = Utils.getMimeType(file)
		if (stickerType == null || stickerType !in supportedMimes) {
			showToast(context.getString(R.string.fallback_040, file.extension))
			CoroutineScope(Dispatchers.Main).launch {
				doFallbackCommitContent(file)
			}
		} else {
			doCommitContent(stickerType, file)
		}
	}

	private fun openShareSheet(file: File) {
		val uri = FileProvider.getUriForFile(
			context,
			"com.fredhappyface.ewesticker.inputcontent",
			file
		)

		val shareIntent = Intent().apply {
			action = Intent.ACTION_SEND
			putExtra(Intent.EXTRA_STREAM, uri)
			type = "image/*"
		}

		shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

		val chooserIntent = Intent.createChooser(shareIntent, "Share Sticker")
		chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(chooserIntent)
	}

	private suspend fun doFallbackCommitContent(file: File) {
		if ("image/png" !in supportedMimes) {
			openShareSheet(file)
			return
		}
		val compatSticker = createCompatSticker(file)
		if (compatSticker != null) {
			doCommitContent("image/png", compatSticker)
		}
	}

	/**
	 * Send a sticker file to a InputConnectionCompat
	 *
	 * @param mimeType String
	 * @param file File
	 */
	private fun doCommitContent(mimeType: String, file: File) {
		val inputContentInfoCompat = InputContentInfoCompat(
			FileProvider.getUriForFile(
				context,
				"com.fredhappyface.ewesticker.inputcontent",
				file
			),
			ClipDescription(file.name, arrayOf(mimeType)),
			null
		)

		if (currentInputConnection != null && currentInputEditorInfo != null) {
			InputConnectionCompat.commitContent(
				currentInputConnection,
				currentInputEditorInfo,
				inputContentInfoCompat,
				InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
				null
			)
		}
	}

	/**
	 * Check if the sticker is supported by the receiver
	 *
	 * @param editorInfo: EditorInfo - the editor/ receiver
	 * @param mimeType: String - the image mimetype
	 * @return boolean - is the mimetype supported?
	 */
	private fun isCommitContentSupported(editorInfo: EditorInfo?, mimeType: String?): Boolean {
		return editorInfo?.packageName != null && mimeType != null && currentInputConnection != null &&
				EditorInfoCompat.getContentMimeTypes(editorInfo)
					.any { ClipDescription.compareMimeTypes(mimeType, it) }
	}
}
