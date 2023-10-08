package com.fredhappyface.ewesticker.utilities

import android.content.Context
import android.widget.Toast

/**
 * The Toaster class provides a simplified interface to android.widget.Toast. Pass in the
 * android.content.Context to the constructor and call the 'toast' function (others as below)
 * toaster.state keeps track of an error state or similar.
 *
 * @property context: android.content.Context. e.g. baseContext
 */
class Toaster(private val context: Context) {
	private var state = 0

	/**
	 * Call toaster.toast with some string to always create a toast notification. Context is set when
	 * Toaster is instantiated. Duration is determined based on text length
	 *
	 * @param string: String. Message to output
	 */
	fun toast(string: String) {
		Toast.makeText(
			this.context,
			string,
			if (string.length > 60) {
				Toast.LENGTH_LONG
			} else {
				Toast.LENGTH_SHORT
			},
		)
			.show()
	}

	/**
	 * Call toaster.toastOnState with an array of messages to create a toast notification.
	 * Context is set when Toaster is instantiated. Duration is determined based on
	 * text length. The message is selected based on the state (which can be set in a callback
	 * function or elsewhere
	 *
	 * @param strings: Array<String>. Array of potential messages to output.
	 */
	fun toastOnState(strings: Array<String>) {
		if (this.state < strings.size) {
			this.toast(strings[this.state])
		} else {
			this.toast("toaster.state=${this.state} out of range strings.size=${strings.size}")
		}
	}

	/**
	 * Set the state to some integer value
	 *
	 * @param state: Int
	 */
	fun setState(state: Int) {
		if (state < 0) {
			this.state = 0
		} else {
			this.state = state
		}
	}
}
