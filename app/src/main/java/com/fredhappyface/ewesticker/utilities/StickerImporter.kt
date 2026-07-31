package com.fredhappyface.ewesticker.utilities

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import com.elvishew.xlog.XLog
import com.fredhappyface.ewesticker.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_FILES = 4096
private const val MAX_PACK_SIZE = 128
private const val BUFFER_SIZE = 64 * 1024 // 64 KB
private const val MANIFEST_NAME = "import-manifest.tsv"

/**
 * How many files to copy at once. The previous code launched one coroutine per file, so a library of
 * several hundred stickers fanned out across every thread Dispatchers.IO would give it, all
 * contending on the same content provider.
 */
private const val COPY_PARALLELISM = 8

/** A file that should end up in the imported tree, and where it goes */
private class PlannedCopy(val source: SourceFile, val destination: File, val isVariant: Boolean)

/**
 * Imports the user's sticker directory into app storage.
 *
 * The import is incremental: it compares each source file against a manifest of what was copied last
 * time and only touches what actually changed, then removes anything that disappeared from the
 * source. Nothing is deleted up front, so the keyboard keeps working throughout and adding one pack
 * costs one pack's worth of work rather than a full rebuild of the library and every generated GIF.
 *
 * @property context: application baseContext
 * @property toaster: an instance of Toaster (used to store an error state for later reporting to the
 * user)
 * @property progressBar: LinearProgressIndicator that we update as we import stickers
 * @property generateGifVariants: generate GIF copies of animated stickers after importing, for apps
 * that accept image/gif but no webp mimetype
 */
class StickerImporter(
	private val context: Context,
	private val toaster: Toaster,
	private val progressBar: LinearProgressIndicator,
	private val generateGifVariants: Boolean = false,
) {
	private val supportedMimes = Utils.getSupportedMimes()
	private val mainHandler = Handler(Looper.getMainLooper())

	private val stickersDir = File(context.filesDir, "stickers")
	private val variantsDir = File(context.filesDir, "variants")
	private val manifestFile = File(context.filesDir, MANIFEST_NAME)

	/**
	 * Copy stickers from a user-selected directory into app storage.
	 *
	 * @param stickerDirPath a URI to the stickers directory to import into EweSticker
	 * @param fullRebuild discard everything already imported and copy the library again from scratch
	 * @return Int how many stickers the library now holds
	 */
	suspend fun importStickers(stickerDirPath: String, fullRebuild: Boolean = false): Int {
		withContext(Dispatchers.Main) {
			progressBar.visibility = View.VISIBLE
			progressBar.isIndeterminate = true
		}

		XLog.i("Listing $stickerDirPath...")
		val tree = withContext(Dispatchers.IO) {
			SourceListing.list(context, Uri.parse(stickerDirPath), MAX_FILES + 1)
		}
		val source = tree.files
		if (source.isEmpty()) {
			XLog.w("Found no files in the sticker directory")
			toaster.setMessage(context.getString(R.string.imported_034))
			withContext(Dispatchers.Main) { progressBar.visibility = View.GONE }
			return 0
		}

		// Deliberately after the listing. Wiping first and then discovering the source cannot be read
		// -- an unmounted card, a grant that went stale -- would destroy the library and the
		// device-generated GIFs, which no source can restore, in the one situation where the user
		// reached for this button precisely because something already looked wrong.
		if (fullRebuild) {
			XLog.i("Full rebuild requested, discarding the imported library")
			withContext(Dispatchers.IO) {
				stickersDir.deleteRecursively()
				variantsDir.deleteRecursively()
				manifestFile.delete()
			}
		}

		if (source.size > MAX_FILES) {
			XLog.w("Found more than $MAX_FILES stickers, notify user")
			toaster.setMessage(context.getString(R.string.imported_031, MAX_FILES))
		}

		val manifest = if (fullRebuild) ImportManifest.empty() else ImportManifest.load(manifestFile)
		val planned = plan(source.take(MAX_FILES))
		val stickerCount = planned.count { !it.isVariant }

		// On the first import after this manifest was introduced -- or after one was lost -- there is
		// no record of anything, so every file would look changed and be re-copied, and every
		// generated GIF would then be discarded as stale despite nothing having actually changed.
		// A destination already holding exactly as many bytes as the source is the file the previous
		// import wrote, so adopt it rather than doing that work again.
		if (!fullRebuild) {
			var adopted = 0
			for (copy in planned) {
				if (!manifest.matches(copy.source) &&
					copy.source.size > 0L &&
					copy.destination.isFile &&
					copy.destination.length() == copy.source.size
				) {
					manifest.record(copy.source)
					adopted++
				}
			}
			if (adopted > 0) {
				XLog.i("Adopted $adopted already-imported file(s) into the manifest")
			}
		}

		// Anything already present and unchanged is left alone. This is the whole point of the
		// manifest: on a re-import that added one pack, only that pack is copied.
		val outstanding = planned.filter { !isUpToDate(it, manifest) }
		XLog.i("Importing ${outstanding.size} of ${planned.size} file(s); the rest are unchanged")

		withContext(Dispatchers.Main) {
			progressBar.isIndeterminate = false
			progressBar.progress = 0
		}

		// Stickers whose content actually changed. Their variants are stale; everything else's are
		// not, regardless of what the copy did to file timestamps.
		val changed = outstanding.filter { !it.isVariant }.map { it.source.key }.toSet()

		val copied = try {
			copyAll(outstanding, manifest)
		} finally {
			// Losing the manifest because the user walked away mid-import would force a full re-copy
			// next time, which is the cost this whole change exists to remove.
			withContext(NonCancellable + Dispatchers.IO) { manifest.save(manifestFile) }
		}
		val removed = withContext(Dispatchers.IO) {
			prune(planned, changed, fullRebuild, tree.complete)
		}
		// Forget files the source no longer offers -- a sticker deleted from the keyboard, say --
		// otherwise the manifest only ever grows.
		if (tree.complete) {
			val present = planned.map { it.source.key }.toSet()
			manifest.keys().filterNot { it in present }.forEach { manifest.forget(it) }
		}
		withContext(NonCancellable + Dispatchers.IO) { manifest.save(manifestFile) }

		if (generateGifVariants) {
			GifVariantGenerator(context, toaster, progressBar).generate(stickersDir, variantsDir)
		}
		// Counted from disk rather than from the plan: most variants are generated on the device and
		// so never appear in the plan at all, which made a normal import report zero of them.
		val variants = withContext(Dispatchers.IO) {
			variantsDir.walkTopDown().count { it.isFile }
		}

		withContext(Dispatchers.Main) {
			progressBar.visibility = View.GONE
		}

		// What is actually there, rather than what was planned: copies can fail, and prune can decline
		// to run, either of which would make a planned count disagree with what the keyboard loads.
		val onDisk = withContext(Dispatchers.IO) {
			planned.count { !it.isVariant && it.destination.isFile }
		}
		XLog.i(
			"Library holds $onDisk of $stickerCount planned sticker(s) and $variants variant(s); " +
				"copied $copied, removed $removed",
		)
		return onDisk
	}

	/**
	 * Decide what belongs in the imported tree and where, rejecting unsupported files and enforcing
	 * the per-pack cap. Done in one pass up front so that the copy stage has no shared state to
	 * mutate, which is what made the previous concurrent version's counts unreliable.
	 */
	private fun plan(source: List<SourceFile>): List<PlannedCopy> {
		// A file that differs from a webp only by extension is an alternate encoding of that sticker
		// rather than a sticker of its own -- see tools/gif-variants.py.
		val webpKeys = source
			.filter { it.name.endsWith(".webp", ignoreCase = true) }
			.map { "${it.pack}/${it.baseName}" }
			.toSet()

		val planned = mutableListOf<PlannedCopy>()
		val packCounts = mutableMapOf<String, Int>()
		val overfullPacks = mutableSetOf<String>()

		for (file in source) {
			if (file.mimeType !in supportedMimes) {
				XLog.w("'${file.key}' is not a supported mimetype (${file.mimeType}), notify user")
				toaster.setMessage(
					context.getString(R.string.imported_033, file.mimeType, file.pack, file.name),
				)
				continue
			}

			// Only formats the sender actually looks for count as variants; diverting anything else
			// would hide it from the keyboard while nothing ever used it.
			val isVariant = file.name.endsWith(".gif", ignoreCase = true) &&
				"${file.pack}/${file.baseName}" in webpKeys

			if (!isVariant) {
				val count = packCounts.getOrDefault(file.pack, 0)
				if (count >= MAX_PACK_SIZE) {
					if (overfullPacks.add(file.pack)) {
						XLog.w("Found more than $MAX_PACK_SIZE stickers in '${file.pack}'")
						toaster.setMessage(
							context.getString(R.string.imported_032, MAX_PACK_SIZE, file.pack),
						)
					}
					continue
				}
				packCounts[file.pack] = count + 1
			}

			val tree = if (isVariant) variantsDir else stickersDir
			planned.add(PlannedCopy(file, File(tree, file.key), isVariant))
		}
		return planned
	}

	/** Already copied, unchanged at the source, and still present at the destination */
	private fun isUpToDate(copy: PlannedCopy, manifest: ImportManifest): Boolean =
		manifest.matches(copy.source) &&
			copy.destination.isFile &&
			copy.destination.length() == copy.source.size

	private suspend fun copyAll(
		outstanding: List<PlannedCopy>,
		manifest: ImportManifest,
	): Int {
		if (outstanding.isEmpty()) {
			return 0
		}
		val done = AtomicInteger()
		val copied = AtomicInteger()
		val gate = Semaphore(COPY_PARALLELISM)
		coroutineScope {
			outstanding.map { copy ->
				async(Dispatchers.IO) {
					gate.withPermit {
						if (copyOne(copy)) {
							copied.incrementAndGet()
							synchronized(manifest) { manifest.record(copy.source) }
						}
					}
					val finished = done.incrementAndGet()
					val percent = finished * 100 / outstanding.size
					mainHandler.post {
						// Posts from concurrent copies can arrive out of order, which made the bar
						// visibly jump backwards.
						progressBar.progress = maxOf(progressBar.progress, percent)
					}
				}
			}.awaitAll()
		}
		return copied.get()
	}

	private fun copyOne(copy: PlannedCopy): Boolean {
		copy.destination.parentFile?.mkdirs()
		// Into a temporary file and then renamed: writing the destination directly means a copy that
		// fails half way leaves a truncated file where a perfectly good sticker used to be.
		val partial = try {
			File.createTempFile("import-", ".part", copy.destination.parentFile)
		} catch (e: IOException) {
			XLog.e("Could not create a temporary file for '${copy.source.key}'")
			XLog.e(e)
			return false
		}
		return try {
			context.contentResolver.openInputStream(copy.source.uri)?.use { input ->
				input.buffered(BUFFER_SIZE).use { buffered ->
					partial.outputStream().buffered(BUFFER_SIZE).use { output ->
						buffered.copyTo(output)
					}
				}
			} ?: run {
				XLog.e("Could not open '${copy.source.key}'")
				return false
			}
			if (!partial.renameTo(copy.destination)) {
				XLog.e("Could not move '${copy.source.key}' into place")
				return false
			}
			true
		} catch (e: Throwable) {
			// A failure here used to cancel the enclosing scope, taking every other in-flight copy
			// with it and skipping both the prune and the manifest save.
			if (e is CancellationException) {
				throw e
			}
			XLog.e("Failed to copy '${copy.source.key}'")
			XLog.e(e)
			false
		} finally {
			partial.delete()
		}
	}

	/**
	 * Remove what the source no longer has.
	 *
	 * Stickers are matched against the plan. Variants are judged differently, because a GIF generated
	 * on the device has no counterpart in the source and must not be read as an orphan: one is dropped
	 * only when its sticker is gone, or when that sticker's content actually changed and a generation
	 * pass will follow to replace it.
	 *
	 * Note there is deliberately no timestamp comparison here. Copying rewrites a destination and
	 * moves its mtime forward, so "sticker newer than variant" is true for everything this run
	 * touched -- which on a first import, where nothing has a manifest entry yet, means every single
	 * variant.
	 *
	 * @param planned List<PlannedCopy> everything the source says should exist
	 * @param changedStickers Set<String> keys of stickers whose content this run replaced
	 * @param fullRebuild Boolean the user asked for a clean rebuild
	 * @param listingComplete Boolean whether the source walk read every directory
	 * @return Int how many files were removed
	 */
	private fun prune(
		planned: List<PlannedCopy>,
		changedStickers: Set<String>,
		fullRebuild: Boolean,
		listingComplete: Boolean,
	): Int {
		val wanted = planned.map { it.destination.absolutePath }.toSet()

		// A listing that could not read every directory understates the source, and is indistinguishable
		// from the user having deleted those packs.
		if (!listingComplete && !fullRebuild) {
			XLog.w("The source listing was incomplete, so nothing will be removed")
			toaster.setMessage(context.getString(R.string.imported_036))
			return 0
		}

		// Even a complete-looking listing can be wrong -- a stale grant, a provider returning an empty
		// cursor. Refuse to act on one that would throw away most of what is imported.
		val existing = stickersDir.walkTopDown()
			.filter { it.isFile && !it.absolutePath.contains("__compatSticker__") }
			.count()
		val survivors = planned.count { !it.isVariant }
		if (!fullRebuild && existing > 0 && survivors < existing / 2) {
			XLog.w(
				"Listing produced $survivors sticker(s) against $existing already imported; " +
					"refusing to prune in case the source could not be read properly",
			)
			toaster.setMessage(context.getString(R.string.imported_036))
			return 0
		}

		var removed = 0
		stickersDir.walkBottomUp().forEach { file ->
			if (file.isFile &&
				file.absolutePath !in wanted &&
				!file.absolutePath.contains("__compatSticker__")
			) {
				if (file.delete()) removed++
			} else if (file.isDirectory && file != stickersDir && file.listFiles()?.isEmpty() == true) {
				file.delete()
			}
		}

		variantsDir.walkBottomUp().forEach { file ->
			if (file.isFile) {
				// A variant the source supplied was just copied, so it is current by definition.
				if (file.absolutePath in wanted) {
					return@forEach
				}
				val pack = file.parentFile?.name ?: return@forEach
				val stickerKey = "$pack/${file.nameWithoutExtension}.webp"
				val sticker = File(stickersDir, stickerKey)
				if (!sticker.isFile) {
					// Orphan: whatever it was generated from is gone.
					if (file.delete()) removed++
				} else if (stickerKey in changedStickers && generateGifVariants) {
					// Stale, and only worth discarding because a generation pass will replace it.
					// Without that gate this would delete a usable GIF and leave nothing behind.
					if (file.delete()) removed++
				}
			} else if (file.isDirectory && file != variantsDir && file.listFiles()?.isEmpty() == true) {
				file.delete()
			}
		}
		return removed
	}
}
