package com.fredhappyface.ewesticker

import android.content.ClipDescription
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.inputmethodservice.InputMethodService
import android.os.Build.VERSION.SDK_INT
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.iterator
import androidx.gridlayout.widget.GridLayout
import androidx.preference.PreferenceManager
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.fetch.VideoFrameFileFetcher
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.min

/**
 * ImageKeyboard class inherits from the InputMethodService class - provides the keyboard
 * functionality
 */
class ImageKeyboard : InputMethodService() {
	// onCreate
	//  Shared Preferences
	private lateinit var sharedPreferences: SharedPreferences
	private var vertical = false
	private var iconsPerX = 0
	private var iconSize = 0

	//  Constants
	private lateinit var internalDir: File
	private var totalIconPadding = 0
	private lateinit var toaster: Toaster

	//  Load Packs
	private lateinit var loadedPacks: HashMap<String, StickerPack>
	private var activePack = ""

	//  Caches
	private var compatCache = Cache()
	private var recentCache = Cache()

	// onStartInput
	private lateinit var supportedMimes: List<String>

	// onCreateInputView
	private lateinit var keyboardRoot: ViewGroup
	private lateinit var packsList: ViewGroup
	private lateinit var packContent: ViewGroup
	private var keyboardHeight = 0
	private var fullIconSize = 0

	// switchPackLayout: cache for image container
	private var imageContainerCache = HashMap<Int, FrameLayout>()

	/**
	 * When the activity is created...
	 * - ensure coil can decode (and display) animated images
	 * - set the internal sticker dir, icon-padding, icon-size, icons-per-col, caches and
	 * loaded-packs
	 */
	override fun onCreate() {
		// Misc
		super.onCreate()
		val scale = applicationContext.resources.displayMetrics.density
		// Setup coil
		val imageLoader =
			ImageLoader.Builder(baseContext)
				.componentRegistry {
					if (SDK_INT >= 28) {
						add(ImageDecoderDecoder(baseContext))
					} else {
						add(GifDecoder())
					}
					add(VideoFrameFileFetcher(baseContext))
				}
				.build()
		Coil.setImageLoader(imageLoader)
		//  Shared Preferences
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		this.vertical = this.sharedPreferences.getBoolean("vertical", false)
		this.iconsPerX = this.sharedPreferences.getInt("iconsPerX", 3)
		this.totalIconPadding =
			(resources.getDimension(R.dimen.sticker_padding) * 2 * (this.iconsPerX + 1)).toInt()
		//  Constants
		this.internalDir = File(filesDir, "stickers")
		this.iconSize =
			(if (this.vertical) {
				(resources.displayMetrics.widthPixels - this.totalIconPadding) / this.iconsPerX.toFloat()
			} else {
				(this.sharedPreferences.getInt("iconSize", 80) * scale)
			})
				.toInt()
		this.toaster = Toaster(applicationContext)
		//  Load Packs
		this.loadedPacks = HashMap()
		val packs =
			this.internalDir.listFiles { obj: File ->
				obj.isDirectory && !obj.absolutePath.contains("__compatSticker__")
			}
				?: arrayOf()
		for (file in packs) {
			val pack = StickerPack(file)
			if (pack.stickerList.isNotEmpty()) {
				this.loadedPacks[file.name] = pack
			}
		}
		this.activePack = this.sharedPreferences.getString("activePack", "").toString()
		//  Caches
		this.sharedPreferences.getString("recentCache", "")?.let {
			this.recentCache.fromSharedPref(it)
		}
		this.sharedPreferences.getString("compatCache", "")?.let {
			this.compatCache.fromSharedPref(it)
		}
	}

	/**
	 * When the keyboard is first drawn...
	 * - inflate keyboardLayout
	 * - set the keyboard height
	 * - create pack icons
	 *
	 * @return View keyboardLayout
	 */
	override fun onCreateInputView(): View {
		val keyboardLayout = View.inflate(applicationContext, R.layout.keyboard_layout, null)
		this.keyboardRoot = keyboardLayout.findViewById(R.id.keyboardRoot)
		this.packsList = keyboardLayout.findViewById(R.id.packsList)
		this.packContent = keyboardLayout.findViewById(R.id.packContent)
		this.keyboardHeight =
			if (this.vertical) {
				800
			} else {
				this.iconSize * this.iconsPerX + this.totalIconPadding
			}
		this.packContent.layoutParams?.height = this.keyboardHeight
		this.fullIconSize =
			(min(
				resources.displayMetrics.widthPixels,
				this.keyboardHeight -
						resources.getDimensionPixelOffset(R.dimen.text_size_body)
			) * 0.95)
				.toInt()
		createPackIcons()
		return keyboardLayout
	}

	/**
	 * Disable full-screen mode as content will likely be hidden by the IME.
	 *
	 * @return Boolean false
	 */
	override fun onEvaluateFullscreenMode(): Boolean {
		return false
	}

	/**
	 * When entering some input field update the list of supported-mimes
	 *
	 * @param info
	 * @param restarting
	 */
	override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
		this.supportedMimes =
			Utils.getSupportedMimes().filter { isCommitContentSupported(info, it) }
	}

	/** When leaving some input field update the caches */
	override fun onFinishInput() {
		val editor = this.sharedPreferences.edit()
		editor.putString("recentCache", this.recentCache.toSharedPref())
		editor.putString("compatCache", this.compatCache.toSharedPref())
		editor.putString("activePack", this.activePack)
		editor.apply()
		super.onFinishInput()
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
			toaster.toast(getString(R.string.fallback_040, file.extension))
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
					ImageRequest.Builder(baseContext)
						.data(file)
						.target { result ->
							val bitmap = result.toBitmap()
							bitmap.compress(
								Bitmap.CompressFormat.PNG, 90, FileOutputStream(compatSticker)
							)
						}
						.build()
				imageLoader.execute(request)
			} catch (ignore: IOException) {
				toaster.toast(getString(R.string.fallback_041))
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
				FileProvider.getUriForFile(this, "com.fredhappyface.ewesticker.inputcontent", file),
				ClipDescription(file.name, arrayOf(mimeType)),
				null
			)
		// InputConnection, EditorInfo, InputContentInfoCompat, int flags, null opts
		InputConnectionCompat.commitContent(
			currentInputConnection,
			currentInputEditorInfo,
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
		currentInputConnection ?: return false
		EditorInfoCompat.getContentMimeTypes(editorInfo).forEach {
			if (ClipDescription.compareMimeTypes(mimeType, it)) {
				return true
			}
		}
		return false
	}

	/**
	 * Swap the pack layout every time a pack is selected. If already cached use that otherwise
	 * create the pack layout
	 *
	 * @param packName String
	 */
	private fun switchPackLayout(packName: String) {
		// Set the active pack and do highlighting
		this.activePack = packName
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			if (packButton.tag == packName) {
				(packButton as ImageButton).setColorFilter(getColor(R.color.accent_a))
			} else {
				(packButton as ImageButton).setColorFilter(getColor(R.color.transparent))
			}
		}
		// Deal with recent
		val packLayout: FrameLayout
		if (packName == "__recentSticker__") {
			packLayout = createPackLayout(this.recentCache.toFiles().reversedArray())
		} else {
			// Otherwise
			val stickers = this.loadedPacks[packName]?.stickerList ?: return
			val imageContainerHash = stickers.hashCode()
			if (imageContainerHash in imageContainerCache.keys) {
				packLayout = (imageContainerCache[imageContainerHash] ?: return)
			} else {
				packLayout = createPackLayout(stickers)
				imageContainerCache[imageContainerHash] = packLayout
			}
		}
		// Swap the image container
		this.packContent.removeAllViewsInLayout()
		if (packLayout.parent != null) {
			toaster.toast(getString(R.string.switch_050))
		} else {
			this.packContent.addView(packLayout)
		}
	}

	/**
	 * Create the initial pack layout (the pack container and the grid)
	 *
	 * @return Pair<FrameLayout, GridLayout> packContainer to pack
	 */
	private fun createPartialPackLayout(): Pair<FrameLayout, GridLayout> {
		if (this.vertical) {
			val packContainer =
				layoutInflater.inflate(R.layout.pack_vertical, this.packContent, false) as
						FrameLayout
			val pack = packContainer.findViewById<GridLayout>(R.id.pack)
			pack.columnCount = this.iconsPerX
			return packContainer to pack
		}
		val packContainer =
			layoutInflater.inflate(R.layout.pack_horizontal, this.packContent, false) as FrameLayout
		val pack = packContainer.findViewById<GridLayout>(R.id.pack)
		pack.rowCount = this.iconsPerX
		return packContainer to pack
	}

	/**
	 * Create the pack layout (called by switchPackLayout if the FrameLayout is not cached)
	 *
	 * @param stickers
	 */
	private fun createPackLayout(stickers: Array<File>): FrameLayout {
		val (packContainer, pack) = createPartialPackLayout()
		for (sticker in stickers) {
			val imageCard =
				layoutInflater.inflate(R.layout.sticker_card, pack, false) as FrameLayout
			val imgButton = imageCard.findViewById<ImageButton>(R.id.stickerButton)
			imgButton.layoutParams.height = this.iconSize
			imgButton.layoutParams.width = this.iconSize
			imgButton.load(sticker)
			imgButton.tag = sticker
			imgButton.setOnClickListener {
				val file = it.tag as File
				this.recentCache.add(file.absolutePath)
				val stickerType = Utils.getMimeType(file)
				if (stickerType == null || stickerType !in this.supportedMimes) {
					CoroutineScope(Dispatchers.Main).launch { doFallbackCommitContent(file) }
					return@setOnClickListener
				}
				doCommitContent(stickerType, file)
			}
			imgButton.setOnLongClickListener { view: View ->
				val file = view.tag as File
				val fullStickerLayout =
					layoutInflater.inflate(R.layout.sticker_preview, this.keyboardRoot, false) as
							RelativeLayout
				// Set dimens + load image
				fullStickerLayout.layoutParams.height =
					this.keyboardHeight +
							(resources.getDimension(R.dimen.pack_dimens) +
									resources.getDimension(R.dimen.pack_padding_vertical) * 2)
								.toInt()
				val fSticker = fullStickerLayout.findViewById<ImageButton>(R.id.stickerButton)
				fSticker.layoutParams.height = this.fullIconSize
				fSticker.layoutParams.width = this.fullIconSize
				fSticker.load(file)
				// Tap to exit popup
				fullStickerLayout.setOnClickListener { this.keyboardRoot.removeView(it) }
				fSticker.setOnClickListener { this.keyboardRoot.removeView(fullStickerLayout) }
				this.keyboardRoot.addView(fullStickerLayout)
				return@setOnLongClickListener true
			}
			pack.addView(imageCard)
		}
		return packContainer
	}

	private fun addPackButton(tag: Any): ImageButton {
		val packCard = layoutInflater.inflate(R.layout.pack_card, this.packsList, false)
		val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
		packButton.tag = tag
		packButton.setOnClickListener { switchPackLayout(it?.tag as String) }
		this.packsList.addView(packCard)
		return packButton
	}

	/** Create the pack icons (image buttons) that when tapped switch the pack (switchPackLayout) */
	private fun createPackIcons() {
		this.packsList.removeAllViewsInLayout()
		// Back button
		if (this.sharedPreferences.getBoolean("showBackButton", true)) {
			val backButton = addPackButton("__back__")
			backButton.load(getDrawable(R.drawable.ic_chevron_left))
			backButton.setOnClickListener {
				if (SDK_INT >= 28) {
					this.switchToPreviousInputMethod()
				} else {
					(applicationContext.getSystemService(INPUT_METHOD_SERVICE) as
							InputMethodManager)
						.showInputMethodPicker()
				}
			}
		}
		// Recent
		val recentButton = addPackButton("__recentSticker__")
		recentButton.load(getDrawable(R.drawable.ic_clock))
		recentButton.setOnClickListener { switchPackLayout(it?.tag as String) }
		// Packs
		val sortedPackNames = this.loadedPacks.keys.toTypedArray()
		Arrays.sort(sortedPackNames)
		for (sortedPackName in sortedPackNames) {
			val packButton = addPackButton(sortedPackName)
			packButton.load(this.loadedPacks[sortedPackName]?.thumbSticker)
			packButton.setOnClickListener { switchPackLayout(it?.tag as String) }
		}
		if (sortedPackNames.isNotEmpty()) {
			when (this.activePack) {
				"__recentSticker__" -> switchPackLayout(this.activePack)
				in sortedPackNames -> switchPackLayout(this.activePack)
				else -> switchPackLayout(sortedPackNames[0])
			}
		}
	}
}
