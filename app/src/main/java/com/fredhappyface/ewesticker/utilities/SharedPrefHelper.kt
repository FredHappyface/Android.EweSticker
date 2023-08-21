package com.fredhappyface.ewesticker.utilities

import android.content.Context
import androidx.preference.PreferenceManager
import com.fredhappyface.ewesticker.model.StickerPack
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

object SharedPrefHelper {
	private const val KEY_STICKERS = "com.fredhappyface.ewesticker.pref.stickers"

	fun getStickerPacksFromPref(context: Context): List<StickerPack> {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
		val gson = Gson()
		val json = sharedPreferences.getString(KEY_STICKERS, null) ?: return emptyList()

		return try {
			gson.fromJson(json, object : TypeToken<ArrayList<StickerPack>>() {}.type)
		} catch (e: JsonSyntaxException) {
			emptyList()
		}
	}

}
