package com.fredhappyface.ewesticker

import java.io.File

interface StickerClickListener {
	fun onStickerClicked(sticker: File)
}