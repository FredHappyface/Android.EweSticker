package com.fredhappyface.ewesticker

import android.content.ClipDescription
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build.VERSION.SDK_INT
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.gridlayout.widget.GridLayout
import androidx.preference.PreferenceManager
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
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
 * ImageKeyboard class inherits from the InputMethodService class - provides the keyboard functionality
 */
class ImageKeyboard : InputMethodService() {

	// onCreate
	//    Constants
	private lateinit var mInternalDir: File
	private var mTotalIconPadding = 0

	//    Shared Preferences
	private lateinit var mSharedPreferences: SharedPreferences
	private var mVertical = false
	private var mIconsPerX = 0
	private var mIconSize = 0

	//    Load Packs
	private lateinit var mLoadedPacks: HashMap<String, StickerPack>
	private var mActivePack = ""

	//    Caches
	private var mCompatCache = Cache()
	private var mRecentCache = Cache()

	// onStartInput
	private lateinit var mSupportedMimes: List<String>

	// onCreateInputView
	private lateinit var mKeyboardRoot: ViewGroup
	private lateinit var mPacksList: ViewGroup
	private lateinit var mPackContent: ViewGroup
	private var mKeyboardHeight = 0
	private var mFullIconSize = 0

	// switchPackLayout: cache for image container
	private var imageContainerCache = HashMap<Int, FrameLayout>()

	/**
	 * When the activity is created...
	 * - ensure coil can decode (and display) animated images
	 * - set the internal sticker dir, icon-padding, icon-size, icons-per-col, caches and loaded-packs
	 */
	override fun onCreate() {
		super.onCreate()
		val imageLoader = ImageLoader.Builder(baseContext)
			.componentRegistry {
				if (SDK_INT >= 28) {
					add(ImageDecoderDecoder(baseContext))
				} else {
					add(GifDecoder())
				}
			}
			.build()
		Coil.setImageLoader(imageLoader)
		//    Constants
		val scale = applicationContext.resources.displayMetrics.density
		mInternalDir = File(filesDir, "stickers")
		mTotalIconPadding =
			(resources.getDimension(R.dimen.sticker_padding) * 2 * (mIconsPerX + 1)).toInt()
		//    Shared Preferences
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		mVertical = mSharedPreferences.getBoolean("vertical", false)
		mIconsPerX = mSharedPreferences.getInt("iconsPerX", 3)
		mIconSize = (if (mVertical) {
			(resources.displayMetrics.widthPixels - mTotalIconPadding) / mIconsPerX
		} else {
			(mSharedPreferences.getInt("iconSize", 80) * scale)
		}).toInt()
		//    Load Packs
		mLoadedPacks = HashMap()
		val packs =
			mInternalDir.listFiles { obj: File -> obj.isDirectory && !obj.absolutePath.contains("__compatSticker__") }
				?: arrayOf()
		for (file in packs) {
			val pack = StickerPack(file)
			if (pack.stickerList.isNotEmpty()) {
				mLoadedPacks[file.name] = pack
			}
		}
		mActivePack = mSharedPreferences.getString("activePack", "").toString()
		//    Caches
		mSharedPreferences.getString("recentCache", "")?.let { mRecentCache.fromSharedPref(it) }
		mSharedPreferences.getString("compatCache", "")?.let { mCompatCache.fromSharedPref(it) }
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
		val keyboardLayout =
			View.inflate(applicationContext, R.layout.keyboard_layout, null)
		mKeyboardRoot = keyboardLayout.findViewById(R.id.keyboardRoot)
		mPacksList = keyboardLayout.findViewById(R.id.packsList)
		mPackContent = keyboardLayout.findViewById(R.id.packContent)
		mKeyboardHeight = if (mVertical) {
			800
		} else {
			mIconSize * mIconsPerX + mTotalIconPadding
		}
		mPackContent.layoutParams?.height = mKeyboardHeight
		mFullIconSize = (min(resources.displayMetrics.widthPixels, mKeyboardHeight) * 0.8).toInt()
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
		mSupportedMimes = Utils.getSupportedMimes().filter { isCommitContentSupported(info, it) }
	}

	/**
	 * When leaving some input field update the caches
	 *
	 */
	override fun onFinishInput() {
		val editor = mSharedPreferences.edit()
		editor.putString("recentCache", mRecentCache.toSharedPref())
		editor.putString("compatCache", mCompatCache.toSharedPref())
		editor.putString("activePack", mActivePack)
		editor.apply()
		super.onFinishInput()
	}

	/**
	 * In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you,
	 * Signal) create a temporary png and send that. In the event that png is not supported,
	 * alert the user.
	 *
	 * @param file: File
	 */
	private suspend fun doFallbackCommitContent(file: File) {
		// PNG might not be supported
		if ("image/png" !in mSupportedMimes) {
			Toast.makeText(
				applicationContext,
				file.extension + " not supported here.", Toast.LENGTH_SHORT
			).show()
			return
		}
		// Create a new compatSticker and convert the sticker to png
		val compatStickerName = file.hashCode().toString()
		val compatSticker = File(mInternalDir, "__compatSticker__/$compatStickerName.png")
		if (!compatSticker.exists()) {
			// If the sticker doesn't exist then create
			compatSticker.parentFile?.mkdirs()
			try {
				val request = ImageRequest.Builder(baseContext).data(file).target { result ->
					val bitmap = result.toBitmap()
					bitmap.compress(Bitmap.CompressFormat.PNG, 90, FileOutputStream(compatSticker))
				}.build()
				imageLoader.execute(request)
			} catch (ignore: IOException) {
			}
		}
		// Send the compatSticker!
		doCommitContent("image/png", compatSticker)
		// Remove old stickers
		val remSticker = mCompatCache.add(compatStickerName)
		remSticker?.let { File(mInternalDir, "__compatSticker__/$remSticker.png").delete() }
	}

	/**
	 * Send a sticker file to a InputConnectionCompat
	 *
	 * @param mimeType:    String
	 * @param file:        File
	 */
	private fun doCommitContent(mimeType: String, file: File) {
		// ContentUri, ClipDescription, linkUri
		val inputContentInfoCompat = InputContentInfoCompat(
			FileProvider.getUriForFile(this, "com.fredhappyface.ewesticker.inputcontent", file),
			ClipDescription(file.name, arrayOf(mimeType)),
			null
		)
		// InputConnection, EditorInfo, InputContentInfoCompat, int flags, null opts
		InputConnectionCompat.commitContent(
			currentInputConnection, currentInputEditorInfo, inputContentInfoCompat,
			InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
		)
	}

	/**
	 * Check if the sticker is supported by the receiver
	 *
	 * @param editorInfo: EditorInfo - the editor/ receiver
	 * @param mimeType:   String - the image mimetype
	 * @return boolean - is the mimetype supported?
	 */
	private fun isCommitContentSupported(editorInfo: EditorInfo?, mimeType: String?): Boolean {
		editorInfo?.packageName ?: return false
		mimeType ?: return false
		currentInputConnection ?: return false
		for (supportedMimeType in EditorInfoCompat.getContentMimeTypes(editorInfo)) {
			if (ClipDescription.compareMimeTypes(mimeType, supportedMimeType)) {
				return true
			}
		}
		return false
	}

	/**
	 * Swap the pack layout every time a pack is selected. If already cached use that
	 * otherwise create the pack layout
	 *
	 * @param pack StickerPack
	 */
	private fun switchPackLayout(pack: StickerPack) {
		// Check the cache
		mActivePack = pack.name
		val stickers = pack.stickerList
		val imageContainerHash = stickers.hashCode()
		lateinit var packLayout: FrameLayout
		if (imageContainerHash in imageContainerCache.keys) {
			packLayout = (imageContainerCache[imageContainerHash] ?: return)
		} else {
			packLayout = createPackLayout(stickers)
			imageContainerCache[imageContainerHash] = packLayout
		}
		// Swap the image container
		mPackContent.removeAllViewsInLayout()
		packLayout.parent ?: mPackContent.addView(packLayout)
	}

	/**
	 * Create the initial pack layout (the pack container and the grid)
	 *
	 * @return Pair<FrameLayout, GridLayout> packContainer to pack
	 */
	private fun createPartialPackLayout(): Pair<FrameLayout, GridLayout> {
		if (mVertical) {
			val packContainer = layoutInflater.inflate(
				R.layout.pack_vertical, mPackContent, false
			) as FrameLayout
			val pack = packContainer.findViewById<GridLayout>(R.id.pack)
			pack.columnCount = mIconsPerX
			return packContainer to pack
		}
		val packContainer = layoutInflater.inflate(
			R.layout.pack_horizontal,
			mPackContent,
			false
		) as FrameLayout
		val pack = packContainer.findViewById<GridLayout>(R.id.pack)
		pack.rowCount = mIconsPerX
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
			val imageCard = layoutInflater.inflate(
				R.layout.sticker_card,
				pack,
				false
			) as FrameLayout
			val imgButton = imageCard.findViewById<ImageButton>(R.id.stickerButton)
			imgButton.layoutParams.height = mIconSize
			imgButton.layoutParams.width = mIconSize
			imgButton.load(sticker)
			imgButton.tag = sticker
			imgButton.setOnClickListener {
				val file = it.tag as File
				mRecentCache.add(file.absolutePath)
				val stickerType = Utils.getMimeType(file)
				if (stickerType == null || stickerType !in mSupportedMimes) {
					CoroutineScope(Dispatchers.Main).launch {
						doFallbackCommitContent(file)
					}
					return@setOnClickListener
				}
				doCommitContent(stickerType, file)
			}
			imgButton.setOnLongClickListener { view: View ->
				val file = view.tag as File
				val fullSticker = layoutInflater.inflate(
					R.layout.sticker_preview,
					mKeyboardRoot,
					false
				) as RelativeLayout
				val fSticker = fullSticker.findViewById<ImageButton>(R.id.stickerButton)
				// Set dimens + load image
				fullSticker.layoutParams.height =
					mKeyboardHeight + (resources.getDimension(R.dimen.pack_dimens) + resources.getDimension(
						R.dimen.pack_padding_vertical
					) * 2).toInt()
				fSticker.layoutParams.height = mFullIconSize
				fSticker.layoutParams.width = mFullIconSize
				fSticker.load(file)
				// Tap to exit popup
				fullSticker.setOnClickListener { mKeyboardRoot.removeView(it) }
				fSticker.setOnClickListener { mKeyboardRoot.removeView(fullSticker) }
				mKeyboardRoot.addView(fullSticker)
				return@setOnLongClickListener true
			}
			pack.addView(imageCard)
		}
		return packContainer
	}

	private fun addPackButton(icon: Drawable? = null): ImageButton {
		val packCard = layoutInflater.inflate(R.layout.pack_card, mPacksList, false)
		val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
		packButton.setImageDrawable(icon)
		mPacksList.addView(packCard)
		return packButton
	}

	/**
	 * Create the pack icons (image buttons) that when tapped switch the pack (switchPackLayout)
	 *
	 */
	private fun createPackIcons() {
		mPacksList.removeAllViewsInLayout()
		// Back button
		if (mSharedPreferences.getBoolean("showBackButton", false)) {
			val backButton = addPackButton(
				ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_left, null)
			)
			backButton.setOnClickListener {
				if (SDK_INT >= 28) {
					this.switchToPreviousInputMethod()
				} else {
					(applicationContext.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
				}
			}
		}
		// Recent
		val recentButton =
			addPackButton(ResourcesCompat.getDrawable(resources, R.drawable.ic_clock, null))
		recentButton.setOnClickListener {
			mPackContent.removeAllViewsInLayout()
			mPackContent.addView(createPackLayout(mRecentCache.toFiles().reversedArray()))
		}
		// Packs
		val sortedPackNames = mLoadedPacks.keys.toTypedArray()
		Arrays.sort(sortedPackNames)
		for (sortedPackName in sortedPackNames) {
			val pack = mLoadedPacks[sortedPackName] ?: return
			val packButton = addPackButton()
			packButton.load(pack.thumbSticker)
			packButton.tag = pack
			packButton.setOnClickListener { view: View? ->
				switchPackLayout(view?.tag as StickerPack)
			}
		}
		if (sortedPackNames.isNotEmpty()) {
			if (mActivePack in sortedPackNames) {
				switchPackLayout((mLoadedPacks[mActivePack] ?: return))
			} else {
				switchPackLayout((mLoadedPacks[sortedPackNames[0]] ?: return))
			}
		}
	}
}
