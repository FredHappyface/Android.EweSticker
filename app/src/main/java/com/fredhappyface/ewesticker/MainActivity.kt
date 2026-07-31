package com.fredhappyface.ewesticker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.fredhappyface.ewesticker.utilities.GifVariantGenerator
import com.fredhappyface.ewesticker.utilities.LibraryScanner
import com.fredhappyface.ewesticker.utilities.StickerImporter
import com.fredhappyface.ewesticker.utilities.Toaster
import com.fredhappyface.ewesticker.utilities.startLogger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.noties.markwon.Markwon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** MainActivity class inherits from the AppCompatActivity class - provides the settings view */
class MainActivity : AppCompatActivity() {
	// onCreate
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var backupSharedPreferences: SharedPreferences
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

		startLogger(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:$localClassName")

		val markwon: Markwon = Markwon.create(this)
		val featuresText = findViewById<TextView>(R.id.features_text)
		markwon.setMarkdown(featuresText, getString(R.string.features_text))

		val linksText = findViewById<TextView>(R.id.links_text)
		markwon.setMarkdown(linksText, getString(R.string.links_text))

		// Set late-init attrs
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.backupSharedPreferences =
			this.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

		XLog.i("Loading private shared preferences: ${this.sharedPreferences.all}")
		XLog.i("Loading backup shared preferences: ${this.backupSharedPreferences.all}")
		this.contextView = findViewById(R.id.activityMainRoot)
		this.toaster = Toaster(baseContext)
		refreshStickerDirPath()
		// Update UI with config
		seekBar(findViewById(R.id.iconsPerXSb), findViewById(R.id.iconsPerXLbl), "iconsPerX", 3)
		seekBar(findViewById(R.id.iconSizeSb), findViewById(R.id.iconSizeLbl), "iconSize", 80, 20)
		toggle(findViewById(R.id.showBackButton), "showBackButton", true) {}
		toggle(findViewById(R.id.showSearchButton), "showSearchButton", true) {}
		toggle(findViewById(R.id.vibrate), "vibrate", true) {}
		toggle(findViewById(R.id.vertical), "vertical") { isChecked: Boolean ->
			findViewById<SeekBar>(R.id.iconSizeSb).isEnabled = !isChecked
		}
		toggle(findViewById(R.id.restoreOnClose), "restoreOnClose", false) {}
		toggle(findViewById(R.id.continuousScroll), "continuousScroll", true) {}
		toggle(findViewById(R.id.scroll), "scroll", false) {}
		toggle(findViewById(R.id.insensitive_sort), "insensitiveSort", false) {}
		toggle(findViewById(R.id.pngFallback), "isPngFallback", true) {}
		toggle(findViewById(R.id.animateGrid), "animateGrid", false) {}
		toggle(findViewById(R.id.generateGifs), "generateGifVariants", false) {}

		val versionText: TextView = findViewById(R.id.versionText)
		var version = getString(R.string.version_text)
		try {
			val packageInfo = packageManager.getPackageInfo(packageName, 0)
			version = packageInfo.versionName ?: version
		} catch (_: PackageManager.NameNotFoundException) {
		}

		versionText.text = version
		XLog.i("Version: $version")

	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to
	 * this.sharedPreferences and resets recentCache, compatCache
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val editor = sharedPreferences.edit()
				val uri = result.data?.data
				val stickerDirPath = result.data?.data.toString()
				val contentResolver = applicationContext.contentResolver

				val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
					Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				if (uri != null) {
					contentResolver.takePersistableUriPermission(uri, takeFlags)
				}

				editor.putString("stickerDirPath", stickerDirPath)
				editor.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				editor.putString("recentCache", "")
				editor.putString("compatCache", "")
				editor.apply()
				refreshStickerDirPath()
				importStickers(stickerDirPath)
			}
		}

	private val saveFileLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == RESULT_OK) {
				result.data?.data?.also { uri ->
					val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
					val currentDate = Date()
					val logFileName = dateFormatter.format(currentDate)
					val file = File(filesDir, "logs/$logFileName")
					if (file.exists()) {
						contentResolver.openOutputStream(uri)?.use { outputStream ->
							file.inputStream().use { inputStream ->
								inputStream.copyTo(outputStream)
							}
						}
					}
				}
			}
		}

	/**
	 * Called on button press to launch settings
	 *
	 * @param ignoredView: View
	 */
	fun enableKeyboard(ignoredView: View) {
		val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
		startActivity(intent)
	}

	/**
	 * Called on button press to choose a new directory
	 *
	 * @param ignoredView: View
	 */
	fun chooseDir(ignoredView: View) {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
		chooseDirResultLauncher.launch(intent)
	}

	/**
	 * Called on button press to save logs
	 *
	 * @param ignoredView: View
	 */
	fun saveLogs(ignoredView: View) {
		val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
			addCategory(Intent.CATEGORY_OPENABLE)
			type = "text/plain"
			putExtra(Intent.EXTRA_TITLE, "ewesticker.log")
		}
		saveFileLauncher.launch(saveIntent)
	}

	/**
	 * reloadStickers
	 *
	 * Call this function when a user taps the reload stickers button. If we have a set stickerDirPath, call importStickers()
	 *
	 * @param ignoredView: View
	 */
	/**
	 * The library operation currently running, if any. Importing, generating and deleting all walk
	 * the same two trees, so exactly one of them may run at a time: a delete landing in the middle of
	 * a generation wipes files it is still writing, and a second generation started from a re-enabled
	 * button converts everything twice into the same place.
	 */
	private var libraryJob: Job? = null

	/** The newest stats scan, so a slow earlier one cannot paint over a newer result */
	private var scanJob: Job? = null

	private val libraryBusy: Boolean
		get() = libraryJob?.isActive == true

	/** Every control that mutates or reports on the library, so busy state is applied in one place */
	private fun libraryButtons(): List<Button> = listOfNotNull(
		findViewById(R.id.updateStickerPackInfoBtn),
		findViewById(R.id.reloadStickerPackInfoBtn),
		findViewById(R.id.fullReimportBtn),
		findViewById(R.id.generateVariantsBtn),
		findViewById(R.id.deleteVariantsBtn),
	)

	/**
	 * Run a library operation, with every button disabled for its duration and restored afterwards
	 * whatever happens. Refuses to start a second one.
	 *
	 * @param progressBar LinearProgressIndicator? shown while the work runs
	 * @param work suspend () -> Unit the operation itself
	 */
	private fun runLibraryOperation(
		progressBar: LinearProgressIndicator?,
		work: suspend () -> Unit,
	) {
		if (libraryBusy) {
			XLog.i("A library operation is already running, ignoring this one")
			return
		}
		libraryButtons().forEach { it.isEnabled = false }
		progressBar?.visibility = View.VISIBLE
		libraryJob = lifecycleScope.launch(Dispatchers.IO) {
			try {
				work()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				// Without this the exception reaches the uncaught handler and takes the app with it,
				// leaving the buttons disabled and the progress bar spinning on the way out.
				XLog.e("A library operation failed")
				XLog.e(e)
				withContext(Dispatchers.Main) { toaster.toast(getString(R.string.library_failed)) }
			} finally {
				withContext(NonCancellable + Dispatchers.Main) {
					// Cleared first: a job is still active inside its own finally, so leaving this set
					// would make the refresh below treat the library as busy and keep every button off.
					libraryJob = null
					progressBar?.visibility = View.GONE
					refreshLibraryStats()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		refreshLibraryStats()
	}

	/** Re-measure the library and redraw the Library card */
	private fun refreshLibraryStats() {
		val summary = findViewById<TextView>(R.id.librarySummary)
		val animatedLine = findViewById<TextView>(R.id.libraryAnimated)
		val coverage = findViewById<TextView>(R.id.libraryCoverage)
		val generate = findViewById<Button>(R.id.generateVariantsBtn)
		val delete = findViewById<Button>(R.id.deleteVariantsBtn)

		// Every button, not just the two below: runLibraryOperation disables all of them, so this is
		// the single place that puts them back.
		val busy = libraryBusy
		findViewById<Button>(R.id.updateStickerPackInfoBtn).isEnabled = !busy
		findViewById<Button>(R.id.reloadStickerPackInfoBtn).isEnabled = !busy
		findViewById<Button>(R.id.fullReimportBtn).isEnabled = !busy

		// Cancel any scan still running: on a slow library an older one can finish after a newer one
		// and repaint the card with counts that are already out of date.
		scanJob?.cancel()
		scanJob = lifecycleScope.launch(Dispatchers.IO) {
			// Stats every sticker and sniffs each one's header, so not on the main thread.
			val stats = try {
				LibraryScanner.scan(filesDir)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				XLog.e("Could not read the library")
				XLog.e(e)
				null
			}
			withContext(Dispatchers.Main) {
				if (stats == null) {
					summary.text = getString(R.string.library_failed)
					return@withContext
				}
				if (stats.isEmpty) {
					summary.text = getString(R.string.library_empty)
					animatedLine.text = ""
					coverage.text = ""
					generate.isEnabled = false
					delete.isEnabled = false
					return@withContext
				}
				summary.text = getString(
					R.string.library_summary,
					stats.stickers,
					stats.packs,
					LibraryScanner.formatBytes(stats.stickerBytes),
				)
				animatedLine.text = getString(
					R.string.library_animated,
					stats.animated,
					stats.withVariant,
					LibraryScanner.formatBytes(stats.variantBytes),
				)
				coverage.text = if (stats.missingVariants > 0) {
					resources.getQuantityString(
						R.plurals.library_missing,
						stats.missingVariants,
						stats.missingVariants,
					)
				} else if (stats.animated > 0) {
					getString(R.string.library_all_covered)
				} else {
					""
				}
				// Enablement has to account for work in flight, not just what is on disk: coming back
				// to this screen mid-generation would otherwise offer both buttons again.
				generate.isEnabled = stats.missingVariants > 0 && !libraryBusy
				delete.isEnabled = stats.variantBytes > 0 && !libraryBusy
			}
		}
	}

	/**
	 * Generate GIF versions for animated stickers that do not have one.
	 *
	 * Reachable on its own rather than only as a side effect of importing, which used to mean
	 * re-copying the whole library just to fill in a few missing GIFs.
	 *
	 * @param ignoredView: View
	 */
	fun generateVariants(ignoredView: View) {
		val progressBar = findViewById<LinearProgressIndicator>(R.id.libraryProgressIndicator)
		runLibraryOperation(progressBar) {
			val result = GifVariantGenerator(baseContext, toaster, progressBar).generate(
				File(filesDir, "stickers"),
				File(filesDir, "variants"),
			)
			withContext(Dispatchers.Main) {
				// Three outcomes, not two: a run where every conversion failed used to report that
				// everything already had a GIF version, directly contradicting the card above it.
				toaster.toast(
					when {
						result.pending == 0 -> getString(R.string.library_nothing_to_generate)
						result.written == 0 -> getString(R.string.library_generate_failed)
						result.written < result.pending -> getString(
							R.string.library_generated_partial,
							result.written,
							result.pending,
						)
						else -> getString(R.string.library_generated, result.written)
					},
				)
				bumpLibraryVersion()
			}
		}
	}

	/**
	 * Delete every generated GIF version, after saying what that costs.
	 *
	 * @param ignoredView: View
	 */
	fun deleteVariants(ignoredView: View) {
		if (libraryBusy) {
			return
		}
		// Disabled straight away: raising the dialog is asynchronous, so two quick taps used to stack
		// two of them, the second quoting space the first had already freed.
		findViewById<Button>(R.id.deleteVariantsBtn).isEnabled = false
		lifecycleScope.launch(Dispatchers.IO) {
			val stats = LibraryScanner.scan(filesDir)
			withContext(Dispatchers.Main) {
				if (isFinishing || isDestroyed) {
					return@withContext
				}
				MaterialAlertDialogBuilder(this@MainActivity)
					.setTitle(R.string.library_delete_title)
					.setMessage(
						getString(
							R.string.library_delete_message,
							LibraryScanner.formatBytes(stats.variantBytes),
						),
					)
					.setNegativeButton(android.R.string.cancel) { _, _ -> refreshLibraryStats() }
					.setOnCancelListener { refreshLibraryStats() }
					.setPositiveButton(R.string.library_delete_confirm) { _, _ ->
						runLibraryOperation(null) {
							val bytes = LibraryScanner.deleteVariants(filesDir)
							withContext(Dispatchers.Main) {
								toaster.toast(
									getString(
										R.string.library_deleted,
										LibraryScanner.formatBytes(bytes),
									),
								)
								bumpLibraryVersion()
							}
						}
					}
					.show()
			}
		}
	}

	/**
	 * Rebuild the library from scratch, discarding what was imported before.
	 *
	 * The normal import only touches what changed, which is what makes adding a pack quick. This is
	 * the way back when the imported copy is suspected of being wrong rather than merely stale.
	 *
	 * @param ignoredView: View
	 */
	fun fullReimport(ignoredView: View) {
		val stickerDirPath = this.sharedPreferences.getString("stickerDirPath", null)
		if (stickerDirPath != null) {
			importStickers(stickerDirPath, fullRebuild = true)
		} else {
			this.toaster.toast(getString(R.string.imported_034))
		}
	}

	fun reloadStickers(ignoredView: View) {
		val stickerDirPath = this.sharedPreferences.getString(
			"stickerDirPath",
			null,
		)
		if (stickerDirPath != null) {
			importStickers(stickerDirPath)
		} else {
			this.toaster.toast(
				getString(R.string.imported_034),
			)
		}
	}

	/** Import files from storage to internal directory */
	private fun importStickers(stickerDirPath: String, fullRebuild: Boolean = false) {
		toaster.toast(getString(R.string.imported_010))
		val progressBar = findViewById<LinearProgressIndicator>(R.id.linearProgressIndicator)
		runLibraryOperation(progressBar) {
			val totalStickers =
				StickerImporter(
					baseContext,
					toaster,
					progressBar,
					backupSharedPreferences.getBoolean("generateGifVariants", false),
				).importStickers(stickerDirPath, fullRebuild)

			withContext(Dispatchers.Main) {
				if (toaster.messages.size > 0) {
					toaster.toastOnMessages()
				} else {
					toaster.toast(getString(R.string.imported_020, totalStickers))
				}

				val editor = sharedPreferences.edit()
				editor.putInt("numStickersImported", totalStickers)
				editor.apply()
				bumpLibraryVersion()
				refreshStickerDirPath()
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
		callback: (Boolean) -> Unit,
	) {
		compoundButton.isChecked =
			this.backupSharedPreferences.getBoolean(sharedPrefKey, sharedPrefDefault)
		callback(compoundButton.isChecked)
		compoundButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			callback(compoundButton.isChecked)
			val editor = this.backupSharedPreferences.edit()
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
		multiplier: Int = 1,
	) {
		seekBarLabel.text =
			this.backupSharedPreferences.getInt(sharedPrefKey, sharedPrefDefault).toString()
		seekBar.progress =
			this.backupSharedPreferences.getInt(sharedPrefKey, sharedPrefDefault) / multiplier
		seekBar.setOnSeekBarChangeListener(
			object : OnSeekBarChangeListener {
				// Seeded from what is stored, not the default: onProgressChanged only fires when the
				// value actually moves, so a touch that does not move it used to save the default.
				var progressMultiplier =
					backupSharedPreferences.getInt(sharedPrefKey, sharedPrefDefault)
				override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
					progressMultiplier = progress * multiplier
					seekBarLabel.text = progressMultiplier.toString()
				}

				override fun onStartTrackingTouch(seekBar: SeekBar) {}
				override fun onStopTrackingTouch(seekBar: SeekBar) {
					val editor = backupSharedPreferences.edit()
					editor.putInt(sharedPrefKey, progressMultiplier)
					editor.apply()
					showChangedPrefText()
				}
			},
		)
	}

	/** Reads saved sticker dir path from preferences */
	private fun refreshStickerDirPath() {
		findViewById<TextView>(R.id.stickerPackInfoPath).text =
			this.sharedPreferences.getString(
				"stickerDirPath", resources.getString(R.string.update_sticker_pack_info_path),
			)
		findViewById<TextView>(R.id.stickerPackInfoDate).text =
			this.sharedPreferences.getString(
				"lastUpdateDate", resources.getString(R.string.update_sticker_pack_info_date),
			)
		findViewById<TextView>(R.id.stickerPackInfoTotal).text =
			this.sharedPreferences.getInt("numStickersImported", 0).toString()
	}

	/** Reusable function to warn about changing preferences */
	private fun showChangedPrefText() {
		bumpLibraryVersion()
		this.toaster.toast(
			getString(R.string.pref_000),
		)
	}

	/**
	 * Record that something the keyboard reads has changed. A running keyboard compares this against
	 * the generation it applied and reloads on its next input field, so nothing here needs the user
	 * to restart the keyboard by hand.
	 */
	private fun bumpLibraryVersion() {
		val editor = this.backupSharedPreferences.edit()
		editor.putInt(
			"libraryVersion",
			this.backupSharedPreferences.getInt("libraryVersion", 0) + 1,
		)
		editor.apply()
	}
}
