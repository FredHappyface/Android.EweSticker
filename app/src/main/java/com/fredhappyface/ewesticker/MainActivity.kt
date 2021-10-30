package com.fredhappyface.ewesticker

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors

/**
 * MainActivity class inherits from the AppCompatActivity class - provides the settings view
 */
class MainActivity : AppCompatActivity() {
	// init
	private val mSupportedMimes = Utils.getSupportedMimes()

	// onCreate
	private lateinit var mSharedPreferences: SharedPreferences
	private lateinit var mContextView: View

	/**
	 * Sets up content view, shared prefs, etc.
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		// Inflate view
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		// Set late-init attrs
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		mContextView = findViewById(R.id.activityMainRoot)
		refreshStickerDirPath()
		// Update UI with config
		seekBar(
			findViewById(R.id.iconsPerColumnSb),
			findViewById(R.id.iconsPerColumnLbl),
			"iconsPerColumn",
			3
		)
		seekBar(
			findViewById(R.id.iconSizeSb),
			findViewById(R.id.iconSizeLbl),
			"iconSize",
			80,
			20
		)
		toggle(findViewById(R.id.showBackButton), "showBackButton") { }
		toggle(findViewById(R.id.vertical), "vertical") { isChecked: Boolean ->
			findViewById<SeekBar>(R.id.iconsPerColumnSb).isEnabled = !isChecked
			findViewById<SeekBar>(R.id.iconSizeSb).isEnabled = !isChecked
		}
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to
	 * mSharedPreferences and resets recentCache, compatCache
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val editor = mSharedPreferences.edit()
				editor.putString("stickerDirPath", result.data?.data.toString())
				editor.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				editor.putString("recentCache", "")
				editor.putString("compatCache", "")
				editor.apply()
				refreshStickerDirPath()
				importStickers()
			}
		}

	/**
	 * Called on button press to choose a new directory
	 *
	 * @param view: View
	 */
	fun chooseDir(view: View) {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
		chooseDirResultLauncher.launch(intent)
	}

	/**
	 * Copies images from pack directory by calling importSticker() on all of them
	 *
	 * @param pack source pack
	 */
	private fun importPack(pack: DocumentFile): Int {
		var stickersInPack = 0
		for (sticker in pack.listFiles()) {
			stickersInPack += importSticker(sticker, pack.name + "/")
		}
		return stickersInPack
	}

	/**
	 * Copies stickers from source to internal storage
	 *
	 * @param sticker sticker to copy over
	 * @param pack    the pack which the sticker belongs to
	 *
	 * @return 1 if sticker imported successfully else 0
	 */
	private fun importSticker(sticker: DocumentFile, pack: String): Int {
		if (sticker.isDirectory || sticker.type !in mSupportedMimes) {
			return 0
		}
		val destSticker = File(filesDir, "stickers/" + pack + sticker.name)
		destSticker.parentFile?.mkdirs()
		try {
			val inputStream = contentResolver.openInputStream(sticker.uri)
			Files.copy(inputStream, destSticker.toPath())
			inputStream?.close()
		} catch (e: java.lang.Exception) {
		}
		return 1
	}

	/**
	 * Import files from storage to internal directory
	 */
	private fun importStickers() {
		// Use worker thread because this takes several seconds
		val executor = Executors.newSingleThreadExecutor()
		val handler = Handler(Looper.getMainLooper())
		Snackbar.make(
			mContextView,
			"Starting import. You will not be able to reselect directory until finished. This might take a bit!",
			Snackbar.LENGTH_LONG
		).show()
		val button = findViewById<Button>(R.id.updateStickerPackInfoBtn)
		button.isEnabled = false
		executor.execute {
			File(filesDir, "stickers").deleteRecursively()
			val stickerDirPath = mSharedPreferences.getString("stickerDirPath", "none set")
			var stickersInDir = 0
			for (file in DocumentFile.fromTreeUri(applicationContext, Uri.parse(stickerDirPath))
				?.listFiles() ?: arrayOf()) {
				if (file.isFile) stickersInDir += importSticker(file, "")
				if (file.isDirectory) stickersInDir += importPack(file)
			}
			handler.post {
				Snackbar.make(
					mContextView,
					"Imported $stickersInDir stickers. You may need to reload the keyboard for new stickers to show up.",
					Snackbar.LENGTH_LONG
				).show()
				val editor = mSharedPreferences.edit()
				editor.putInt("numStickersImported", stickersInDir)
				editor.apply()
				refreshStickerDirPath()
				button.isEnabled = true
			}
		}
	}

	/**
	 * Add toggle logic for each toggle/ checkbox in the layout
	 *
	 * @param compoundButton CompoundButton
	 * @param sharedPrefKey String - Id/Key of the SharedPreferences to update
	 * @param callback (Boolean) -> Unit - Add custom behaviour with a callback - for instance to
	 * disable some options
	 */
	private fun toggle(
		compoundButton: CompoundButton,
		sharedPrefKey: String,
		callback: (Boolean) -> Unit
	) {
		compoundButton.isChecked = mSharedPreferences.getBoolean(sharedPrefKey, false)
		callback(compoundButton.isChecked)
		compoundButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			callback(compoundButton.isChecked)
			val editor = mSharedPreferences.edit()
			editor.putBoolean(sharedPrefKey, isChecked)
			editor.apply()
		}
	}

	/**
	 * Add seekbar logic for each seekbar in the layout
	 *
	 * @param seekBar SeekBar
	 * @param seekBarLabel  TextView - the label with a value updated when the progress is changed
	 * @param sharedPrefKey String - Id/Key of the SharedPreferences to update
	 * @param sharedPrefDefault Int - default value
	 * @param multiplier Int - multiplier (used to update SharedPreferences and set the seekBarLabel)
	 */
	private fun seekBar(
		seekBar: SeekBar,
		seekBarLabel: TextView,
		sharedPrefKey: String,
		sharedPrefDefault: Int,
		multiplier: Int = 1
	) {
		seekBarLabel.text = mSharedPreferences.getInt(sharedPrefKey, sharedPrefDefault).toString()
		seekBar.progress = mSharedPreferences.getInt(sharedPrefKey, sharedPrefDefault) / multiplier
		seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
			var progressMultiplier = sharedPrefDefault
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				progressMultiplier = progress * multiplier
				seekBarLabel.text = progressMultiplier.toString()
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}
			override fun onStopTrackingTouch(seekBar: SeekBar) {
				val editor = mSharedPreferences.edit()
				editor.putInt(sharedPrefKey, progressMultiplier)
				editor.apply()
				showChangedPrefText()
			}
		})
	}

	/**
	 * Reads saved sticker dir path from preferences
	 */
	private fun refreshStickerDirPath() {
		val stickerDirPath = mSharedPreferences.getString("stickerDirPath", "none set")
		val lastUpdateDate = mSharedPreferences.getString("lastUpdateDate", "never")
		val numStickersImported = mSharedPreferences.getInt("numStickersImported", 0)
		findViewById<TextView>(R.id.stickerPackInfoPath).text = stickerDirPath
		findViewById<TextView>(R.id.stickerPackInfoDate).text = lastUpdateDate
		findViewById<TextView>(R.id.stickerPackInfoTotal).text = numStickersImported.toString()
	}

	/**
	 * Reusable function to warn about changing preferences
	 */
	internal fun showChangedPrefText() {
		Snackbar.make(
			mContextView,
			"Preferences changed. You may need to reload the keyboard for settings to apply.",
			Snackbar.LENGTH_SHORT
		).show()
	}
}
