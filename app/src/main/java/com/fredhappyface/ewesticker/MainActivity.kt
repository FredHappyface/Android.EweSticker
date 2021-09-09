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
	private val supportedMimes = Utils.getSupportedMimes()

	// late-init
	lateinit var sharedPreferences: SharedPreferences
	private lateinit var contextView: View
	private lateinit var iconsPerColumnValue: TextView
	private lateinit var iconSizeValue: TextView

	/**
	 * For each sticker, check if it is in a compatible file format with EweSticker
	 *
	 * @param sticker sticker to check compatibility with EweSticker for
	 * @return true if supported image type
	 */
	private fun canImportSticker(sticker: DocumentFile): Boolean {
		val mimesToCheck = ArrayList(supportedMimes.keys)
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
				val editor = sharedPreferences.edit()
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
			val stickerDirPath = sharedPreferences.getString("stickerDirPath", "none set")
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
				val editor = sharedPreferences.edit()
				editor.putInt("numStickersImported", stickersInDir)
				editor.apply()
				refreshStickerDirPath()
				button.isEnabled = true
			}
		}
	}


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
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		contextView = findViewById(R.id.activityMainRoot)
		iconsPerColumnValue = findViewById(R.id.iconsPerColumnValue)
		iconSizeValue = findViewById(R.id.iconSizeValue)
		refreshStickerDirPath()

		// Update UI with config
		iconsPerColumnValue.text = sharedPreferences.getInt("iconsPerColumn", 3).toString()
		iconSizeValue.text = String.format("%ddp", sharedPreferences.getInt("iconSize", 80))


		val backButtonToggle = findViewById<CompoundButton>(R.id.backButtonToggle)
		backButtonToggle.isChecked = sharedPreferences.getBoolean("showBackButton", false)
		backButtonToggle.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			val editor = sharedPreferences.edit()
			editor.putBoolean("showBackButton", isChecked)
			editor.apply()
		}
		val disableAnimations = findViewById<CompoundButton>(R.id.disableAnimations)
		disableAnimations.isChecked = sharedPreferences.getBoolean("disableAnimations", false)
		disableAnimations.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			val editor = sharedPreferences.edit()
			editor.putBoolean("disableAnimations", isChecked)
			editor.apply()
		}
		val iconsPerColumnSeekBar = findViewById<SeekBar>(R.id.iconsPerColumnSeekBar)
		iconsPerColumnSeekBar.progress = sharedPreferences.getInt("iconsPerColumn", 3)
		iconsPerColumnSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
			var iconsPerColumn = 3
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				iconsPerColumn = progress
				iconsPerColumnValue.text = iconsPerColumn.toString()
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}
			override fun onStopTrackingTouch(seekBar: SeekBar) {
				val editor = sharedPreferences.edit()
				editor.putInt("iconsPerColumn", iconsPerColumn)
				editor.apply()
				showChangedPrefText()
			}
		})
		val iconSizeSeekBar = findViewById<SeekBar>(R.id.iconSizeSeekBar)
		iconSizeSeekBar.progress = sharedPreferences.getInt("iconSize", 80) / 20
		iconSizeSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
			var iconSize = 80
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				iconSize = progress * 20
				iconSizeValue.text = String.format("%ddp", iconSize)
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}
			override fun onStopTrackingTouch(seekBar: SeekBar) {
				val editor = sharedPreferences.edit()
				editor.putInt("iconSize", iconSize)
				editor.apply()
				showChangedPrefText()

			}
		})
	}


	/**
	 * Rereads saved sticker dir path from preferences
	 */
	private fun refreshStickerDirPath() {
		val stickerDirPath = sharedPreferences.getString("stickerDirPath", "none set")
		val lastUpdateDate = sharedPreferences.getString("lastUpdateDate", "never")
		val numStickersImported = sharedPreferences.getInt("numStickersImported", 0)
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
		Snackbar.make(contextView, eventInfo, Snackbar.LENGTH_SHORT).show()
	}

}
