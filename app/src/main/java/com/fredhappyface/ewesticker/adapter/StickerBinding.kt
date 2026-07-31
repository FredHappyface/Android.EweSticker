package com.fredhappyface.ewesticker.adapter

import android.os.Build
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import coil.decode.BitmapFactoryDecoder
import coil.load
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.view.StickerPackViewHolder
import java.io.File

/**
 * Bind a sticker to a thumbnail. Shared so that the single-pack grid and the all-packs list cannot
 * drift apart in how a sticker responds to being tapped, held or swiped over.
 *
 * @param file File the sticker this cell shows
 * @param iconSize Int side length to draw the thumbnail at
 * @param vibrate Boolean give haptic feedback when tapped
 * @param listener StickerClickListener target for taps and long presses
 * @param gestureDetector GestureDetector consulted so swipes over a sticker still reach the keyboard
 * @param animate Boolean play animated stickers in the grid rather than showing a still frame
 */
internal fun StickerPackViewHolder.bindSticker(
	file: File,
	iconSize: Int,
	vibrate: Boolean,
	listener: StickerClickListener,
	gestureDetector: GestureDetector,
	animate: Boolean,
) {
	stickerThumbnail.load(file) {
		// Without an explicit size coil waits for a layout pass to work one out, and then sizes
		// against the view rather than the cell we already know the dimensions of. Coerced because
		// coil rejects a non-positive dimension by throwing, and the search strip can compute a
		// negative cell size at some combinations of row count, icon size and font scale.
		size(iconSize.coerceAtLeast(1))
		if (!animate && usesPlatformDecoder(file)) {
			// A distinct cache key: without one this still frame and the animated preview of the same
			// file share an entry, and whichever lands first is served to the other.
			memoryCacheKey("still:${file.absolutePath}")
			// An animated result is an AnimatedImageDrawable, which coil never writes to its memory
			// cache -- only BitmapDrawables go in -- so every rebind while scrolling decodes the
			// whole container again. Decoding a single frame yields something cacheable.
			decoderFactory(STILL_FRAME_DECODER)
		}
	}
	stickerThumbnail.layoutParams.height = iconSize
	stickerThumbnail.layoutParams.width = iconSize
	stickerThumbnail.tag = file
	stickerThumbnail.setOnClickListener {
		val tapped = it.tag as File
		if (vibrate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
		}
		listener.onStickerClicked(tapped)
	}
	stickerThumbnail.setOnLongClickListener {
		listener.onStickerLongClicked(it.tag as File)
		return@setOnLongClickListener true
	}
	stickerThumbnail.setOnTouchListener { _, event ->
		return@setOnTouchListener gestureDetector.onTouchEvent(event)
	}
}

/**
 * Shared rather than built per bind: each instance carries its own semaphore, so allocating one per
 * cell removes the process-wide cap on concurrent bitmap decodes that the single instance provides.
 */
private val STILL_FRAME_DECODER = BitmapFactoryDecoder.Factory()

/**
 * Can the platform's own bitmap decoder read this, or does it need one of the decoders registered on
 * the loader? Forcing BitmapFactory on an svg or a video would leave the cell blank.
 */
private fun usesPlatformDecoder(file: File): Boolean =
	file.extension.lowercase() in setOf("webp", "gif", "png", "jpg", "jpeg", "heif", "heic", "bmp")
