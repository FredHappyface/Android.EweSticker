package com.fredhappyface.ewesticker

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
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap

class ImageKeyboard : InputMethodService() {
	// Attributes
	private lateinit var supportedMimes: MutableMap<String, String>
	private var loadedPacks = HashMap<String, StickerPack>()
	private lateinit var contextView: View
	private lateinit var imageContainer: LinearLayout
	private lateinit var packContainer: LinearLayout
	private lateinit var internalDir: File
	private var scale = 0f

	// SharedPref
	private lateinit var sharedPreferences: SharedPreferences
	private var iconsPerColumn = 0
	private var iconSize = 0
	private var disableAnimations = false

	// Cache for recent + compat stickers
	private var compatCache = Cache()
	private var recentCache = Cache()

	// Cache for image container
	private var imageContainerCache = HashMap<Int, LinearLayout>()

	/**
	 * Adds a back button as a PackCard to keyboard that shows the InputMethodPicker
	 */
	private fun addBackButtonToContainer() {
		val packCard = layoutInflater.inflate(R.layout.pack_card, packContainer, false)
		val backButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
		val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_left, null)
		backButton.setImageDrawable(icon)
		backButton.setOnClickListener {
			val inputMethodManager = applicationContext
				.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
			inputMethodManager.showInputMethodPicker()
		}
		packContainer.addView(packCard)
	}

	/**
	 * Adds a recent button as a PackCard to keyboard that shows the InputMethodPicker
	 */
	private fun addRecentButtonToContainer() {
		val packCard = layoutInflater.inflate(R.layout.pack_card, packContainer, false)
		val recentButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
		val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_clock, null)
		recentButton.setImageDrawable(icon)
		recentButton.setOnClickListener {
			imageContainer.removeAllViewsInLayout()
			imageContainer.addView(createImageContainer(recentCache.toFiles()))
		}
		packContainer.addView(packCard)
	}

	/**
	 * Adds a pack card to the keyboard from a StickerPack
	 *
	 * @param pack: StickerPack - the sticker pack to add
	 */
	private fun addPackToContainer(pack: StickerPack) {
		val packCard = layoutInflater.inflate(R.layout.pack_card, packContainer, false)
		val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
		setStickerButtonImage(pack.thumbSticker, packButton)
		packButton.tag = pack
		packButton.setOnClickListener { view: View ->
			switchImageContainer((view.tag as StickerPack).stickerList)
		}
		packContainer.addView(packCard)
	}

	/**
	 * In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you, Signal)
	 * Create a temporary png and send that. In the event that png is not supported, create a snackbar as before
	 *
	 * @param file: File
	 */
	private fun doFallbackCommitContent(file: File) {
		// PNG might not be supported
		if (supportedMimes[".png"] == null) {
			Snackbar.make(
				contextView, Utils.getFileExtension(file.name) +
						" not supported here.", Snackbar.LENGTH_SHORT
			).show()
			return
		}
		// Create a new compatSticker and convert the sticker to png
		val compatStickerName = file.hashCode().toString()
		val compatSticker = File(internalDir, "__compatSticker__/$compatStickerName.png")
		if (!compatSticker.exists()) {
			// If the sticker doesn't exist then create
			compatSticker.parentFile?.mkdirs()
			try {
				ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
					.compress(Bitmap.CompressFormat.PNG, 90, FileOutputStream(compatSticker))
			} catch (ignore: IOException) {
			}
		}
		// Send the compatSticker!
		doCommitContent("description", "image/png", compatSticker)
		// Remove old stickers
		val remSticker = compatCache.add(compatStickerName)
		if (remSticker != null) {
			File(internalDir, "__compatSticker__/$remSticker.png")
		}
	}

	/**
	 * Send a sticker file to a InputConnectionCompat
	 *
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
		// Create drawable from file
		var drawable: Drawable? = null
		try {
			drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(sticker))
		} catch (ignore: IOException) {
		}
		// Disable animations?
		if (!disableAnimations && drawable is AnimatedImageDrawable) {
			drawable.start()
		}
		// Apply
		btn.setImageDrawable(drawable)
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

	/**
	 * When the activity is crated, grab the number of icons per column and the configured icon size
	 * before reloading the packs
	 *
	 */
	override fun onCreate() {
		super.onCreate()
		scale = applicationContext.resources.displayMetrics.density
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		iconsPerColumn = sharedPreferences.getInt("iconsPerColumn", 3)
		iconSize = sharedPreferences.getInt("iconSize", 80)
		disableAnimations = sharedPreferences.getBoolean(
			"disableAnimations",
			false
		)
		recentCache.fromSharedPref(sharedPreferences.getString("recentCache", "")!!)
		compatCache.fromSharedPref(sharedPreferences.getString("compatCache", "")!!)
		internalDir = File(filesDir, "stickers")
		reloadPacks()
	}

	/**
	 * Called when the keyboard is first drawn
	 *
	 * @return
	 */
	override fun onCreateInputView(): View {
		val keyboardLayout =
			View.inflate(applicationContext, R.layout.keyboard_layout, null)
		contextView = keyboardLayout.findViewById(R.id.keyboardRoot)
		packContainer = keyboardLayout.findViewById(R.id.packContainer)
		imageContainer = keyboardLayout.findViewById(R.id.imageContainer)
		imageContainer.layoutParams?.height =
			(scale * (iconSize * iconsPerColumn) + resources.getDimension(R.dimen.sticker_padding) * 2 * (iconsPerColumn + 1)).toInt()
		recreatePackContainer()
		return keyboardLayout
	}

	/**
	 * In full-screen mode the inserted content is likely to be hidden by the IME. Hence in this
	 * sample we simply disable full-screen mode.
	 *
	 * @return
	 */
	override fun onEvaluateFullscreenMode(): Boolean {
		return false
	}

	/**
	 * When entering some input field (or InputView)
	 *
	 * @param info
	 * @param restarting
	 */
	override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
		supportedMimes = Utils.getSupportedMimes()
		val mimesToCheck = supportedMimes.keys.toTypedArray()
		for (s in mimesToCheck) {
			val mimeSupported = isCommitContentSupported(info, supportedMimes[s]!!)
			if (!mimeSupported) {
				supportedMimes.remove(s)
			}
		}
	}

	/**
	 * Swap the image container every time a new pack is selected. If already cached use that otherwise create
	 *
	 * @param stickers
	 */
	private fun switchImageContainer(stickers: Array<File>) {
		// Check the cache
		val imageContainerHash = stickers.hashCode()
		lateinit var imageContainerLayout: LinearLayout
		if (imageContainerHash !in imageContainerCache.keys) {
			imageContainerLayout = createImageContainer(stickers)
			imageContainerCache[imageContainerHash] = createImageContainer(stickers)
		} else {
			imageContainerLayout = imageContainerCache[imageContainerHash]!!
		}
		// Swap the image container
		imageContainer.removeAllViews()
		imageContainer.addView(imageContainerLayout)
	}

	/**
	 * Recreate the image container every time a new pack is selected
	 *
	 * @param stickers
	 */
	private fun createImageContainer(stickers: Array<File>): LinearLayout {
		val tempImageContainer =
			View.inflate(applicationContext, R.layout.image_container, null) as LinearLayout
		lateinit var imageContainerColumn: LinearLayout
		for (i in stickers.indices) {
			// Add a new column
			if (i % iconsPerColumn == 0) {
				imageContainerColumn = layoutInflater.inflate(
					R.layout.image_container_column,
					tempImageContainer,
					false
				) as LinearLayout
				tempImageContainer.addView(imageContainerColumn)
			}
			val imageCard = layoutInflater.inflate(
				R.layout.sticker_card,
				imageContainerColumn,
				false
			) as CardView
			val imgButton = imageCard.findViewById<ImageButton>(R.id.stickerButton)
			imgButton.layoutParams.height = (iconSize * scale).toInt()
			imgButton.layoutParams.width = (iconSize * scale).toInt()
			setStickerButtonImage(stickers[i], imgButton)
			imgButton.tag = stickers[i]
			imgButton.setOnClickListener { view: View ->
				val file = view.tag as File
				recentCache.add(file.absolutePath)
				val stickerType = supportedMimes[Utils.getFileExtension(file.name)]
				if (stickerType == null) {
					// Sticker is unsupported by input
					doFallbackCommitContent(file)
					return@setOnClickListener
				}
				doCommitContent(file.name, stickerType, file)
			}
			imageContainerColumn.addView(imageCard)
		}
		return tempImageContainer
	}

	/**
	 * Process of creating the pack icons
	 *
	 */
	private fun recreatePackContainer() {
		packContainer.removeAllViewsInLayout()
		// Back button
		if (sharedPreferences.getBoolean("showBackButton", false)) {
			addBackButtonToContainer()
		}
		// Recent
		addRecentButtonToContainer()
		// Packs
		val sortedPackNames = loadedPacks.keys.toTypedArray()
		Arrays.sort(sortedPackNames)
		for (sortedPackName in sortedPackNames) {
			addPackToContainer(loadedPacks[sortedPackName]!!)
		}
		if (sortedPackNames.isNotEmpty()) {
			switchImageContainer(loadedPacks[sortedPackNames[0]]!!.stickerList)
		}
	}

	/**
	 * Clear the loadedPacks attribute and repopulate based on the directory tree of stickers
	 *
	 */
	private fun reloadPacks() {
		loadedPacks = HashMap()
		val packs =
			internalDir.listFiles { obj: File -> obj.isDirectory && !obj.absolutePath.contains("__compatSticker__") }
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

	override fun onFinishInput() {
		// Lets save stuff here
		val editor = sharedPreferences.edit()
		editor.putString("recentCache", recentCache.toSharedPref())
		editor.putString("compatCache", compatCache.toSharedPref())
		editor.apply()
		// Call super
		super.onFinishInput()
	}

	companion object {
		// Constants
		private const val AUTHORITY = "com.fredhappyface.ewesticker.inputcontent"
	}
}
