package com.fredhappyface.ewesticker.utilities

import java.io.File
import java.util.ArrayDeque
import java.util.Deque

/**
 * Basically this behaves like an ordered set with some maximum capacity. When this capacity is
 * exceeded an element is removed from the start
 */
class Cache(private val capacity: Int = 30) {
	private val data: Deque<String> = ArrayDeque()

	/**
	 * Logic to add an element
	 *
	 * @param elem
	 *
	 * @return
	 */
	fun add(elem: String): String? {
		var evicted: String? = null
		if (data.contains(elem)) {
			data.remove(elem)
		} else if (data.size >= capacity) {
			// Returning here used to skip the insert below, so a full cache dropped an old entry
			// without keeping the new one and never got back to capacity.
			evicted = data.pollFirst()
		}
		data.offerLast(elem)
		return evicted
	}

	/** Forget an element, used when the sticker it points at has been deleted */
	fun remove(elem: String) {
		data.remove(elem)
	}

	/**
	 * convert this to a string to write to shared-pref
	 *
	 * @return
	 */
	fun toSharedPref(): String {
		return data.joinToString("\n")
	}

	/**
	 * convert this to a array of files
	 *
	 * @return
	 */
	fun toFiles(): Array<File> {
		return data.map { File(it) }.toTypedArray()
	}

	/** convert from a string (shared-pref) to this */
	fun fromSharedPref(raw: String) {
		data.clear()
		if (raw.trim().isNotEmpty()) {
			data.addAll(raw.split("\n"))
		}
	}
}
