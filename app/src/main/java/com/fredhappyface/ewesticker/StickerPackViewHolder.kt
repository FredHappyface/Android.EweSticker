package com.fredhappyface.ewesticker

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class StickerPackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
	val stickerThumbnail: ImageView = itemView.findViewById(R.id.stickerButton)
}
