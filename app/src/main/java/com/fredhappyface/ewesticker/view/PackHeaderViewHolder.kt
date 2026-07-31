package com.fredhappyface.ewesticker.view

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fredhappyface.ewesticker.R

class PackHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
	val packName: TextView = itemView.findViewById(R.id.packHeaderName)
	val divider: View = itemView.findViewById(R.id.packHeaderDivider)
}
