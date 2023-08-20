package com.fredhappyface.ewesticker.utilities

import android.content.Context
import android.content.SharedPreferences
import com.fredhappyface.ewesticker.model.StickerPack
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

class SharedPrefHelper {

	private val SHARED_PREF = "com.fredhappyface.ewesticker.pref"
	private val KEY_STICKERS = "com.fredhappyface.ewesticker.pref.stickers"

	fun getSharedPreferences(context: Context): SharedPreferences{
		return context.getSharedPreferences(this.SHARED_PREF, Context.MODE_PRIVATE)
	}


	fun getStickerPacksFromPref(context: Context): ArrayList<StickerPack?> {
		val sharedPreferences =
			context.getSharedPreferences(this.SHARED_PREF, Context.MODE_PRIVATE)
		val gson = Gson()
		val json = sharedPreferences.getString(this.KEY_STICKERS, "")
		val stickerPacks: ArrayList<StickerPack?> = if (json == "") {
			ArrayList<StickerPack?>()
		} else {
			try {
				gson.fromJson(
					sharedPreferences.getString(this.KEY_STICKERS, null),
					object : TypeToken<ArrayList<StickerPack>>() {}.type
				)
			} catch (e: JsonSyntaxException) {
				ArrayList<StickerPack?>()
			}
		}
		return stickerPacks
	}
}