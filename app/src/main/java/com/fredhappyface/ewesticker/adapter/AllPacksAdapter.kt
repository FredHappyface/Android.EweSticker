package com.fredhappyface.ewesticker.adapter

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fredhappyface.ewesticker.R
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.view.PackHeaderViewHolder
import com.fredhappyface.ewesticker.view.StickerPackViewHolder
import java.io.File

private const val TYPE_HEADER = 0
private const val TYPE_STICKER = 1

/**
 * Every pack in one scrollable list, each preceded by a labelled divider.
 *
 * The alternative, one pack on screen at a time, means going back to the pack bar to see anything
 * else. Putting the whole library in a single list lets a scroll carry on past the end of a pack,
 * while the pack bar becomes a way to jump rather than the only way to move.
 *
 * @property iconSize Int side length to draw each sticker at
 * @property items List<Item> headers and stickers, already in display order
 */
class AllPacksAdapter(
	private val iconSize: Int,
	private val items: List<Item>,
	private val listener: StickerClickListener,
	private val gestureDetector: GestureDetector,
	private val vibrate: Boolean,
	private val animate: Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	/** One row of the flattened library: either a pack label or one of its stickers */
	sealed class Item {
		class PackHeader(val packName: String) : Item()

		class Sticker(val file: File) : Item()
	}

	override fun getItemViewType(position: Int): Int = when (items[position]) {
		is Item.PackHeader -> TYPE_HEADER
		is Item.Sticker -> TYPE_STICKER
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return if (viewType == TYPE_HEADER) {
			PackHeaderViewHolder(inflater.inflate(R.layout.pack_header, parent, false))
		} else {
			StickerPackViewHolder(inflater.inflate(R.layout.sticker_card, parent, false))
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = items[position]) {
			is Item.PackHeader -> {
				holder as PackHeaderViewHolder
				holder.packName.text = item.packName
				// A rule above the very first pack would look like a stray line under the pack bar.
				holder.divider.visibility = if (position == 0) View.GONE else View.VISIBLE
			}

			is Item.Sticker -> (holder as StickerPackViewHolder)
				.bindSticker(item.file, iconSize, vibrate, listener, gestureDetector, animate)
		}
	}

	override fun getItemCount(): Int = items.size

	/** Is the row at this position a pack label, and so in need of the full row width? */
	fun isHeader(position: Int): Boolean =
		position in items.indices && items[position] is Item.PackHeader
}
