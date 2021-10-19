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

class MainActivity : AppCompatActivity() {
	// init
	private val mSupportedMimes = Utils.getSupportedMimes()

	// onCreate
	lateinit var mSharedPreferences: SharedPreferences
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
		seekBar(findViewById(R.id.iconSizeSb), findViewById(R.id.iconSizeLbl), "iconSize", 80, 20)

		toggle(findViewById(R.id.showBackButton), "showBackButton")

		val compoundButton = findViewById<CompoundButton>(R.id.vertical)
		val sharedPrefKey = "vertical"

		val isChecked = mSharedPreferences.getBoolean(sharedPrefKey, false)
		compoundButton.isChecked = isChecked
		findViewById<SeekBar>(R.id.iconsPerColumnSb).isEnabled = !isChecked
		findViewById<SeekBar>(R.id.iconSizeSb).isEnabled = !isChecked
		compoundButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			findViewById<SeekBar>(R.id.iconsPerColumnSb).isEnabled = !isChecked
			findViewById<SeekBar>(R.id.iconSizeSb).isEnabled = !isChecked
			val editor = mSharedPreferences.edit()
			editor.putBoolean(sharedPrefKey, isChecked)
			editor.apply()
		}


	}


	/**
	 * For each sticker, check if it is in a compatible file format with EweSticker
	 *
	 * @param sticker sticker to check compatibility with EweSticker for
	 * @return true if supported image type
	 */
	private fun canImportSticker(sticker: DocumentFile): Boolean {
		val mimesToCheck = ArrayList(mSupportedMimes.keys)
		return !(sticker.isDirectory ||
				!mimesToCheck.contains(sticker.name?.let { Utils.getFileExtension(it) }))
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds the returned Uri to shared prefs
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val data: Intent? = result.data
				val editor = mSharedPreferences.edit()
				editor.putString("stickerDirPath", data?.data.toString())
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
	 * Delete everything from input File
	 *
	 * @param fileOrDirectory File to start deleting from
	 */
	private fun deleteRecursive(fileOrDirectory: File) {
		if (fileOrDirectory.isDirectory) {
			for (child in Objects.requireNonNull(fileOrDirectory.listFiles())) {
				deleteRecursive(child)
			}
		}
		fileOrDirectory.delete()
	}

	/**
	 * Copies images from pack directory by calling importSticker() on all of them
	 *
	 * @throws java.io.IOException – if an I/O error occurs when reading or writing
	 * @throws SecurityException – In the case of the default provider, and a security manager is
	 * installed, the checkWrite method is invoked to check write access to the file. Where the
	 * REPLACE_EXISTING option is specified, the security manager's checkDelete method is invoked to
	 * check that an existing file can be deleted.
	 *
	 * @param pack source pack
	 */
	private fun importPack(pack: DocumentFile): Int {
		var stickersInPack = 0
		val stickers = pack.listFiles()
		try {
			for (sticker in stickers) {
				stickersInPack += importSticker(sticker, pack.name + "/")
			}
		} catch (e: Exception) {
			throw e
		}
		return stickersInPack
	}

	/**
	 * Copies stickers from source to internal storage
	 *
	 * @param sticker sticker to copy over
	 * @param pack    the pack which the sticker belongs to
	 *
	 * @throws java.io.IOException – if an I/O error occurs when reading or writing
	 * @throws SecurityException – In the case of the default provider, and a security manager is
	 * installed, the checkWrite method is invoked to check write access to the file. Where the
	 * REPLACE_EXISTING option is specified, the security manager's checkDelete method is invoked to
	 * check that an existing file can be deleted.
	 *
	 * @return 1 if sticker imported successfully else 0
	 */
	private fun importSticker(sticker: DocumentFile, pack: String): Int {
		if (!canImportSticker(sticker)) {
			return 0
		}
		val destSticker = File(filesDir, "stickers/" + pack + sticker.name)
		destSticker.parentFile?.mkdirs()
		try {
			val inputStream = contentResolver.openInputStream(sticker.uri)
			Files.copy(inputStream, destSticker.toPath())
			inputStream!!.close()
		} catch (e: java.lang.Exception) {
			when (e) {
				is java.nio.file.FileAlreadyExistsException, is java.nio.file.DirectoryNotEmptyException -> {
					throw java.io.IOException(e)
				}
				else -> {
					throw e
				}
			}
		}
		return 1
	}

	/**
	 * Import files from storage to internal directory
	 */
	private fun importStickers() {
		//Use worker thread because this takes several seconds
		val executor = Executors.newSingleThreadExecutor()
		val handler = Handler(Looper.getMainLooper())
		reportEvent(
			"Starting import. You will not be able to reselect directory until finished. This might take a bit!",
		)
		val button = findViewById<Button>(R.id.chooseStickerDir)
		button.isEnabled = false
		executor.execute {
			val oldStickers = File(filesDir, "stickers")
			deleteRecursive(oldStickers)
			var errorText = ""
			var error: java.lang.Exception? = null
			var stickersInDir = 0
			val stickerDirPath = mSharedPreferences.getString("stickerDirPath", "none set")
			val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(stickerDirPath))
			val files = tree!!.listFiles()
			try {
				for (file in files) {
					if (file.isFile) stickersInDir += importSticker(file, "")
					if (file.isDirectory) stickersInDir += importPack(file)
				}
			} catch (e: java.io.IOException) {
				errorText = "An IO Exception occurred (please contact the dev)"
				error = e
			} catch (e: SecurityException) {
				errorText = "A Security Exception occurred (please contact the dev)"
				error = e
			}

			handler.post {
				if (error != null) {
					reportEvent(errorText, error)
				} else {
					reportEvent(
						"Imported $stickersInDir stickers. You may need to reload the keyboard for new stickers to show up.",
					)
				}
				val editor = mSharedPreferences.edit()
				editor.putInt("numStickersImported", stickersInDir)
				editor.apply()
				refreshStickerDirPath()
				button.isEnabled = true
			}
		}
	}


	private fun toggle(compoundButton: CompoundButton, sharedPrefKey: String) {
		compoundButton.isChecked = mSharedPreferences.getBoolean(sharedPrefKey, false)
		compoundButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			val editor = mSharedPreferences.edit()
			editor.putBoolean(sharedPrefKey, isChecked)
			editor.apply()
		}
	}

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
	 * Rereads saved sticker dir path from preferences
	 */
	private fun refreshStickerDirPath() {
		val stickerDirPath = mSharedPreferences.getString("stickerDirPath", "none set")
		val lastUpdateDate = mSharedPreferences.getString("lastUpdateDate", "never")
		val numStickersImported = mSharedPreferences.getInt("numStickersImported", 0)
		val dirStatus = findViewById<TextView>(R.id.stickerDirStatus)
		dirStatus.text = String.format(
			"%s on %s with %d stickers loaded.",
			stickerDirPath,
			lastUpdateDate,
			numStickersImported
		)
	}

	/**
	 * Reusable function to warn about changing preferences
	 */
	internal fun showChangedPrefText() {
		reportEvent("Preferences changed. You may need to reload the keyboard for settings to apply.")
	}

	/**
	 * Function to log some event and report to the end user
	 */
	private fun reportEvent(eventInfo: String, exception: Exception? = null) {
		exception?.printStackTrace() // if an exception then print stack trace
		Snackbar.make(mContextView, eventInfo, Snackbar.LENGTH_SHORT).show()
	}
}
