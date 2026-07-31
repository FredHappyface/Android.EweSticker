package com.fredhappyface.ewesticker.utilities

import android.content.SharedPreferences
import com.elvishew.xlog.XLog
import java.io.File

/**
 * A named set of stickers, remembered across imports.
 *
 * Entries are "pack/filename" rather than absolute paths, because every import rewrites the imported
 * tree; recents stores absolute paths and loses its contents whenever a sticker is re-copied to a
 * different location.
 *
 * Used for the starred stickers and, separately, for the ones the user has deleted -- those are
 * remembered so the importer can keep skipping them instead of copying them back in from a source
 * folder that still has them.
 *
 * @property preferences SharedPreferences the backup preferences, shared with the settings screen
 * @property preferenceKey String which set this instance reads and writes
 */
class StickerKeySet(
	private val preferences: SharedPreferences,
	private val preferenceKey: String,
) {
	/**
	 * A snapshot of the stored keys.
	 *
	 * getStringSet hands back an instance the framework may reuse, and mutating it corrupts the
	 * in-memory preferences, so this always copies.
	 */
	private fun stored(): MutableSet<String> =
		preferences.getStringSet(preferenceKey, emptySet())?.toMutableSet() ?: mutableSetOf()

	/** "pack/filename" for a sticker in the imported tree */
	fun keyOf(sticker: File): String? {
		val pack = sticker.parentFile?.name ?: return null
		return "$pack/${sticker.name}"
	}

	fun contains(sticker: File): Boolean {
		val key = keyOf(sticker) ?: return false
		return key in stored()
	}

	/**
	 * Star or unstar a sticker.
	 *
	 * @param sticker File the sticker to toggle
	 * @return Boolean true if it is now a favourite
	 */
	fun toggle(sticker: File): Boolean {
		val key = keyOf(sticker) ?: return false
		val current = stored()
		val nowPresent = if (key in current) {
			current.remove(key)
			false
		} else {
			current.add(key)
			true
		}
		preferences.edit().putStringSet(preferenceKey, current).apply()
		XLog.i("${if (nowPresent) "Added" else "Removed"} '$key' ${if (nowPresent) "to" else "from"} $preferenceKey")
		return nowPresent
	}

	/**
	 * The starred stickers that still exist, in a stable order.
	 *
	 * Anything the source dropped is skipped rather than rendered as a blank cell that commits a uri
	 * to a file which is not there -- which reports success and delivers nothing.
	 *
	 * @param stickersDir File the imported stickers tree
	 * @return Array<File> existing favourites
	 */
	fun resolve(stickersDir: File): Array<File> {
		val current = stored()
		val alive = LinkedHashMap<String, File>()
		for (key in current.sorted()) {
			val file = File(stickersDir, key)
			if (file.isFile) {
				alive[key] = file
			}
		}
		// Dead keys are dropped as they are found. Without this a tab whose visibility is decided by
		// the raw set but whose contents are filtered by existence stays visible over an empty grid,
		// and the only way to unstar something is to long-press a sticker that no longer exists.
		if (alive.size != current.size) {
			XLog.i("Dropping ${current.size - alive.size} missing entr(ies) from $preferenceKey")
			preferences.edit().putStringSet(preferenceKey, alive.keys.toMutableSet()).apply()
		}
		return alive.values.toTypedArray()
	}

	/**
	 * Does anything in this set still exist? Answered through resolve so that a tab's visibility and
	 * its contents can never disagree.
	 */
	fun hasAny(stickersDir: File): Boolean = resolve(stickersDir).isNotEmpty()

	/** Forget a sticker, used when deleting one that was also starred */
	fun remove(sticker: File) {
		val key = keyOf(sticker) ?: return
		val current = stored()
		if (current.remove(key)) {
			preferences.edit().putStringSet(preferenceKey, current).apply()
		}
	}

	/** Is the set empty? Used to decide whether a tab is worth showing. */
	fun isEmpty(): Boolean = stored().isEmpty()
}
