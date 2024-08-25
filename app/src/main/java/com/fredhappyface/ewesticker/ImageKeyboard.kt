package com.fredhappyface.ewesticker

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.iterator
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.imageLoader
import coil.load
import com.fredhappyface.ewesticker.adapter.StickerPackAdapter
import com.fredhappyface.ewesticker.model.StickerPack
import com.fredhappyface.ewesticker.utilities.Cache
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.utilities.StickerSender
import com.fredhappyface.ewesticker.utilities.Toaster
import java.io.File
import kotlin.math.abs
import kotlin.math.min

private const val SWIPE_THRESHOLD = 1
private const val SWIPE_VELOCITY_THRESHOLD = 1

/**
 * ImageKeyboard class inherits from the InputMethodService class - provides the keyboard
 * functionality
 */
class ImageKeyboard : InputMethodService(), StickerClickListener {
	// onCreate
	//  Shared Preferences
	private lateinit var sharedPreferences: SharedPreferences
	private var restoreOnClose = false
	private var vertical = false
	private var scroll = false
	private var vibrate = false
	private var iconsPerX = 0
	private var iconSize = 0
	private var insensitiveSort = false

	//  Constants
	private lateinit var internalDir: File
	private var totalIconPadding = 0
	private lateinit var toaster: Toaster

	//  Load Packs
	private lateinit var loadedPacks: HashMap<String, StickerPack>
	private var allStickers: List<File> = listOf()
	private var activePack = ""

	//  Caches
	private var compatCache = Cache()
	private var recentCache = Cache()

	// onStartInput
	private lateinit var stickerSender: StickerSender

	// onCreateInputView
	private lateinit var keyboardRoot: ViewGroup
	private lateinit var packsList: ViewGroup
	private lateinit var packContent: ViewGroup
	private var keyboardHeight = 0
	private var fullIconSize = 0
	private var qwertyWidth = 0

	private lateinit var gestureDetector: GestureDetector

	/**
	 * When the activity is created...
	 * - ensure coil can decode (and display) animated images
	 * - set the internal sticker dir, icon-padding, icon-size, icons-per-col, caches and
	 * loaded-packs
	 */
	override fun onCreate() {
		// Misc
		super.onCreate()
		val scale = baseContext.resources.displayMetrics.density
		// Setup coil
		val imageLoader =
			ImageLoader.Builder(baseContext)
				.components {
					if (SDK_INT >= 28) {
						add(ImageDecoderDecoder.Factory())
					} else {
						add(GifDecoder.Factory())
					}
					add(VideoFrameDecoder.Factory())
				}
				.build()
		Coil.setImageLoader(imageLoader)
		//  Shared Preferences
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		this.restoreOnClose = this.sharedPreferences.getBoolean("restoreOnClose", false)
		this.vertical = this.sharedPreferences.getBoolean("vertical", false)
		this.scroll = this.sharedPreferences.getBoolean("scroll", false)
		this.vibrate = this.sharedPreferences.getBoolean("vibrate", true)
		this.insensitiveSort = this.sharedPreferences.getBoolean("insensitiveSort", false)

		this.iconsPerX = this.sharedPreferences.getInt("iconsPerX", 3)
		this.totalIconPadding =
			(resources.getDimension(R.dimen.sticker_padding) * 2 * (this.iconsPerX + 1)).toInt()
		//  Constants
		this.internalDir = File(filesDir, "stickers")
		this.iconSize =
			(
				if (this.vertical) {
					(resources.displayMetrics.widthPixels - this.totalIconPadding) / this.iconsPerX.toFloat()
				} else {
					(this.sharedPreferences.getInt("iconSize", 80) * scale)
				}
				).toInt()
		this.toaster = Toaster(baseContext)
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
			this.allStickers += pack.stickerList
		}
		this.activePack = this.sharedPreferences.getString("activePack", "").toString()
		//  Caches
		this.sharedPreferences.getString("recentCache", "")?.let {
			this.recentCache.fromSharedPref(it)
		}
		this.sharedPreferences.getString("compatCache", "")?.let {
			this.compatCache.fromSharedPref(it)
		}
		window.window?.navigationBarColor = getColor(R.color.bg)
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
		val keyboardLayout = View.inflate(baseContext, R.layout.keyboard_layout, null)
		gestureDetector = GestureDetector(baseContext, GestureListener())

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
			(
				min(
					resources.displayMetrics.widthPixels,
					this.keyboardHeight -
						resources.getDimensionPixelOffset(R.dimen.text_size_body) * 2,
				) * 0.95
				)
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
		this.stickerSender = StickerSender(
			this.baseContext,
			this.toaster,
			this.internalDir,
			this.currentInputConnection,
			this.currentInputEditorInfo,
			this.compatCache,
			this.imageLoader,
		)
	}

	/** When leaving some input field update the caches */
	override fun onFinishInput() {
		val editor = this.sharedPreferences.edit()
		editor.putString("recentCache", this.recentCache.toSharedPref())
		editor.putString("compatCache", this.compatCache.toSharedPref())
		editor.putString("activePack", this.activePack)
		editor.apply()
		super.onFinishInput()
		if (restoreOnClose) {
			closeKeyboard()
		}
	}

	/**
	 * Swap the pack layout every time a pack is selected. If already cached use that otherwise
	 * create the pack layout
	 *
	 * @param packName String
	 */
	private fun switchPackLayout(packName: String) {
		this.activePack = packName
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			if (packButton.tag == packName) {
				(packButton as ImageButton).setColorFilter(getColor(R.color.accent_a))
			} else {
				(packButton as ImageButton).setColorFilter(getColor(R.color.transparent))
			}
		}

		val stickers: Array<File>
		if (packName == "__recentSticker__") {
			stickers = this.recentCache.toFiles().reversedArray()
		} else {
			stickers = loadedPacks[packName]?.stickerList ?: return
		}
		val recyclerView = RecyclerView(this)
		val adapter = StickerPackAdapter(
			iconSize,
			stickers,
			this,
			gestureDetector,
			this.vibrate)
		val layoutManager = GridLayoutManager(
			this,
			iconsPerX,
			if (vertical) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL,
			false,
		)
		recyclerView.layoutManager = layoutManager
		recyclerView.adapter = adapter
		packContent.removeAllViewsInLayout()
		packContent.addView(recyclerView)
	}

	/**
	 * Set the current tab to the search page/ view
	 */
	private fun searchView() {
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			if (packButton.tag == "__search__") {
				(packButton as ImageButton).setColorFilter(getColor(R.color.accent_a))
			} else {
				(packButton as ImageButton).setColorFilter(getColor(R.color.transparent))
			}
		}

		qwertyWidth = (resources.displayMetrics.widthPixels / 10.4).toInt()

		val qwertyLayout = layoutInflater.inflate(R.layout.qwerty_layout, packContent, false)
		val searchText = qwertyLayout.findViewById<TextView>(R.id.search_text)
		val searchResults = qwertyLayout.findViewById<LinearLayout>(R.id.search_results)

		val searchResultsHeight =
			packContent.layoutParams.height -
				(
					resources.getDimension(R.dimen.qwerty_row_height) +
					resources.getDimension(R.dimen.qwerty_row_height) * 4
					)

		searchResults.layoutParams.height = searchResultsHeight.toInt()

		fun searchStickers(query: String): List<File> {
			return this.allStickers.filter { it.name.contains(query, ignoreCase = true) }
		}

		fun updateSearchResults(stickers: List<File>) {
			val recyclerView = RecyclerView(baseContext)
			val adapter = StickerPackAdapter(
				(searchResultsHeight * 0.9).toInt(),
				stickers.take(128).toTypedArray(),
				this,
				gestureDetector,
				this.vibrate,
			)
			val layoutManager = GridLayoutManager(
				baseContext,
				1,
				RecyclerView.HORIZONTAL,
				false,
			)
			recyclerView.layoutManager = layoutManager
			recyclerView.adapter = adapter
			searchResults.removeAllViewsInLayout()
			searchResults.addView(recyclerView)
		}

		fun searchAppend(char: String) {
			searchText.append(char)
			val query = searchText.text.toString()
			updateSearchResults(searchStickers(query))
		}

		fun searchBack(char: String) {
			if (searchText.text.isNotEmpty()) {
				val newText = searchText.text.substring(0, searchText.text.length - 1)
				searchText.text = newText
			}
			val query = searchText.text.toString()
			updateSearchResults(searchStickers(query))
		}

		fun searchClear(char: String) {
			searchText.text = ""
			searchResults.removeAllViews()
		}

		fun addKey(
			char: String,
			secondaryChar: String,
			tap: (String) -> Unit = ::searchAppend,
			longTap: (String) -> Unit = ::searchAppend,
		): RelativeLayout {
			val buttonView = layoutInflater.inflate(R.layout.qwerty_key, null, false)
			val button = buttonView.findViewById<RelativeLayout>(R.id.btn)
			val layoutParams =
				LinearLayout.LayoutParams(qwertyWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
			button.layoutParams = layoutParams
			button.layoutParams.width = qwertyWidth
			button.tag = arrayOf(char.lowercase(), secondaryChar)

			val pText = buttonView.findViewById<TextView>(R.id.primaryText)
			pText.text = char
			val sText = buttonView.findViewById<TextView>(R.id.secondaryText)
			sText.text = secondaryChar
			button.setOnClickListener {
				if (this.vibrate && SDK_INT >= Build.VERSION_CODES.O_MR1) {
					it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
				}
				tap((it.tag as Array<String>)[0])
			}
			button.setOnLongClickListener {
				longTap((it.tag as Array<String>)[1])
				return@setOnLongClickListener true
			}
			return button
		}

		fun addRow(row: LinearLayout, chars: List<String>, secondaryChars: List<String>) {
			for ((index, key) in chars.withIndex()) {
				val button = addKey(key, secondaryChars[index])
				row.addView(button)
			}
		}

		val row1 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_1)
		addRow(row1, "QWERTYUIOP".map { it.toString() }, "1234567890".map { it.toString() })
		val row2 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_2)
		addRow(row2, "ASDFGHJKL".map { it.toString() }, "@#£_&-+()".map { it.toString() })
		val row3 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_3)
		addRow(row3, "ZXCVBNM".map { it.toString() }, "*\"':;!?".map { it.toString() })
		val row4 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_4)

		val backspace = addKey("←", "", ::searchBack, ::searchClear)
		backspace.layoutParams.width = qwertyWidth * 2
		row3.addView(backspace)

		val spacebar = addKey(" ", " ")
		spacebar.layoutParams.width = qwertyWidth * 7
		row4.addView(spacebar)

		packContent.removeAllViewsInLayout()
		packContent.addView(qwertyLayout)
	}

	/**
	 * Adds a pack button to the packsList/ tab bar.
	 *
	 * @param tag The pack name associated with the pack button.
	 * @return The ImageButton representing the added pack button.
	 */
	private fun addPackButton(tag: String): ImageButton {
		val packCard = layoutInflater.inflate(R.layout.sticker_card, this.packsList, false)
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
			backButton.load(getDrawable(R.drawable.arrow_back_circle))
			backButton.setOnClickListener {
				closeKeyboard()
			}
		}

		// Search
		if (this.sharedPreferences.getBoolean("showSearchButton", true)) {
			val searchButton = addPackButton("__search__")
			searchButton.load(getDrawable(R.drawable.search_circle))
			searchButton.setOnClickListener {
				searchView()
			}
		}
		// Recent
		val recentPackName = "__recentSticker__"
		val recentButton = addPackButton(recentPackName)
		recentButton.load(getDrawable(R.drawable.time))
		recentButton.setOnClickListener { switchPackLayout(recentPackName) }
		// Packs
		val sortedPackNames = if (this.insensitiveSort) {
			this.loadedPacks.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
		} else {
			this.loadedPacks.keys.sorted()
		}.toTypedArray()




		for (sortedPackName in sortedPackNames) {
			val packButton = addPackButton(sortedPackName)
			packButton.load(this.loadedPacks[sortedPackName]?.thumbSticker)
			packButton.setOnClickListener { switchPackLayout(sortedPackName) }
		}

		val targetPack =
			if (activePack in sortedPackNames + recentPackName) activePack else sortedPackNames.firstOrNull()

		if (sortedPackNames.isNotEmpty()) {
			targetPack?.let { switchPackLayout(it) }
		}
	}

	private fun closeKeyboard() {
		if (SDK_INT >= 28) {
			this.switchToPreviousInputMethod()
		} else {
			(
				baseContext.getSystemService(INPUT_METHOD_SERVICE) as
					InputMethodManager
				).showInputMethodPicker()
		}
	}

	/**
	 * onStickerClicked
	 *
	 * When a sticker is tapped/ clicked. Update the cache and send the sticker
	 *
	 *  @param sticker: File
	 */
	override fun onStickerClicked(sticker: File) {
		this.recentCache.add(sticker.absolutePath)
		this.stickerSender.sendSticker(sticker)
	}

	/**
	 * onStickerLongClicked
	 *
	 * When a sticker is long tapped/ clicked. Attach a new view to see an enlarged version of the sticker
	 *
	 *  @param sticker: File
	 */
	override fun onStickerLongClicked(sticker: File) {
		val fullStickerLayout =
			layoutInflater.inflate(R.layout.sticker_preview, this.keyboardRoot, false) as
				RelativeLayout
		// Set dimens + load image
		fullStickerLayout.layoutParams.height =
			this.keyboardHeight +
				(
					resources.getDimension(R.dimen.pack_dimens) +
						resources.getDimension(R.dimen.sticker_padding) * 4
					).toInt()
		val fSticker = fullStickerLayout.findViewById<ImageButton>(R.id.stickerButton)
		fSticker.layoutParams.height = this.fullIconSize
		fSticker.layoutParams.width = this.fullIconSize
		fSticker.load(sticker)
		val fText = fullStickerLayout.findViewById<TextView>(R.id.stickerInfo)
		val stickerName = trimString(sticker.name)
		val packName = trimString(sticker.parent?.split('/')?.last())
		fText.text = getString(R.string.sticker_pack_info, stickerName, packName)

		// Tap to exit popup
		fullStickerLayout.setOnClickListener { this.keyboardRoot.removeView(it) }
		fSticker.setOnClickListener { this.keyboardRoot.removeView(fullStickerLayout) }
		this.keyboardRoot.addView(fullStickerLayout)
	}

	internal fun switchToPreviousPack() {
		// Get a list of sorted pack names
		val sortedPackNames = loadedPacks.keys.sorted()
		// Find the index of the current active pack
		val currentIndex = sortedPackNames.indexOf(activePack)
		// Calculate the index of the previous pack, considering wrap-around
		val previousIndex = if (currentIndex > 0) currentIndex - 1 else sortedPackNames.size - 1
		val previousPack = sortedPackNames[previousIndex]
		switchPackLayout(previousPack)
	}

	internal fun switchToNextPack() {
		val sortedPackNames = loadedPacks.keys.sorted()
		val currentIndex = sortedPackNames.indexOf(activePack)
		val nextIndex = (currentIndex + 1) % sortedPackNames.size
		val nextPack = sortedPackNames[nextIndex]
		switchPackLayout(nextPack)
	}

	private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
		override fun onDown(e: MotionEvent): Boolean {
			return false
		}

		override fun onScroll(
			e1: MotionEvent?,
			e2: MotionEvent,
			velocityX: Float,
			velocityY: Float,
		): Boolean {
			val diffX = e2.x - (e1?.x ?: 0f)
			val diffY = e2.y - (e1?.y ?: 0f)

			if (
				scroll &&
				abs(if (vertical) diffX else diffY) > SWIPE_THRESHOLD &&
				abs(if (vertical) velocityX else velocityY) > SWIPE_VELOCITY_THRESHOLD
			) {
				if (diffX > 0) {
					// Swipe right
					switchToPreviousPack()
				} else {
					// Swipe left
					switchToNextPack()
				}
				return true
			}

			return false
		}
	}
}

/**
 * trimString
 *
 * for strings longer than 32 chars, trim to 32 chars and add ellipsis ...
 *
 *  @param str: String
 *  @return String
 */
fun trimString(str: String?): String {
	if (str == null) {
		return "null"
	}
	if (str.length > 32) {
		return str.substring(0, 32) + "..."
	}
	return str
}
