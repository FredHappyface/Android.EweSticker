package com.fredhappyface.ewesticker

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.util.*
import java.util.concurrent.Executors


/** MainActivity class inherits from the AppCompatActivity class - provides the settings view */
class MainActivity : AppCompatActivity() {
	// onCreate
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var contextView: View
	private lateinit var toaster: Toaster

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
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.contextView = findViewById(R.id.activityMainRoot)
		this.toaster = Toaster(baseContext)
		refreshStickerDirPath()
		// Update UI with config
		seekBar(findViewById(R.id.iconsPerXSb), findViewById(R.id.iconsPerXLbl), "iconsPerX", 3)
		seekBar(findViewById(R.id.iconSizeSb), findViewById(R.id.iconSizeLbl), "iconSize", 80, 20)
		toggle(findViewById(R.id.showBackButton), "showBackButton", true) {}
		toggle(findViewById(R.id.vertical), "vertical") { isChecked: Boolean ->
			findViewById<SeekBar>(R.id.iconSizeSb).isEnabled = !isChecked
		}
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to
	 * this.sharedPreferences and resets recentCache, compatCache
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val editor = this.sharedPreferences.edit()
				val stickerDirPath = result.data?.data.toString()
				editor.putString("stickerDirPath", stickerDirPath)
				editor.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				editor.putString("recentCache", "")
				editor.putString("compatCache", "")
				editor.apply()
				refreshStickerDirPath()
				importStickers(stickerDirPath)
			}
		}

	/**
	 * Called on button press to launch settings
	 *
	 * @param view: View
	 */
	fun enableKeyboard(view: View) {
		val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
		startActivity(intent)
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

	/** Import files from storage to internal directory */
	private fun importStickers(stickerDirPath: String) {
		// Use worker thread because this takes several seconds
		val executor = Executors.newSingleThreadExecutor()
		val handler = Handler(Looper.getMainLooper())
		toaster.toast(
			getString(R.string.imported_010),
		)
		val button = findViewById<Button>(R.id.updateStickerPackInfoBtn)
		button.isEnabled = false
		executor.execute {
			val totalStickers =
				StickerImporter(baseContext, this.toaster).importStickers(
					stickerDirPath
				)
			handler.post {
				toaster.toastOnState(
					arrayOf(
						getString(R.string.imported_020, totalStickers),
						getString(R.string.imported_031, totalStickers),
						getString(R.string.imported_032, totalStickers),
						getString(R.string.imported_033, totalStickers),
					)
				)
				val editor = this.sharedPreferences.edit()
				editor.putInt("numStickersImported", totalStickers)
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
	 * @param sharedPrefDefault Boolean - default value (default=false)
	 * @param callback (Boolean) -> Unit - Add custom behaviour with a callback - for instance to
	 * disable some options
	 */
	private fun toggle(
		compoundButton: CompoundButton,
		sharedPrefKey: String,
		sharedPrefDefault: Boolean = false,
		callback: (Boolean) -> Unit
	) {
		compoundButton.isChecked =
			this.sharedPreferences.getBoolean(sharedPrefKey, sharedPrefDefault)
		callback(compoundButton.isChecked)
		compoundButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			callback(compoundButton.isChecked)
			val editor = this.sharedPreferences.edit()
			editor.putBoolean(sharedPrefKey, isChecked)
			editor.apply()
		}
	}

	/**
	 * Add seekbar logic for each seekbar in the layout
	 *
	 * @param seekBar SeekBar
	 * @param seekBarLabel TextView - the label with a value updated when the progress is changed
	 * @param sharedPrefKey String - Id/Key of the SharedPreferences to update
	 * @param sharedPrefDefault Int - default value
	 * @param multiplier Int - multiplier (used to update SharedPreferences and set the
	 * seekBarLabel)
	 */
	private fun seekBar(
		seekBar: SeekBar,
		seekBarLabel: TextView,
		sharedPrefKey: String,
		sharedPrefDefault: Int,
		multiplier: Int = 1
	) {
		seekBarLabel.text =
			this.sharedPreferences.getInt(sharedPrefKey, sharedPrefDefault).toString()
		seekBar.progress =
			this.sharedPreferences.getInt(sharedPrefKey, sharedPrefDefault) / multiplier
		seekBar.setOnSeekBarChangeListener(
			object : OnSeekBarChangeListener {
				var progressMultiplier = sharedPrefDefault
				override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
					progressMultiplier = progress * multiplier
					seekBarLabel.text = progressMultiplier.toString()
				}

				override fun onStartTrackingTouch(seekBar: SeekBar) {}
				override fun onStopTrackingTouch(seekBar: SeekBar) {
					val editor = sharedPreferences.edit()
					editor.putInt(sharedPrefKey, progressMultiplier)
					editor.apply()
					showChangedPrefText()
				}
			})
	}

	/** Reads saved sticker dir path from preferences */
	private fun refreshStickerDirPath() {
		findViewById<TextView>(R.id.stickerPackInfoPath).text =
			this.sharedPreferences.getString(
				"stickerDirPath", resources.getString(R.string.update_sticker_pack_info_path)
			)
		findViewById<TextView>(R.id.stickerPackInfoDate).text =
			this.sharedPreferences.getString(
				"lastUpdateDate", resources.getString(R.string.update_sticker_pack_info_date)
			)
		findViewById<TextView>(R.id.stickerPackInfoTotal).text =
			this.sharedPreferences.getInt("numStickersImported", 0).toString()
	}

	/** Reusable function to warn about changing preferences */
	internal fun showChangedPrefText() {
		this.toaster.toast(
			getString(R.string.pref_000)
		)
	}
}
