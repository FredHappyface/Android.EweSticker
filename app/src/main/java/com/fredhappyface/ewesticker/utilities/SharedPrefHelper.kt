package com.fredhappyface.ewesticker.utilities

import android.content.Context
import android.content.SharedPreferences
import com.fredhappyface.ewesticker.model.StickerPack
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

private const val SHARED_PREF = "com.fredhappyface.ewesticker.pref"
private const val KEY_STICKERS = "com.fredhappyface.ewesticker.pref.stickers"

class SharedPrefHelper {
	fun getSharedPreferences(context: Context): SharedPreferences {
		return context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE)
	}


	fun getStickerPacksFromPref(context: Context): ArrayList<StickerPack?> {
		val sharedPreferences =
			context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE)
		val gson = Gson()
		val json = sharedPreferences.getString(KEY_STICKERS, "")
		val stickerPacks: ArrayList<StickerPack?> = if (json == "") {
			ArrayList()
		} else {
			try {
				gson.fromJson(
					sharedPreferences.getString(KEY_STICKERS, null),
					object : TypeToken<ArrayList<StickerPack>>() {}.type
				)
			} catch (e: JsonSyntaxException) {
				ArrayList()
			}
		}
		return stickerPacks
	}
}