package com.fredhappyface.ewesticker

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
	private val chooseStickerDir = 62519
	private val supportedMimes = Utils.getSupportedMimes()
	lateinit var sharedPreferences: SharedPreferences

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
	 * Called on button press to choose a new directory
	 *
	 * @param view: View
	 */
	fun chooseDir(view: View) {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
		startActivityForResult(intent, chooseStickerDir)
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
	 * @param pack source pack
	 */
	private fun importPack(pack: DocumentFile): Int {
		var stickersInPack = 0
		val stickers = pack.listFiles()
		for (sticker in stickers) {
			stickersInPack += importSticker(sticker, pack.name + "/")
		}
		return stickersInPack
	}

	/**
	 * Copies stickers from source to internal storage
	 *
	 * @param sticker sticker to copy over
	 * @param pack    the pack which the sticker belongs to
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
		} catch (e: IOException) {
			e.printStackTrace()
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
		Toast.makeText(
			applicationContext,
			"Starting import. You will not be able to reselect directory until finished. This might take a bit!",
			Toast.LENGTH_LONG
		).show()
		val button = findViewById<Button>(R.id.chooseStickerDir)
		button.isEnabled = false
		executor.execute {

			val oldStickers = File(filesDir, "stickers")
			deleteRecursive(oldStickers)
			var stickersInDir = 0
			val stickerDirPath = sharedPreferences.getString("stickerDirPath", "none set")
			val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(stickerDirPath))
			val files = tree!!.listFiles()
			for (file in files) {
				if (file.isFile) stickersInDir += importSticker(file, "")
				if (file.isDirectory) stickersInDir += importPack(file)
			}

			handler.post {
				Toast.makeText(
					applicationContext,
					"Imported $stickersInDir stickers. You may need to reload the keyboard for new stickers to show up.",
					Toast.LENGTH_LONG
				).show()
				val editor = sharedPreferences.edit()
				editor.putInt("numStickersImported", stickersInDir)
				editor.apply()
				refreshStickerDirPath()
				button.isEnabled = true
			}
		}
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds the returned Uri to shared prefs
	 *
	 * @param requestCode Int - RequestCode as defined under the Activity private vars
	 * @param resultCode  Int - The result code, we only want to do stuff if successful
	 * @param data        Intent? - Extra data in the form of an intent. tend to access .data
	 */
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == chooseStickerDir && resultCode == RESULT_OK) {
			if (data != null) {
				val editor = sharedPreferences.edit()
				editor.putString("stickerDirPath", data.data.toString())
				editor.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				editor.putString("recentCache", "")
				editor.putString("compatCache", "")
				editor.apply()
				refreshStickerDirPath()
				importStickers()
			}
		}
	}

	/**
	 * Sets up content view, shared prefs, etc.
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		refreshStickerDirPath()
		refreshKeyboardConfig()
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
		val iconsPerRowSeekBar = findViewById<SeekBar>(R.id.iconsPerRowSeekBar)
		iconsPerRowSeekBar.progress = sharedPreferences.getInt("iconsPerRow", 3)
		iconsPerRowSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
			var iconsPerRow = 3
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				iconsPerRow = progress
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}
			override fun onStopTrackingTouch(seekBar: SeekBar) {
				val editor = sharedPreferences.edit()
				editor.putInt("iconsPerRow", iconsPerRow)
				editor.apply()
				refreshKeyboardConfig()
				showChangedPrefText()
			}
		})
		val iconSizeSeekBar = findViewById<SeekBar>(R.id.iconSizeSeekBar)
		iconSizeSeekBar.progress = sharedPreferences.getInt("iconSize", 160) / 10
		iconSizeSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
			var iconSize = 160
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				iconSize = progress * 10
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}
			override fun onStopTrackingTouch(seekBar: SeekBar) {
				val editor = sharedPreferences.edit()
				editor.putInt("iconSize", iconSize)
				editor.apply()
				refreshKeyboardConfig()
				showChangedPrefText()
			}
		})
	}

	/**
	 * Refreshes config from preferences
	 */
	fun refreshKeyboardConfig() {
		val iconsPerRow = sharedPreferences.getInt("iconsPerRow", 3)
		val iconsPerRowValue = findViewById<TextView>(R.id.iconsPerRowValue)
		iconsPerRowValue.text = iconsPerRow.toString()
		val iconSize = sharedPreferences.getInt("iconSize", 160)
		val iconSizeValue = findViewById<TextView>(R.id.iconSizeValue)
		iconSizeValue.text = String.format("%dpx", iconSize)
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
	fun showChangedPrefText() {
		Toast.makeText(
			applicationContext,
			"Preferences changed. You may need to reload the keyboard for settings to apply.",
			Toast.LENGTH_LONG
		).show()
	}


}
