package com.fredhappyface.ewesticker

import java.io.File
import java.util.*

/**
 * Basically this behaved like an ordered set with some maximum capacity. When this capacity is
 * exceeded an element is removed from the start
 *
 */
class Cache(private val size: Int = 30) {
	private var mData: LinkedList<String> = LinkedList()

	/**
	 * Logic to add an element
	 *
	 * @param elem
	 *
	 * @return
	 */
	fun add(elem: String): String? {
		if (!mData.contains(elem)) {
			mData.add(elem)
		}
		if (mData.size > size) {
			return mData.removeAt(0)
		}
		return null
	}

	/**
	 * Get an element
	 *
	 * @param idx
	 */
	fun get(idx: Int) {
		mData[idx]
	}

	/**
	 * convert this to a string to write to shared-pref
	 *
	 * @return
	 */
	fun toSharedPref(): String {
		return mData.joinToString("\n") { it }
	}

	/**
	 * convert this to a array of files
	 *
	 * @return
	 */
	fun toFiles(): Array<File> {
		return mData.map { File(it) }.toTypedArray()
	}

	/**
	 * convert from a string (shared-pref) to this
	 */
	fun fromSharedPref(raw: String) {
		mData = LinkedList()
		mData.addAll(raw.split("\n").filter { it.isNotEmpty() })
	}
}
