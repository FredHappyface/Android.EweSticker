package com.fredhappyface.ewesticker.utilities

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.elvishew.xlog.XLog
import com.fredhappyface.ewesticker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

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
 * @property isPngFallback: is a png fallback enabled
 * @property generateGifVariants: convert an animated sticker to a GIF at send time when the target
 * takes image/gif and no variant exists yet
 */
class StickerSender(
	private val context: Context,
	private val toaster: Toaster,
	private val internalDir: File,
	private val currentInputConnection: InputConnection?,
	private val currentInputEditorInfo: EditorInfo?,
	private val compatCache: Cache,
	private val imageLoader: ImageLoader,
	private val isPngFallback: Boolean,
	private val generateGifVariants: Boolean = false,
) {

	private val supportedMimes = this.currentInputEditorInfo?.contentMimeTypes ?: emptyArray()
	private val packageName = this.currentInputEditorInfo?.packageName

	/**
	 * Set once this sender no longer owns the input field. An on-demand conversion can outlive the
	 * field it started in, and committing or opening a share sheet after that puts a chooser on top
	 * of whatever the user moved on to.
	 */
	@Volatile
	private var abandoned = false

	/** Stop acting on a field this sender no longer owns. */
	fun abandon() {
		abandoned = true
	}

	init {
		XLog.i("Connecting to $packageName which supports [${supportedMimes.joinToString(", ")}]")
	}

	/**
	 * Wrapper function to display a toast message to the user
	 *
	 * @param message String
	 */
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

		// A zero-length file satisfies exists() forever, so a write that failed once would never be
		// retried. Length is the honest test of "do we already have this".
		if (compatSticker.length() == 0L) {
			XLog.i("Create a fallback png sticker '__compatSticker__/$compatStickerName.png'")

			compatSticker.parentFile?.mkdirs()
			val request = ImageRequest.Builder(context)
				.data(file)
				// Without an explicit size, coil resolves one from the display rather than the
				// source, so the fallback came out rescaled and soft. A stand-in for a sticker
				// should be exactly the resolution of the sticker it replaces.
				.size(Size.ORIGINAL)
				.build()

			// execute() reports a decode failure by returning an ErrorResult rather than throwing.
			// Running the encode in a target callback hid that: no file was written, yet this
			// returned a path anyway, and commitContent then reported success for a sticker that
			// never arrived.
			val drawable = (imageLoader.execute(request) as? SuccessResult)?.drawable
			if (drawable == null) {
				XLog.e("Could not decode '${file.name}' for a png fallback")
				showToast(context.getString(R.string.fallback_041))
				return null
			}

			// Encoding a full-resolution png and writing it are both slow enough to be felt when
			// they happen on the thread that draws the keyboard, which is where the target callback
			// used to put them.
			val written = withContext(Dispatchers.IO) {
				try {
					FileOutputStream(compatSticker).use { stream ->
						drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
					}
				} catch (e: IOException) {
					XLog.e("Could not write the png fallback for '${file.name}'")
					XLog.e(e)
					false
				}
			}
			if (!written || compatSticker.length() == 0L) {
				compatSticker.delete()
				showToast(context.getString(R.string.fallback_041))
				return null
			}
		}

		compatCache.add(compatStickerName)?.let {
			File(internalDir, "__compatSticker__/$it.png").delete()
		}

		return compatSticker
	}

	/**
	 * Pick the file and mimetype to actually commit for a sticker, given what the target app says
	 * it accepts. An exact mimetype match always wins. Failing that, for webp:
	 *
	 * 1. Prefer any vendor-specific image/webp.* mimetype the target advertises. WhatsApp never
	 *    advertises plain image/webp; it exposes image/webp.wasticker, and only content committed
	 *    under that mimetype is treated as a sticker rather than flattened into a photo -- which is
	 *    also what preserves animation.
	 * 2. Otherwise, if the target takes image/gif, send a GIF variant of the sticker if one was
	 *    generated (see tools/gif-variants.py). Apps like Instagram and Teams advertise no webp
	 *    mimetype at all, so a GIF is the only way to keep an animated sticker moving there.
	 *
	 * @param file File the sticker to send
	 * @param stickerType String the mimetype derived from the file extension
	 * @return Pair<File, String> the file to commit and the mimetype to commit it under
	 */
	private fun resolveCommit(file: File, stickerType: String): Pair<File, String> {
		// A wildcard counts as accepting the original. Without this an app advertising both image/*
		// and image/gif would be handed the downscaled, 128-colour GIF variant even though it would
		// have taken the full-quality webp.
		if (stickerType in supportedMimes ||
			("image/*" in supportedMimes && stickerType.startsWith("image/")) ||
			("video/*" in supportedMimes && stickerType.startsWith("video/"))
		) {
			return file to stickerType
		}
		if (stickerType == "image/webp") {
			supportedMimes.firstOrNull { it.startsWith("image/webp.") }?.let {
				XLog.i("Committing ${file.name} as '$it' for $packageName")
				return file to it
			}
			if ("image/gif" in supportedMimes) {
				formatVariant(file, "gif")?.let {
					XLog.i("Committing ${file.name} as its GIF variant for $packageName")
					return it to "image/gif"
				}
			}
		}
		return file to stickerType
	}

	/**
	 * Look for an alternate encoding of a sticker, imported alongside it into a parallel tree so
	 * that it never shows up as a duplicate in the keyboard grid.
	 *
	 * @param file File the sticker to find a variant of
	 * @param extension String the extension of the wanted variant
	 * @return File? the variant, or null if it was never generated
	 */
	private fun formatVariant(file: File, extension: String): File? {
		val pack = file.parentFile?.name ?: return null
		// Note internalDir is the stickers tree itself, whereas the importer keeps variants in a
		// sibling of it so that they are never enumerated as packs.
		val variant =
			File(context.filesDir, "variants/$pack/${file.nameWithoutExtension}.$extension")
		return variant.takeIf { it.isFile }
	}

	/**
	 * Main method to send a sticker to an InputConnectionCompat, this attempts to send via the happy path (assuming the
	 * InputConnectionCompat supports the stickers mime type. Otherwise attempts falling back to png, and then finally if that fails,
	 * opening a share sheet
	 */
	fun sendSticker(file: File) {
		val stickerType = Utils.getMimeType(file) ?: "__unknown__"
		val (commitFile, commitType) = resolveCommit(file, stickerType)

		// Try and only send as is if the app explicitly supports it
		// Note: Many apps do not support svg, so send as png regardless!
		if ((commitType in supportedMimes
				|| "image/*" in supportedMimes && stickerType.startsWith("image/")
				|| "video/*" in supportedMimes && stickerType.startsWith("video/"))
			&& stickerType != "image/svg+xml"
		) {
			if (!doCommitContent(commitType, commitFile)) {
				CoroutineScope(Dispatchers.Main).launch {
					doFallbackCommitContent(stickerType, file)
				}
			}
		} else {
			CoroutineScope(Dispatchers.Main).launch {
				doFallbackCommitContent(stickerType, file)
			}
		}

	}

	/**
	 * Called by sendSticker. Send a sticker file to a InputConnectionCompat
	 *
	 * @param mimeType String
	 * @param file File
	 * @return success Boolean
	 */
	private fun doCommitContent(mimeType: String, file: File): Boolean {
		XLog.i("Sending ${file.name} ($mimeType) to ${this.packageName}")
		// getUriForFile throws if the file sits outside every root in file_paths.xml. Reporting
		// failure lets the caller fall back, which beats taking the whole keyboard down mid-send.
		val uri = try {
			FileProvider.getUriForFile(
				context,
				"com.fredhappyface.ewesticker.inputcontent",
				file,
			)
		} catch (e: IllegalArgumentException) {
			XLog.e("Cannot serve '${file.path}' over the file provider")
			XLog.e(e)
			return false
		}
		val inputContentInfoCompat = InputContentInfoCompat(
			uri,
			ClipDescription(file.name, arrayOf(mimeType)),
			null,
		)

		if (currentInputConnection != null && currentInputEditorInfo != null) {

			return InputConnectionCompat.commitContent(
				currentInputConnection,
				currentInputEditorInfo,
				inputContentInfoCompat,
				InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
				null,
			)
		}
		return false
	}

	/**
	 * Called by sendSticker. Otherwise attempts falling back to png, and then finally if that fails, opening a
	 * share sheet to send the sticker
	 *
	 * @param mimeType String
	 * @param file File
	 */
	private suspend fun doFallbackCommitContent(mimeType: String, file: File) {

		// Before settling for a still image, try producing the GIF this sticker never got. Doing it
		// here means only the animated stickers actually sent to a GIF-only app are ever converted,
		// and the result lands in the variants tree, so the next send of it is immediate.
		convertVariantOnDemand(file)?.let {
			if (doCommitContent("image/gif", it)) {
				return
			}
		}
		if (abandoned) {
			XLog.i("Input field went away while converting, abandoning the send")
			return
		}

		if (isPngFallback && ("image/png" in supportedMimes || "image/*" in supportedMimes)) {
			val compatSticker = createCompatSticker(file)
			if (compatSticker != null) {
				if (!doCommitContent("image/png", compatSticker)) {
					openShareSheet(mimeType, file)
				} else if (WebpAnimation.isAnimated(file)) {
					// A png of an animated sticker drops the animation without saying so, which
					// just looks broken. Distinguish an app that cannot take a GIF at all from one
					// that could, had a variant been generated for this sticker.
					showToast(
						context.getString(
							if ("image/gif" in supportedMimes) {
								R.string.fallback_no_gif
							} else {
								R.string.fallback_animation
							},
						),
					)
				}
				return
			}
		}
		openShareSheet(mimeType, file)

	}

	/**
	 * Build the GIF variant of an animated sticker that does not have one yet, for a target that
	 * takes image/gif but no webp. Returns null whenever that does not apply, or when conversion
	 * fails, leaving the caller to fall back as before.
	 *
	 * @param file File the sticker being sent
	 * @return File? the freshly written GIF, or null
	 */
	private suspend fun convertVariantOnDemand(file: File): File? {
		if (abandoned || !generateGifVariants || "image/gif" !in supportedMimes) {
			return null
		}
		if (!WebpAnimation.isAnimated(file) || formatVariant(file, "gif") != null) {
			return null
		}
		val pack = file.parentFile?.name ?: return null
		val destination =
			File(context.filesDir, "variants/$pack/${file.nameWithoutExtension}.gif")

		// Conversion takes seconds, which is long enough for an impatient second tap. Serialising on
		// the destination means the second tap waits for the first result instead of starting a
		// duplicate conversion, and the re-checks inside the lock let it use what just landed.
		val lock = conversionLocks.computeIfAbsent(destination.path) { Mutex() }
		lock.withLock {
			if (destination.isFile) {
				return destination
			}
			if (destination.path in failedConversions) {
				return null
			}
			showToast(context.getString(R.string.fallback_converting))
			val converted = withContext(Dispatchers.Default) {
				// The decoder now runs inside the keyboard process, where anything escaping takes
				// the keyboard down mid-typing. convert() only handles IOException and
				// OutOfMemoryError, and a malformed sticker can produce neither of those.
				runCatching {
					GifVariantGenerator(context, toaster, null).convert(file, destination)
				}.getOrElse {
					if (it is CancellationException) {
						throw it
					}
					XLog.e("On-demand GIF conversion threw for '${file.name}'")
					XLog.e(it)
					false
				}
			}
			if (!converted) {
				// Remember the failure: retrying costs seconds and will fail the same way.
				failedConversions.add(destination.path)
				XLog.e("On-demand GIF conversion failed for '${file.name}'")
				return null
			}
		}
		XLog.i("Converted '${file.name}' to a GIF variant on demand for $packageName")
		return destination.takeIf { it.isFile }
	}

	/**
	 * Called by doFallbackCommitContent. Opens a share sheet to send the sticker
	 *
	 * @param mimeType String
	 * @param file File
	 */
	private companion object {
		/**
		 * Shared across senders, because a new one is built for every input field while a conversion
		 * started in a previous field may still be running.
		 */
		val conversionLocks = ConcurrentHashMap<String, Mutex>()
		val failedConversions: MutableSet<String> =
			Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
	}

	private fun openShareSheet(mimeType: String, file: File) {
		XLog.i("$packageName reports that is doesn't support png over its InputConnectionCompat, so open a share sheet")
		val uri = FileProvider.getUriForFile(
			context,
			"com.fredhappyface.ewesticker.inputcontent",
			file,
		)

		val shareIntent = Intent().apply {
			action = Intent.ACTION_SEND
			putExtra(Intent.EXTRA_STREAM, uri)
			type = mimeType
		}

		shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		shareIntent.setPackage(packageName)

		val chooserIntent = Intent.createChooser(shareIntent, "Share Sticker")
		chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(chooserIntent)
	}

}
