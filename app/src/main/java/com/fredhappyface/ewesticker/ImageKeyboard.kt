package com.fredhappyface.ewesticker

import android.content.ClipDescription
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.gridlayout.widget.GridLayout
import androidx.preference.PreferenceManager
import coil.Coil
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.load
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap


class ImageKeyboard : InputMethodService() {
	// onCreate
	//   constants
	private lateinit var mInternalDir: File
	private var mIconPadding = 0f

	//   shared pref
	private lateinit var mSharedPreferences: SharedPreferences
	private var mVertical = false
	private var mIconsPerColumn = 0
	private var mIconSize = 0
	private var mCompatCache = Cache()
	private var mRecentCache = Cache()

	// Attributes
	private lateinit var mLoadedPacks: HashMap<String, StickerPack>
	private lateinit var mSupportedMimes: MutableMap<String, String>

	//   keyboard root view, pack content view, pack list view
	private lateinit var mKeyboardRoot: View
	private lateinit var mPackContent: ViewGroup
	private lateinit var mPacksList: ViewGroup

	//   cache for image container
	private var imageContainerCache = HashMap<Int, FrameLayout>()

	/**
	 * When the activity is crated, grab the number of icons per column and the configured icon size
	 * before reloading the packs
	 *
	 */
	override fun onCreate() {
		super.onCreate()
		val imageLoader = ImageLoader.Builder(baseContext)
			.componentRegistry {
				add(ImageDecoderDecoder(baseContext))
			}
			.build()
		Coil.setImageLoader(imageLoader)
		// Constants
		val scale = applicationContext.resources.displayMetrics.density
		mInternalDir = File(filesDir, "stickers")
		mIconPadding = (resources.getDimension(R.dimen.sticker_padding) * 2)
		// Shared pref
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		mVertical = mSharedPreferences.getBoolean("vertical", false)
		mRecentCache.fromSharedPref(mSharedPreferences.getString("recentCache", "")!!)
		mCompatCache.fromSharedPref(mSharedPreferences.getString("compatCache", "")!!)
		mIconsPerColumn = if (mVertical) {
			4
		} else {
			mSharedPreferences.getInt("iconsPerColumn", 3)
		}
		mIconSize = (if (mVertical) {
			(resources.displayMetrics.widthPixels - mIconPadding * scale) / 4
		} else {
			(mSharedPreferences.getInt("iconSize", 80) * scale)
		}).toInt()

		// Clear the loadedPacks attribute and repopulate based on the directory tree of stickers
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
		val baseStickers = mInternalDir.listFiles { obj: File -> obj.isFile }
		if (baseStickers != null && baseStickers.isNotEmpty()) {
			mLoadedPacks[""] = StickerPack(mInternalDir)
		}
	}

	/**
	 * Called when the keyboard is first drawn
	 *
	 * @return
	 */
	override fun onCreateInputView(): View {
		val keyboardLayout =
			View.inflate(applicationContext, R.layout.keyboard_layout, null)
		mKeyboardRoot = keyboardLayout.findViewById(R.id.keyboardRoot)
		mPacksList = keyboardLayout.findViewById(R.id.packsList)
		mPackContent = keyboardLayout.findViewById(R.id.packContent)
		mPackContent.layoutParams?.height = if (mVertical) {
			800
		} else {
			((mIconSize + mIconPadding) * mIconsPerColumn).toInt()
		}
		recreatePackLayout()
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
		mSupportedMimes = Utils.getSupportedMimes()
		val mimesToCheck = mSupportedMimes.keys.toTypedArray()
		for (s in mimesToCheck) {
			val mimeSupported = isCommitContentSupported(info, mSupportedMimes[s]!!)
			if (!mimeSupported) {
				mSupportedMimes.remove(s)
			}
		}
	}

	override fun onFinishInput() {
		// Lets save stuff here
		val editor = mSharedPreferences.edit()
		editor.putString("recentCache", mRecentCache.toSharedPref())
		editor.putString("compatCache", mCompatCache.toSharedPref())
		editor.apply()
		// Call super
		super.onFinishInput()
	}


	/**
	 * In the event that a mimetype is unsupported by a InputConnectionCompat (looking at you,
	 * Signal) create a temporary png and send that. In the event that png is not supported,
	 * create a snack-bar as before.
	 *
	 * @param file: File
	 */
	private fun doFallbackCommitContent(file: File) {
		// PNG might not be supported
		if (mSupportedMimes[".png"] == null) {
			Toast.makeText(
				applicationContext,
				Utils.getFileExtension(file.name) +
						" not supported here.", Toast.LENGTH_SHORT
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
				ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
					.compress(Bitmap.CompressFormat.PNG, 90, FileOutputStream(compatSticker))
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
	private fun doCommitContent(
		mimeType: String,
		file: File
	) {
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
	private fun isCommitContentSupported(
		editorInfo: EditorInfo?, mimeType: String
	): Boolean {
		editorInfo?.packageName ?: return false
		currentInputConnection ?: return false
		for (supportedMimeType in EditorInfoCompat.getContentMimeTypes(editorInfo)) {
			if (ClipDescription.compareMimeTypes(mimeType, supportedMimeType)) {
				return true
			}
		}
		return false
	}


	/**
	 * Swap the image container every time a new pack is selected. If already cached use that
	 * otherwise create
	 *
	 * @param stickers
	 */
	private fun switchImageContainer(stickers: Array<File>) {
		// Check the cache
		val imageContainerHash = stickers.hashCode()
		lateinit var imageContainerLayout: FrameLayout
		if (imageContainerHash in imageContainerCache.keys) {
			imageContainerLayout = imageContainerCache[imageContainerHash]!!
		} else {
			imageContainerLayout = createPackLayout(stickers)
			imageContainerCache[imageContainerHash] = imageContainerLayout
		}
		// Swap the image container
		mPackContent.removeAllViewsInLayout()
		if (imageContainerLayout.parent != null) {
			Log.e(
				"Going to throw IllegalStateException", imageContainerLayout.parent.toString()
			)
		}
		mPackContent.addView(imageContainerLayout)
	}

	private fun createPartialPackLayout(): Pair<FrameLayout, GridLayout> {
		if (mVertical) {
			val packContainer = layoutInflater.inflate(
				R.layout.pack_vertical, mPackContent, false
			) as FrameLayout
			val pack = packContainer.findViewById<GridLayout>(R.id.pack)
			pack.columnCount = mIconsPerColumn
			return packContainer to pack
		}
		val packContainer = layoutInflater.inflate(
			R.layout.pack_horizontal,
			mPackContent,
			false
		) as FrameLayout
		val pack = packContainer.findViewById<GridLayout>(R.id.pack)
		pack.rowCount = mIconsPerColumn
		return packContainer to pack
	}

	/**
	 * Recreate the pack layout
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
			imgButton.setOnClickListener { view: View ->
				val file = view.tag as File
				mRecentCache.add(file.absolutePath)
				val stickerType = mSupportedMimes[Utils.getFileExtension(file.name)]
				if (stickerType == null) {
					doFallbackCommitContent(file)
					return@setOnClickListener
				}
				doCommitContent(stickerType, file)
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
	 * Process of creating the pack icons
	 *
	 */
	private fun recreatePackLayout() {
		mPacksList.removeAllViewsInLayout()
		// Back button
		if (mSharedPreferences.getBoolean("showBackButton", false)) {
			val backButton = addPackButton(
				ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_left, null)
			)
			backButton.setOnClickListener {
				val inputMethodManager = applicationContext
					.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
				inputMethodManager.showInputMethodPicker()
			}
		}
		// Recent
		val recentButton =
			addPackButton(ResourcesCompat.getDrawable(resources, R.drawable.ic_clock, null))
		recentButton.setOnClickListener {
			mPackContent.removeAllViewsInLayout()
			mPackContent.addView(createPackLayout(mRecentCache.toFiles()))
		}

		// Packs
		val sortedPackNames = mLoadedPacks.keys.toTypedArray()
		Arrays.sort(sortedPackNames)
		for (sortedPackName in sortedPackNames) {
			val pack = mLoadedPacks[sortedPackName]!!
			val packButton = addPackButton()
			packButton.load(pack.thumbSticker)
			packButton.tag = pack
			packButton.setOnClickListener { view: View? ->
				switchImageContainer((view?.tag as StickerPack).stickerList)
			}
		}
		if (sortedPackNames.isNotEmpty()) {
			switchImageContainer(mLoadedPacks[sortedPackNames[0]]!!.stickerList)
		}
	}
}
