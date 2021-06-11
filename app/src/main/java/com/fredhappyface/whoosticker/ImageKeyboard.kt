package com.fredhappyface.whoosticker

import android.content.ClipDescription
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.preference.PreferenceManager
import com.github.penfeizhou.animation.apng.APNGDrawable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class ImageKeyboard : InputMethodService() {
	// Attributes
	private lateinit var supportedMimes: MutableMap<String, String>
	private var loadedPacks = HashMap<String, StickerPack>()
	private var imageContainer: LinearLayout? = null
	private var packContainer: LinearLayout? = null
	private lateinit var internalDir: File
	private var iconsPerRow = 0
	private var iconSize = 0
	private lateinit var sharedPreferences: SharedPreferences

	/**
	 * Adds a back button as a PackCard to keyboard that shows the InputMethodPicker
	 */
	private fun addBackButtonToContainer() {
		val packCard = layoutInflater.inflate(R.layout.pack_card, packContainer, false)
		val backButton = packCard.findViewById<ImageButton>(R.id.ib3)
		val icon =
			ResourcesCompat.getDrawable(resources, R.drawable.tabler_icon_arrow_back_white, null)
		backButton.setImageDrawable(icon)
		backButton.setOnClickListener {
			val inputMethodManager = applicationContext
				.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
			inputMethodManager.showInputMethodPicker()
		}
		packContainer!!.addView(packCard)
	}

	/**
	 * Adds a pack card to the keyboard from a StickerPack
	 *
	 * @param pack: StickerPack - the sticker pack to add
	 */
	private fun addPackToContainer(pack: StickerPack) {
		val packCard = layoutInflater.inflate(R.layout.pack_card, packContainer, false)
		val packButton = packCard.findViewById<ImageButton>(R.id.ib3)
		setPackButtonImage(pack, packButton)
		packButton.tag = pack
		packButton.setOnClickListener { view: View ->
			imageContainer!!.removeAllViewsInLayout()
			recreateImageContainer(view.tag as StickerPack)
		}
		packContainer!!.addView(packCard)
	}

	/**
	 * In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you, Signal)
	 * Create a temporary png and send that. In the event that png is not supported, create a toast as before
	 *
	 * @param file: File
	 */
	private fun doFallbackCommitContent(file: File) {
		// PNG might not be supported so fallback to toast
		if (supportedMimes[".png"] == null) {
			Toast.makeText(
				applicationContext, Utils.getFileExtension(file.name) +
						" not supported here.", Toast.LENGTH_LONG
			).show()
			return
		}
		// Create a new compatSticker and convert the sticker to png
		val compatSticker = File(filesDir, "stickers/__compatSticker__/__compatSticker__.png")
		compatSticker.parentFile?.mkdirs() // Protect against null pointer exception
		try {
			ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
				.compress(Bitmap.CompressFormat.PNG, 90, FileOutputStream(compatSticker))
		} catch (ignore: IOException) {
		}
		// Send the compatSticker!
		doCommitContent("description", "image/png", compatSticker)
	}

	/**
	 * @param description: String
	 * @param mimeType:    String
	 * @param file:        File
	 */
	private fun doCommitContent(
		description: String, mimeType: String,
		file: File
	) {
		val contentUri = FileProvider.getUriForFile(this, AUTHORITY, file)
		val flag = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
		val inputContentInfoCompat = InputContentInfoCompat(
			contentUri,
			ClipDescription(description, arrayOf(mimeType)),
			null
		)
		InputConnectionCompat.commitContent(
			currentInputConnection, currentInputEditorInfo, inputContentInfoCompat,
			flag, null
		)
	}

	/**
	 * Apply a sticker file to the image button
	 *
	 * @param sticker: File - the file object representing the sticker
	 * @param btn:     ImageButton - the button
	 */
	private fun setStickerButtonImage(sticker: File, btn: ImageButton) {
		val sName = sticker.name
		// Create drawable from file
		var drawable: Drawable? = null
		try {
			drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(sticker))
		} catch (ignore: IOException) {
		}
		if (sName.contains(".png") || sName.contains(".apng")) {
			drawable = APNGDrawable.fromFile(sticker.absolutePath)
			drawable!!.setAutoPlay(false)
			drawable.start()
		}
		// Disable animations?
		if (drawable is AnimatedImageDrawable && !sharedPreferences.getBoolean(
				"disable_animations",
				false
			)
		) {
			drawable.start()
		}
		if (drawable is APNGDrawable && sharedPreferences.getBoolean("disable_animations", false)) {
			drawable.stop()
		}
		// Apply
		btn.setImageDrawable(drawable)
	}

	/**
	 * Apply a sticker the the pack icon (imagebutton)
	 *
	 * @param pack: StickerPack - the stickerpack to grab the pack icon from
	 * @param btn:  ImageButton - the button
	 */
	private fun setPackButtonImage(pack: StickerPack, btn: ImageButton) {
		setStickerButtonImage(pack.thumbSticker, btn)
	}

	/**
	 * Check if the sticker is supported by the receiver
	 *
	 * @param editorInfo: EditorInfo - the editor/ receiver
	 * @param mimeType:   String - the image mimetype
	 * @return boolean - is the mimetype supported?
	 */
	private fun isCommitContentSupported(
		editorInfo: EditorInfo?, mimeType: String
	): Boolean {
		if (editorInfo == null) {
			return false
		}
		currentInputConnection ?: return false
		if (!validatePackageName(editorInfo)) {
			return false
		}
		val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
		for (supportedMimeType in supportedMimeTypes) {
			if (ClipDescription.compareMimeTypes(mimeType, supportedMimeType)) {
				return true
			}
		}
		return false
	}

	override fun onCreate() {
		super.onCreate()
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		iconsPerRow = sharedPreferences.getInt("iconsPerRow", 3)
		iconSize = sharedPreferences.getInt("iconSize", 160)
		reloadPacks()
	}

	override fun onCreateInputView(): View {
		val keyboardLayout =
			layoutInflater.inflate(R.layout.keyboard_layout, null) as RelativeLayout
		packContainer = keyboardLayout.findViewById(R.id.packContainer)
		imageContainer = keyboardLayout.findViewById(R.id.imageContainer)
		imageContainer?.layoutParams?.height = (iconSize * iconsPerRow * 1.4).toInt()
		recreatePackContainer()
		return keyboardLayout
	}

	override fun onEvaluateFullscreenMode(): Boolean {
		// In full-screen mode the inserted content is likely to be hidden by the IME. Hence in this
		// sample we simply disable full-screen mode.
		return false
	}

	override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
		supportedMimes = Utils.getSupportedMimes()
		var allSupported = true
		val mimesToCheck = supportedMimes.keys.toTypedArray()
		for (s in mimesToCheck) {
			val mimeSupported = isCommitContentSupported(info, supportedMimes[s]!!)
			allSupported = allSupported && mimeSupported
			if (!mimeSupported) {
				supportedMimes.remove(s)
			}
		}
		if (!allSupported) {
			Toast.makeText(
				applicationContext,
				"One or more image formats not supported here. Some stickers may not send correctly.",
				Toast.LENGTH_LONG
			).show()
		}
	}

	private fun recreateImageContainer(pack: StickerPack) {
		val imagesDir = File(filesDir, "stickers/$pack")
		imagesDir.mkdirs()
		imageContainer!!.removeAllViewsInLayout()
		var imageContainerColumn = layoutInflater.inflate(
			R.layout.image_container_column,
			imageContainer,
			false
		) as LinearLayout
		val stickers = pack.stickerList
		for (i in stickers.indices) {
			if (i % iconsPerRow == 0) {
				imageContainerColumn = layoutInflater.inflate(
					R.layout.image_container_column,
					imageContainer,
					false
				) as LinearLayout
			}
			val imageCard = layoutInflater.inflate(
				R.layout.sticker_card,
				imageContainerColumn,
				false
			) as CardView
			val imgButton = imageCard.findViewById<ImageButton>(R.id.ib3)
			imgButton.layoutParams.height = iconSize
			imgButton.layoutParams.width = iconSize
			setStickerButtonImage(stickers[i], imgButton)
			imgButton.tag = stickers[i]
			imgButton.setOnClickListener { view: View ->
				val file = view.tag as File
				val stickerType = supportedMimes[Utils.getFileExtension(file.name)]
				if (stickerType == null) {
					doFallbackCommitContent(file)
					return@setOnClickListener
				}
				doCommitContent(file.name, stickerType, file)
			}
			imageContainerColumn.addView(imageCard)
			if (i % iconsPerRow == 0) {
				imageContainer!!.addView(imageContainerColumn)
			}
		}
	}

	private fun recreatePackContainer() {
		packContainer!!.removeAllViewsInLayout()
		// Back button
		if (sharedPreferences.getBoolean("showBackButton", false)) {
			addBackButtonToContainer()
		}
		// Packs
		val sortedPackNames = loadedPacks.keys.toTypedArray()
		Arrays.sort(sortedPackNames)
		for (sortedPackName in sortedPackNames) {
			addPackToContainer(loadedPacks[sortedPackName]!!)
		}
		if (sortedPackNames.isNotEmpty()) {
			recreateImageContainer(loadedPacks[sortedPackNames[0]]!!)
		}
	}

	private fun reloadPacks() {
		loadedPacks = HashMap()
		internalDir = File(filesDir, "stickers")
		val packs = internalDir.listFiles { obj: File -> obj.isDirectory }
		if (packs != null) {
			for (file in packs) {
				val pack = StickerPack(file)
				if (pack.stickerList.isNotEmpty()) {
					loadedPacks[file.name] = pack
				}
			}
		}
		val baseStickers = internalDir.listFiles { obj: File -> obj.isFile }
		if (baseStickers != null && baseStickers.isNotEmpty()) {
			loadedPacks[""] = StickerPack(internalDir)
		}
	}

	private fun validatePackageName(editorInfo: EditorInfo?): Boolean {
		if (editorInfo == null) {
			return false
		}
		val packageName = editorInfo.packageName
		return packageName != null
	}

	companion object {
		// Constants
		private const val AUTHORITY = "com.fredhappyface.whoosticker.inputcontent"
	}
}