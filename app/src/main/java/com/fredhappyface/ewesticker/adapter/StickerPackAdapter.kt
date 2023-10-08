package com.fredhappyface.ewesticker.adapter

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fredhappyface.ewesticker.R
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.view.StickerPackViewHolder
import java.io.File

class StickerPackAdapter(
	private val iconSize: Int,
	private val stickers: Array<File>,
	private val listener: StickerClickListener,
	private val gestureDetector: GestureDetector,
) :

	RecyclerView.Adapter<StickerPackViewHolder>() {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerPackViewHolder {
		val itemView = LayoutInflater.from(parent.context)
			.inflate(R.layout.sticker_card, parent, false)
		return StickerPackViewHolder(itemView)
	}

	override fun onBindViewHolder(holder: StickerPackViewHolder, position: Int) {
		val stickerFile = stickers[position]
		holder.stickerThumbnail.load(stickerFile)
		holder.stickerThumbnail.layoutParams.height = iconSize
		holder.stickerThumbnail.layoutParams.width = iconSize
		holder.stickerThumbnail.tag = stickerFile
		holder.stickerThumbnail.setOnClickListener {
			val file = it.tag as File
			listener.onStickerClicked(file)
		}
		holder.stickerThumbnail.setOnLongClickListener {
			val file = it.tag as File
			listener.onStickerLongClicked(file)
			return@setOnLongClickListener true
		}
		holder.stickerThumbnail.setOnTouchListener { _, event ->
			return@setOnTouchListener gestureDetector.onTouchEvent(event)
		}
	}

	override fun getItemCount(): Int = stickers.size
}
