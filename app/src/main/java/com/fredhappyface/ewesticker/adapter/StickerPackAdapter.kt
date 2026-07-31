package com.fredhappyface.ewesticker.adapter

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fredhappyface.ewesticker.R
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.view.StickerPackViewHolder
import java.io.File

class StickerPackAdapter(
	private val iconSize: Int,
	private val stickers: Array<File>,
	private val listener: StickerClickListener,
	private val gestureDetector: GestureDetector,
	private val vibrate: Boolean,
	private val animate: Boolean,
) :

	RecyclerView.Adapter<StickerPackViewHolder>() {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerPackViewHolder {
		val itemView = LayoutInflater.from(parent.context)
			.inflate(R.layout.sticker_card, parent, false)
		return StickerPackViewHolder(itemView)
	}

	override fun onBindViewHolder(holder: StickerPackViewHolder, position: Int) {
		holder.bindSticker(stickers[position], iconSize, vibrate, listener, gestureDetector, animate)
	}

	override fun getItemCount(): Int = stickers.size
}
