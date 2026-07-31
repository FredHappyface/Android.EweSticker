package com.fredhappyface.ewesticker

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.provider.DocumentsContract
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
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
import coil.decode.BitmapFactoryDecoder
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import com.elvishew.xlog.XLog
import com.fredhappyface.ewesticker.adapter.AllPacksAdapter
import com.fredhappyface.ewesticker.adapter.StickerPackAdapter
import com.fredhappyface.ewesticker.model.StickerPack
import com.fredhappyface.ewesticker.utilities.Cache
import com.fredhappyface.ewesticker.utilities.StickerKeySet
import com.fredhappyface.ewesticker.utilities.SourceListing
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.utilities.StickerSender
import com.fredhappyface.ewesticker.utilities.Toaster
import com.fredhappyface.ewesticker.utilities.startLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/** Tag of the synthetic tab holding starred stickers */
private const val FAVOURITES_PACK = "__favourites__"

/** Tag of the synthetic tab holding recently sent stickers */
private const val RECENT_PACK = "__recentSticker__"

/** Ceiling on how much of the source tree is walked when checking a pack is really empty */
private const val MAX_TREE_ENTRIES = 4096

/** Formats the platform's own bitmap decoder can read, so a still frame can be requested */
private val PLATFORM_DECODABLE =
	setOf("webp", "gif", "png", "jpg", "jpeg", "heif", "heic", "bmp")

/** How long a delete stays armed before it forgets it was asked */
private const val DELETE_ARM_MS = 4000L

private const val SWIPE_THRESHOLD = 1
private const val SWIPE_VELOCITY_THRESHOLD = 1

/** Height the vertical grid starts at before the user has ever dragged the handle. */
private const val DEFAULT_VERTICAL_HEIGHT = 800

/** Ceiling for a dragged height, as a fraction of the screen, so the target app keeps a strip. */
private const val MAX_HEIGHT_FRACTION = 0.85f

/**
 * ImageKeyboard class inherits from the InputMethodService class - provides the keyboard
 * functionality
 */
class ImageKeyboard : InputMethodService(), StickerClickListener {
	// onCreate
	//  Shared Preferences
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var backupSharedPreferences: SharedPreferences
	private var restoreOnClose = false
	private var vertical = false
	private var scroll = false
	private var vibrate = false
	private var iconsPerX = 0
	private var iconSize = 0
	private var insensitiveSort = false
	private var isPngFallback = true
	private var continuousScroll = true
	private var generateGifVariants = false
	private var animateGrid = false

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
	private lateinit var favourites: StickerKeySet

	// onStartInput
	private lateinit var stickerSender: StickerSender

	// onCreateInputView
	private lateinit var keyboardRoot: ViewGroup
	private lateinit var packsList: ViewGroup
	private var packsScroller: HorizontalScrollView? = null
	private lateinit var packContent: ViewGroup
	private var keyboardHeight = 0
	private var fullIconSize = 0
	private var qwertyWidth = 0

	private lateinit var resizeHandle: View

	// Drag state. Each MOVE is measured from where the finger went down rather than from the
	// previous frame, so the height cannot drift as rounding errors accumulate over a long drag.
	private var dragStartRawY = 0f
	private var dragStartHeight = 0

	// The all-packs list, plus where each pack's header sits in it, held highest position first so
	// that a scroll can find the pack currently in view by taking the first entry at or above it.
	private var allPacksView: RecyclerView? = null
	private var packHeaderPositions: Map<String, Int> = emptyMap()
	private var packHeadersByPosition: List<Pair<Int, String>> = emptyList()

	private lateinit var gestureDetector: GestureDetector

	/**
	 * The configuration generation this keyboard has applied. MainActivity bumps the stored one
	 * whenever a preference or the sticker library changes, which is how a running keyboard notices
	 * without being restarted by hand.
	 */
	private var appliedVersion = 0
	private var rebuildInputView = false

	/**
	 * When the activity is created...
	 * - ensure coil can decode (and display) animated images
	 * - set the internal sticker dir, icon-padding, icon-size, icons-per-col, caches and
	 * loaded-packs
	 */
	override fun onCreate() {
		// Misc
		super.onCreate()
		startLogger(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:${javaClass.name}")

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
					add(SvgDecoder.Factory())
				}
				// Stated rather than inherited: this process draws a grid of several hundred stickers
				// and the default is a share of an app heap that was never sized with that in mind.
				.memoryCache {
					MemoryCache.Builder(baseContext).maxSizePercent(0.25).build()
				}
				.build()
		Coil.setImageLoader(imageLoader)
		//  Shared Preferences
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		this.backupSharedPreferences =
			this.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

		XLog.i("Loading private shared preferences: ${this.sharedPreferences.all}")
		XLog.i("Loading backup shared preferences: ${this.backupSharedPreferences.all}")

		this.internalDir = File(filesDir, "stickers")
		this.toaster = Toaster(baseContext)
		this.favourites = StickerKeySet(this.backupSharedPreferences, "favourites")

		loadPreferences()
		loadPacks()
		this.appliedVersion = this.backupSharedPreferences.getInt("libraryVersion", 0)

		loadCaches()
		window.window?.navigationBarColor = getColor(R.color.bg)
	}

	/**
	 * Read every preference the keyboard acts on, plus the sizing derived from them. Split out of
	 * onCreate so a running keyboard can pick up a settings change instead of having to be restarted.
	 */
	private fun loadPreferences() {
		val scale = baseContext.resources.displayMetrics.density
		this.restoreOnClose = this.backupSharedPreferences.getBoolean("restoreOnClose", false)
		this.vertical = this.backupSharedPreferences.getBoolean("vertical", false)
		this.scroll = this.backupSharedPreferences.getBoolean("scroll", false)
		this.vibrate = this.backupSharedPreferences.getBoolean("vibrate", true)
		this.insensitiveSort = this.backupSharedPreferences.getBoolean("insensitiveSort", false)
		this.isPngFallback = this.backupSharedPreferences.getBoolean("isPngFallback", true)
		this.continuousScroll = this.backupSharedPreferences.getBoolean("continuousScroll", true)
		this.generateGifVariants =
			this.backupSharedPreferences.getBoolean("generateGifVariants", false)
		// Off by default: a still frame is memory-cacheable where an AnimatedImageDrawable is not, so
		// the grid stops re-decoding every animated sticker as it scrolls. Verified on device that the
		// platform decoder does return a first frame for an animated webp.
		this.animateGrid = this.backupSharedPreferences.getBoolean("animateGrid", false)

		this.iconsPerX = this.backupSharedPreferences.getInt("iconsPerX", 3)
		this.totalIconPadding =
			(resources.getDimension(R.dimen.sticker_padding) * 2 * (this.iconsPerX + 1)).toInt()
		this.iconSize =
			(
				if (this.vertical) {
					(resources.displayMetrics.widthPixels - this.totalIconPadding) / this.iconsPerX.toFloat()
				} else {
					(this.backupSharedPreferences.getInt("iconSize", 80) * scale)
				}
				).toInt()
	}

	/**
	 * Scan the imported sticker tree. Rebuilds both collections from scratch rather than adding to
	 * them, since this now runs more than once per process.
	 */
	private fun loadPacks() {
		this.loadedPacks = HashMap()
		val packs =
			this.internalDir.listFiles { obj: File ->
				obj.isDirectory && !obj.absolutePath.contains("__compatSticker__")
			}
				?: arrayOf()
		var stickers = listOf<File>()
		for (file in packs) {
			val pack = StickerPack(file)
			if (pack.stickerList.isNotEmpty()) {
				this.loadedPacks[file.name] = pack
			}
			stickers = stickers + pack.stickerList
		}
		this.allStickers = stickers
		XLog.i("Loaded all packs: [${this.loadedPacks.keys.joinToString(", ")}]")
	}

	/**
	 * Restore the recents and compat caches, and which pack was last open.
	 *
	 * These have to be re-read on a reload, not just at startup: choosing a new sticker directory
	 * clears them in the settings screen, and a keyboard still holding the old ones writes them
	 * straight back over that reset in onFinishInput, leaving recents pointing at deleted files for
	 * good. The existing Cache instances are refilled rather than replaced because StickerSender
	 * captures compatCache by identity and would otherwise keep writing into an orphan.
	 */
	private fun loadCaches() {
		this.activePack = this.sharedPreferences.getString("activePack", "").toString()
		this.recentCache.fromSharedPref(this.sharedPreferences.getString("recentCache", "") ?: "")
		this.compatCache.fromSharedPref(this.sharedPreferences.getString("compatCache", "") ?: "")
		XLog.i(
			"Loaded caches (recents=${this.recentCache.toFiles().size}, " +
				"compat=${this.compatCache.toFiles().size}, activePack='${this.activePack}')",
		)
	}

	/**
	 * Pick up a settings change or a re-import if one happened since this keyboard last looked. Only
	 * re-reads from disk when the stored generation actually moved, so focusing a field stays cheap.
	 */
	private fun reloadIfConfigurationChanged() {
		val version = this.backupSharedPreferences.getInt("libraryVersion", 0)
		if (version == this.appliedVersion) {
			return
		}
		XLog.i("Configuration changed (v${this.appliedVersion} -> v$version), reloading")
		loadPreferences()
		loadPacks()
		loadCaches()
		this.appliedVersion = version
		// onStartInputView is skipped when an app merely restarts input on a field it already owns,
		// so a view that is on screen right now would otherwise keep rendering the previous library
		// and sizing. Replace it here in that case, and defer otherwise.
		if (isInputViewShown()) {
			XLog.i("Rebuilding the visible input view for the new configuration")
			setInputView(onCreateInputView())
		} else {
			this.rebuildInputView = true
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
		val keyboardLayout = View.inflate(baseContext, R.layout.keyboard_layout, null)
		gestureDetector = GestureDetector(baseContext, GestureListener())

		this.keyboardRoot = keyboardLayout.findViewById(R.id.keyboardRoot)
		this.packsList = keyboardLayout.findViewById(R.id.packsList)
		this.packsScroller = keyboardLayout.findViewById(R.id.topHScrollView)
		this.packContent = keyboardLayout.findViewById(R.id.packContent)
		this.resizeHandle = keyboardLayout.findViewById(R.id.resizeHandle)
		attachResizeHandle()
		this.keyboardHeight = storedKeyboardHeight()
		this.packContent.layoutParams?.height = this.keyboardHeight
		this.fullIconSize = fullIconSizeFor(this.keyboardHeight)
		createPackIcons()
		// Whatever this returns is already built from the current configuration, so a pending
		// rebuild is satisfied; leaving the flag set would inflate the whole keyboard a second time.
		this.rebuildInputView = false
		return keyboardLayout
	}

	/**
	 * Portrait and landscape keep separate heights. One shared value either overflows the shorter
	 * screen or wastes most of the taller one, and clamping cannot tell the two apart after the
	 * fact because it only ever sees the current screen.
	 */
	private fun keyboardHeightKey(): String =
		if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			"keyboardHeightLandscape"
		} else {
			"keyboardHeight"
		}

	/** What the keyboard was before the user ever dragged it. */
	private fun defaultKeyboardHeight(): Int =
		if (this.vertical) {
			DEFAULT_VERTICAL_HEIGHT
		} else {
			this.iconSize * this.iconsPerX + this.totalIconPadding
		}

	// Two pack rows keeps a row of stickers plus its padding reachable at the smallest size.
	private fun minKeyboardHeight(): Int =
		resources.getDimensionPixelOffset(R.dimen.pack_dimens) * 2

	private fun maxKeyboardHeight(): Int =
		(resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
			.coerceAtLeast(minKeyboardHeight())

	/**
	 * The height to open at: whatever was last dragged to, else the default. Clamped on the way
	 * out, because a height stored on one screen can be impossible on another -- a foldable being
	 * opened, or a display-size change -- and a stale value must never leave the keyboard unusable.
	 */
	private fun storedKeyboardHeight(): Int {
		val stored = this.backupSharedPreferences.getInt(keyboardHeightKey(), 0)
		val height = if (stored > 0) stored else defaultKeyboardHeight()
		return height.coerceIn(minKeyboardHeight(), maxKeyboardHeight())
	}

	/**
	 * Long-press preview image size for a given keyboard height. The preview surface carries a
	 * header row and a caption around the image, so the image has to leave room for them or the
	 * surface grows past the keyboard and clips.
	 */
	private fun fullIconSizeFor(height: Int): Int {
		val previewChrome = resources.getDimensionPixelOffset(R.dimen.pack_dimens) +
			resources.getDimensionPixelOffset(R.dimen.text_size_body) * 4
		return (min(resources.displayMetrics.widthPixels, height - previewChrome) * 0.92)
			.toInt()
			.coerceAtLeast(resources.getDimensionPixelOffset(R.dimen.pack_dimens))
	}

	/**
	 * Drag the handle to resize the keyboard. Guarded end to end: an exception escaping a touch
	 * listener kills the keyboard mid-typing, so a failure here abandons the gesture instead.
	 */
	private fun attachResizeHandle() {
		this.resizeHandle.setOnTouchListener { view, event ->
			try {
				when (event.actionMasked) {
					MotionEvent.ACTION_DOWN -> {
						this.dragStartRawY = event.rawY
						this.dragStartHeight = this.keyboardHeight
						view.isPressed = true
						true
					}

					MotionEvent.ACTION_MOVE -> {
						// The keyboard grows upward from the bottom of the screen, so a finger
						// moving towards the top -- a falling rawY -- has to increase the height.
						applyKeyboardHeight(
							this.dragStartHeight + (this.dragStartRawY - event.rawY).toInt(),
						)
						true
					}

					MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
						view.isPressed = false
						// apply(), not commit(): this is the thread drawing the keyboard.
						this.backupSharedPreferences.edit()
							.putInt(keyboardHeightKey(), this.keyboardHeight)
							.apply()
						if (event.actionMasked == MotionEvent.ACTION_UP) {
							view.performClick()
						}
						true
					}

					else -> false
				}
			} catch (e: Throwable) {
				XLog.e("Resize gesture failed, abandoning it")
				XLog.e(e)
				this.resizeHandle.isPressed = false
				false
			}
		}
	}

	/** Apply a height mid-drag, bringing everything derived from the old one along with it. */
	private fun applyKeyboardHeight(target: Int) {
		val clamped = target.coerceIn(minKeyboardHeight(), maxKeyboardHeight())
		if (clamped == this.keyboardHeight) {
			return
		}
		this.keyboardHeight = clamped
		this.packContent.layoutParams?.height = clamped
		this.packContent.requestLayout()
		this.fullIconSize = fullIconSizeFor(clamped)
		resizeSearchResults(clamped)
	}

	/**
	 * Keep an open search view's results box in step. Rebuilding the view would be simpler but
	 * would throw away whatever has been typed, so only the box is resized here; the result cells
	 * pick up the new size on the next keystroke.
	 */
	private fun resizeSearchResults(height: Int) {
		val results = this.packContent.findViewById<LinearLayout>(R.id.search_results) ?: return
		val chrome = resources.getDimension(R.dimen.qwerty_row_height) * 5
		results.layoutParams?.height = (height - chrome)
			.coerceAtLeast(resources.getDimension(R.dimen.pack_dimens))
			.toInt()
		results.requestLayout()
	}

	/** Install a freshly built input view when the configuration moved under us. */
	override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
		super.onStartInputView(info, restarting)
		if (this.rebuildInputView) {
			this.rebuildInputView = false
			XLog.i("Rebuilding the input view for the new configuration")
			setInputView(onCreateInputView())
		}
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
		// Before building the sender, because it captures isPngFallback and generateGifVariants and
		// would otherwise stay a session behind a change to either.
		reloadIfConfigurationChanged()
		// The outgoing sender may still be finishing a conversion; tell it not to act on a field it
		// no longer owns.
		if (this::stickerSender.isInitialized) {
			this.stickerSender.abandon()
		}
		this.stickerSender = StickerSender(
			this.baseContext,
			this.toaster,
			this.internalDir,
			this.currentInputConnection,
			this.currentInputEditorInfo,
			this.compatCache,
			this.imageLoader,
			this.isPngFallback,
			this.generateGifVariants,
		)
	}

	/** When leaving some input field update the caches */
	override fun onFinishInput() {
		XLog.i("Updating sharedPreferences based on use, and closing...")
		val editor = this.sharedPreferences.edit()
		editor.putString("recentCache", this.recentCache.toSharedPref())
		editor.putString("compatCache", this.compatCache.toSharedPref())
		editor.putString("activePack", this.activePack)
		editor.apply()
		if (this::stickerSender.isInitialized) {
			this.stickerSender.abandon()
		}
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
		XLog.i("Switching pack to '$packName'")
		this.activePack = packName
		highlightPackButton(packName)

		// Both synthetic tabs are filtered through isFile: they are built from stored paths, and an
		// import that drops or renames a sticker would otherwise leave a blank cell that still commits
		// a uri to a file which is not there -- reporting success and delivering nothing.
		val stickers: Array<File> = when (packName) {
			FAVOURITES_PACK -> this.favourites.resolve(this.internalDir)
			RECENT_PACK -> this.recentCache.toFiles().filter { it.isFile }.reversed().toTypedArray()
			else -> loadedPacks[packName]?.stickerList ?: return
		}
		val recyclerView = RecyclerView(this)
		val adapter = StickerPackAdapter(
			iconSize,
			stickers,
			this,
			gestureDetector,
			this.vibrate,
			this.animateGrid,
		)
		val layoutManager = GridLayoutManager(
			this,
			iconsPerX,
			if (vertical) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL,
			false,
		)
		recyclerView.layoutManager = layoutManager
		recyclerView.adapter = adapter
		allPacksView = null
		packContent.removeAllViewsInLayout()
		packContent.addView(recyclerView)
	}

	/**
	 * Tint the pack bar so that only the given tab reads as selected.
	 *
	 * @param tag String tag of the pack button to mark active
	 */
	private fun highlightPackButton(tag: String) {
		var activeCard: View? = null
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			val active = packButton.tag == tag
			// A filled container behind the icon, not a colour filter over it. Tinting recoloured the
			// sticker artwork itself, which is why a selected pack used to look washed out.
			packCard.setBackgroundResource(if (active) R.drawable.pack_selected else 0)
			if (active) {
				activeCard = packCard
			}
		}

		// With a button per pack the bar is several screens wide, so tinting a card means nothing
		// unless the bar is also brought to it - otherwise the highlight that follows the sticker
		// list is usually scrolled out of sight. Deferred because a card laid out this frame has no
		// position yet.
		val card = activeCard ?: return
		val scroller = this.packsScroller ?: return
		scroller.post {
			val centred = card.left - (scroller.width - card.width) / 2
			scroller.smoothScrollTo(centred.coerceAtLeast(0), 0)
		}
	}

	/** Pack names in the order the pack bar shows them */
	private fun sortedPackNames(): List<String> =
		if (this.insensitiveSort) {
			this.loadedPacks.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
		} else {
			this.loadedPacks.keys.sorted()
		}

	/**
	 * Show every pack in one scrollable list, each behind a labelled divider, and optionally jump
	 * straight to one of them.
	 *
	 * @param scrollToPack String? pack to bring to the top, or null to stay at the beginning
	 */
	private fun showAllPacks(scrollToPack: String?) {
		val items = mutableListOf<AllPacksAdapter.Item>()
		val positions = mutableMapOf<String, Int>()
		for (packName in sortedPackNames()) {
			val pack = this.loadedPacks[packName] ?: continue
			positions[packName] = items.size
			items.add(AllPacksAdapter.Item.PackHeader(packName))
			pack.stickerList.forEach { items.add(AllPacksAdapter.Item.Sticker(it)) }
		}
		if (items.isEmpty()) {
			return
		}
		this.packHeaderPositions = positions
		this.packHeadersByPosition = positions.map { it.value to it.key }.sortedByDescending { it.first }

		val adapter =
			AllPacksAdapter(iconSize, items, this, gestureDetector, this.vibrate, this.animateGrid)
		val layoutManager = GridLayoutManager(this, iconsPerX, RecyclerView.VERTICAL, false)
		layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
			// A pack label is a divider and a name, so it needs the whole row rather than one cell.
			override fun getSpanSize(position: Int): Int =
				if (adapter.isHeader(position)) iconsPerX else 1
		}

		val recyclerView = RecyclerView(this)
		recyclerView.layoutManager = layoutManager
		recyclerView.adapter = adapter
		recyclerView.addOnScrollListener(
			object : RecyclerView.OnScrollListener() {
				/**
				 * Whether the list is moving because it was dragged. Jumping to a pack settles over
				 * several layout passes, and treating those as scrolling would walk the remembered
				 * pack through everything in between and save whichever one it stopped on.
				 */
				private var dragged = false

				override fun onScrollStateChanged(view: RecyclerView, state: Int) {
					when (state) {
						RecyclerView.SCROLL_STATE_DRAGGING -> dragged = true
						RecyclerView.SCROLL_STATE_IDLE -> dragged = false
						else -> Unit // settling after a fling still counts as the user's scroll
					}
				}

				override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
					if (!dragged) {
						return
					}
					val first = layoutManager.findFirstVisibleItemPosition()
					if (first == RecyclerView.NO_POSITION) {
						return
					}
					// Keep the pack bar reflecting whatever is on screen, so scrolling past the end
					// of a pack moves the highlight along with it.
					val visiblePack =
						packHeadersByPosition.firstOrNull { it.first <= first }?.second ?: return
					if (visiblePack != activePack) {
						XLog.i("Scrolled into pack '$visiblePack' (row $first)")
						activePack = visiblePack
						highlightPackButton(visiblePack)
					}
				}
			},
		)

		packContent.removeAllViewsInLayout()
		packContent.addView(recyclerView)
		this.allPacksView = recyclerView

		val target = scrollToPack ?: sortedPackNames().firstOrNull()
		if (target != null) {
			positions[target]?.let { layoutManager.scrollToPositionWithOffset(it, 0) }
			this.activePack = target
			highlightPackButton(target)
		}
	}

	/**
	 * Bring a pack to the top of the all-packs list, building that list first if some other tab (the
	 * recents grid or the search page) is currently showing.
	 *
	 * @param packName String pack to scroll to
	 */
	private fun jumpToPack(packName: String) {
		val recyclerView = this.allPacksView
		if (recyclerView == null || recyclerView.parent !== packContent) {
			showAllPacks(packName)
			return
		}
		val position = this.packHeaderPositions[packName] ?: return
		(recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(position, 0)
		this.activePack = packName
		highlightPackButton(packName)
	}

	/** Show a pack, either by scrolling the combined list to it or by showing it on its own */
	private fun selectPack(packName: String) {
		if (this.continuousScroll && this.vertical) {
			jumpToPack(packName)
		} else {
			switchPackLayout(packName)
		}
	}

	/**
	 * Set the current tab to the search page/ view
	 */
	private fun searchView() {
		XLog.i("Switching to search")
		highlightPackButton("__search__")
		allPacksView = null

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

		// Never below one row: with few rows, a small icon size or a large font scale the qwerty rows
		// can cost more than the whole keyboard height, and everything derived from this went negative.
		val usableResultsHeight =
			searchResultsHeight.coerceAtLeast(resources.getDimension(R.dimen.pack_dimens))
		searchResults.layoutParams.height = usableResultsHeight.toInt()

		fun searchStickers(query: String): List<File> {
			// Every whitespace-separated token has to appear somewhere in "pack/filename", so word
			// order does not matter and a pack name narrows the results like any other term.
			val terms = query.split(' ', '\t').filter { it.isNotBlank() }
			if (terms.isEmpty()) {
				return emptyList()
			}
			// Results are capped downstream, and matching pack names means one big pack can now fill
			// that cap on its name alone. Rank a sticker whose own name matches above one that only
			// matched through its pack, so the obvious hit is never the entry that gets truncated.
			return this.allStickers.mapNotNull { sticker ->
				val name = sticker.nameWithoutExtension
				val pack = sticker.parentFile?.name.orEmpty()
				val haystack = "$pack/$name"
				if (!terms.all { haystack.contains(it, ignoreCase = true) }) {
					return@mapNotNull null
				}
				val inName = terms.count { name.contains(it, ignoreCase = true) }
				sticker to inName
			}.sortedByDescending { it.second }.map { it.first }
		}

		fun updateSearchResults(stickers: List<File>) {
			val recyclerView = RecyclerView(baseContext)
			val adapter = StickerPackAdapter(
				(usableResultsHeight * 0.9).toInt().coerceAtLeast(1),
				stickers.take(128).toTypedArray(),
				this,
				gestureDetector,
				this.vibrate,
				this.animateGrid,
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
		packButton.setOnClickListener { selectPack(it?.tag as String) }
		this.packsList.addView(packCard)
		return packButton
	}

	/** Create the pack icons (image buttons) that when tapped switch the pack (switchPackLayout) */
	private fun createPackIcons() {
		buildPackBar()
		restoreActivePack()
	}

	/**
	 * Rebuild the pack bar only. Kept separate from choosing what to show, so that something which
	 * merely changes the set of buttons -- starring the first sticker, unstarring the last -- does not
	 * also renavigate and throw away where the user had scrolled to.
	 */
	private fun buildPackBar() {
		this.packsList.removeAllViewsInLayout()
		// Back button
		if (this.backupSharedPreferences.getBoolean("showBackButton", true)) {
			val backButton = addPackButton("__back__")
			backButton.load(getDrawable(R.drawable.arrow_back_circle))
			backButton.setOnClickListener {
				closeKeyboard()
			}
		}

		// Search
		if (this.backupSharedPreferences.getBoolean("showSearchButton", true)) {
			val searchButton = addPackButton("__search__")
			searchButton.load(getDrawable(R.drawable.search_circle))
			searchButton.setOnClickListener {
				searchView()
			}
		}
		// Recent
		val recentPackName = RECENT_PACK
		val recentButton = addPackButton(recentPackName)
		recentButton.load(getDrawable(R.drawable.time))
		recentButton.setOnClickListener { switchPackLayout(recentPackName) }
		// Favourites, only once there is something in it -- an always-present empty tab is noise
		if (!!this.favourites.hasAny(this.internalDir)) {
			val favouritesButton = addPackButton(FAVOURITES_PACK)
			favouritesButton.load(getDrawable(R.drawable.star_filled_circle))
			favouritesButton.setOnClickListener { switchPackLayout(FAVOURITES_PACK) }
		}
		// Packs
		val sortedPackNames = sortedPackNames().toTypedArray()

		for (sortedPackName in sortedPackNames) {
			val packButton = addPackButton(sortedPackName)
			val thumb = this.loadedPacks[sortedPackName]?.thumbSticker
			packButton.load(thumb) {
				// Always a still frame here regardless of the grid preference: an animated first
				// sticker would otherwise run an AnimatedImageDrawable in a 40dp button for as long as
				// the keyboard is open, and never be memory-cached.
				if (thumb != null && thumb.extension.lowercase() in PLATFORM_DECODABLE) {
					decoderFactory(BitmapFactoryDecoder.Factory())
				}
			}
			packButton.setOnClickListener { selectPack(sortedPackName) }
		}

	}

	/**
	 * Delete a sticker for good: from the source folder it was imported from, from the imported copy,
	 * and from everything generated off the back of it.
	 *
	 * Removing only the imported copy would be undone by the next import, which would copy it back
	 * from a source that still has it, so the source document goes first and the rest follows only if
	 * that succeeded.
	 *
	 * @param sticker File the imported sticker
	 * @param onDone (Boolean) -> Unit called on the main thread with whether it went
	 */
	private fun deleteSticker(sticker: File, onDone: (Boolean) -> Unit) {
		val pack = sticker.parentFile?.name
		if (pack == null) {
			onDone(false)
			return
		}
		val treePath = this.sharedPreferences.getString("stickerDirPath", null)
		CoroutineScope(Dispatchers.Main).launch {
			val deleted = withContext(Dispatchers.IO) {
				runCatching { removeEverywhere(sticker, pack, treePath) }
					.getOrElse {
						if (it is CancellationException) throw it
						// An escape here would take the keyboard down mid-typing.
						XLog.e("Failed to delete '${sticker.name}'")
						XLog.e(it)
						false
					}
			}
			if (deleted) {
				// The pack listing was snapshotted at load, so it still holds the deleted file.
				loadPacks()
				buildPackBar()
				refreshContentAfterLibraryChange()
			}
			onDone(deleted)
		}
	}

	/**
	 * The actual removal, off the main thread. Returns false without touching the imported copy when
	 * the source document is still there but could not be deleted, so the two never disagree.
	 */
	private fun removeEverywhere(sticker: File, pack: String, treePath: String?): Boolean {
		if (treePath != null) {
			val tree = Uri.parse(treePath)
			// FAILED is not ABSENT: if the lookup could not be completed, deleting the imported copy
			// would only hide a sticker the next import brings straight back.
			if (SourceListing.removeDocument(baseContext, tree, pack, sticker.name) ==
				SourceListing.Removal.FAILED
			) {
				return false
			}
			// tools/gif-variants.py writes its output as a sibling of the webp. Leaving that behind
			// means the next import no longer sees a webp of the same name, so the orphan is imported
			// as a sticker in its own right and the deleted sticker reappears as a GIF.
			if (sticker.name.endsWith(".webp", ignoreCase = true)) {
				SourceListing.removeDocument(
					baseContext,
					tree,
					pack,
					"${sticker.nameWithoutExtension}.gif",
				)
			}
		} else {
			XLog.w("No sticker directory is set, deleting the imported copy only")
		}

		// Everything derived from this sticker. Neither has another owner once it is gone, and the
		// generated GIF in particular cannot be rebuilt from anything.
		val variant = File(filesDir, "variants/$pack/${sticker.nameWithoutExtension}.gif")
		val compat = File(internalDir, "__compatSticker__/${sticker.hashCode()}.png")
		val derived = listOf(variant, compat).count { it.isFile && it.delete() }

		favourites.remove(sticker)
		recentCache.remove(sticker.absolutePath)
		val removed = !sticker.exists() || sticker.delete()

		// A pack that just lost its last sticker leaves an empty directory in all three trees.
		val emptiedPack = File(internalDir, pack).listFiles()?.isEmpty() == true
		if (emptiedPack) {
			File(internalDir, pack).delete()
			File(filesDir, "variants/$pack").takeIf { it.listFiles()?.isEmpty() == true }?.delete()
			if (treePath != null) {
				SourceListing.deletePackIfEmpty(baseContext, Uri.parse(treePath), pack, MAX_TREE_ENTRIES)
			}
		}

		XLog.i(
			"Deleted '$pack/${sticker.name}' (copy: $removed, derived files: $derived, " +
				"pack emptied: $emptiedPack)",
		)
		return removed
	}

	/**
	 * Redraw the sticker area after the library itself changed.
	 *
	 * jumpToPack deliberately reuses an all-packs list that is already built and merely scrolls it,
	 * which is right when only the selection changed. It is wrong after a sticker is deleted: the
	 * adapter still holds a cell for a file that has gone, which draws as a blank gap instead of the
	 * grid closing up. Dropping the reference forces the list to be rebuilt from the reloaded packs.
	 */
	private fun refreshContentAfterLibraryChange() {
		this.allPacksView = null
		this.packHeaderPositions = emptyMap()
		this.packHeadersByPosition = emptyList()
		restoreActivePack()
	}

	/** Show whichever tab was last open, falling back to the first pack */
	private fun restoreActivePack() {
		val sortedPackNames = sortedPackNames().toTypedArray()
		// The synthetic tabs have to be members here or the keyboard forgets them on every restart and
		// silently falls back to the first pack.
		val syntheticTabs = buildList {
			add(RECENT_PACK)
			if (favourites.hasAny(internalDir)) add(FAVOURITES_PACK)
		}
		val targetPack =
			if (activePack in sortedPackNames + syntheticTabs) {
				activePack
			} else {
				sortedPackNames.firstOrNull()
			}

		if (sortedPackNames.isNotEmpty()) {
			if (targetPack in syntheticTabs) {
				switchPackLayout(targetPack!!)
			} else {
				targetPack?.let { selectPack(it) }
			}
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
		this.recentCache.add(sticker.path)
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

		val star = fullStickerLayout.findViewById<ImageButton>(R.id.favouriteButton)
		fun paintStar() {
			star.load(
				getDrawable(
					if (favourites.contains(sticker)) {
						R.drawable.star_filled_circle
					} else {
						R.drawable.star_circle
					},
				),
			)
		}
		paintStar()
		star.setOnClickListener {
			val tabWasShown = favourites.hasAny(internalDir)
			val nowFavourite = favourites.toggle(sticker)
			paintStar()
			if (vibrate && SDK_INT >= Build.VERSION_CODES.O_MR1) {
				it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
			}
			toaster.toast(
				getString(
					if (nowFavourite) R.string.favourite_added else R.string.favourite_removed,
				),
			)
			// The tab appears with the first star and goes with the last, so the bar needs rebuilding
			// then -- but only then, and without renavigating, or starring would scroll the grid back
			// to the top of the current pack under the preview the user is still looking at.
			if (tabWasShown != favourites.hasAny(internalDir)) {
				buildPackBar()
				highlightPackButton(activePack)
				// Unstarring the last favourite removes the tab the user may be standing on.
				if (activePack == FAVOURITES_PACK && !favourites.hasAny(internalDir)) {
					restoreActivePack()
				}
			} else if (activePack == FAVOURITES_PACK) {
				// Still on the favourites tab, so its contents just changed under us.
				switchPackLayout(FAVOURITES_PACK)
			}
		}

		val deleteButton = fullStickerLayout.findViewById<ImageButton>(R.id.deleteButton)
		deleteButton.load(getDrawable(R.drawable.delete_circle))
		var armed = false
		val disarm = Runnable {
			armed = false
			deleteButton.load(getDrawable(R.drawable.delete_circle))
		}
		deleteButton.setOnClickListener {
			if (vibrate && SDK_INT >= Build.VERSION_CODES.O_MR1) {
				it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
			}
			if (!armed) {
				// Deleting removes the file from the sticker folder, so it takes two deliberate taps.
				armed = true
				deleteButton.load(getDrawable(R.drawable.delete_confirm_circle))
				toaster.toast(getString(R.string.delete_confirm))
				deleteButton.postDelayed(disarm, DELETE_ARM_MS)
				return@setOnClickListener
			}
			deleteButton.removeCallbacks(disarm)
			deleteButton.isEnabled = false
			deleteSticker(sticker) { deleted ->
				toaster.toast(
					getString(if (deleted) R.string.delete_done else R.string.delete_failed),
				)
				if (deleted) {
					keyboardRoot.removeView(fullStickerLayout)
				} else {
					deleteButton.isEnabled = true
					disarm.run()
				}
			}
		}

		// Tap to exit popup
		fullStickerLayout.setOnClickListener { this.keyboardRoot.removeView(it) }
		fSticker.setOnClickListener { this.keyboardRoot.removeView(fullStickerLayout) }
		this.keyboardRoot.addView(fullStickerLayout)
	}

	internal fun switchToPreviousPack() {
		// sortedPackNames() rather than keys.sorted(), so swiping visits packs in the same order the
		// pack bar shows them. With no packs at all the wrap-around below would index [-1].
		val sortedPackNames = sortedPackNames()
		if (sortedPackNames.isEmpty()) {
			return
		}
		val currentIndex = sortedPackNames.indexOf(activePack)
		val previousIndex = if (currentIndex > 0) currentIndex - 1 else sortedPackNames.size - 1
		selectPack(sortedPackNames[previousIndex])
	}

	internal fun switchToNextPack() {
		// An empty library would make the modulo below a division by zero.
		val sortedPackNames = sortedPackNames()
		if (sortedPackNames.isEmpty()) {
			return
		}
		val currentIndex = sortedPackNames.indexOf(activePack)
		val nextIndex = (currentIndex + 1) % sortedPackNames.size
		selectPack(sortedPackNames[nextIndex])
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
				!(continuousScroll && vertical) &&
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
