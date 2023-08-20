package com.fredhappyface.ewesticker.utilities

import java.io.File
import java.util.*

/**
 * Basically this behaved like an ordered set with some maximum capacity. When this capacity is
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
		if (data.contains(elem)) {
			data.remove(elem)
		} else if (data.size >= capacity) {
			return data.pollFirst()
		}
		data.offerLast(elem)
		return null
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
		data.addAll(raw.split("\n"))
	}


}
