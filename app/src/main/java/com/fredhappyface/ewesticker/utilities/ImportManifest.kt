package com.fredhappyface.ewesticker.utilities

import com.elvishew.xlog.XLog
import java.io.File
import java.io.IOException

/** Separator between the three fields of a manifest line; a tab cannot appear in a document id */
private const val FIELD_SEPARATOR = '\t'

/**
 * What the last import copied, so the next one can tell an unchanged file from a changed one.
 *
 * Without this the importer has nothing to compare against and has to re-copy the whole library
 * every time, which is what made adding a single pack cost a full rebuild.
 *
 * @property entries Map<String, Entry> keyed by "pack/filename"
 */
class ImportManifest private constructor(
	private val entries: MutableMap<String, Entry>,
) {
	data class Entry(val size: Long, val modified: Long)

	/** Has this file been imported already, unchanged since? */
	fun matches(file: SourceFile): Boolean {
		// Some providers report nothing for size or last-modified. Treating the resulting 0 as a
		// value would let every such file look unchanged forever, so treat it as unknown instead.
		if (file.size <= 0L || file.modified <= 0L) {
			return false
		}
		val entry = entries[file.key] ?: return false
		return entry.size == file.size && entry.modified == file.modified
	}

	fun record(file: SourceFile) {
		entries[file.key] = Entry(file.size, file.modified)
	}

	fun forget(key: String) {
		entries.remove(key)
	}

	fun keys(): Set<String> = entries.keys.toSet()

	/**
	 * Persist. Written to a temporary file and renamed so that an import interrupted while saving
	 * leaves the previous manifest intact rather than a truncated one, which would silently force a
	 * full re-copy next time.
	 */
	fun save(file: File) {
		try {
			val temporary = File(file.parentFile, "${file.name}.tmp")
			temporary.bufferedWriter().use { writer ->
				for ((key, entry) in entries) {
					writer.write(key)
					writer.write(FIELD_SEPARATOR.code)
					writer.write(entry.size.toString())
					writer.write(FIELD_SEPARATOR.code)
					writer.write(entry.modified.toString())
					writer.newLine()
				}
			}
			if (!temporary.renameTo(file)) {
				XLog.e("Could not move the import manifest into place")
				temporary.delete()
			}
		} catch (e: IOException) {
			XLog.e("Could not save the import manifest")
			XLog.e(e)
		}
	}

	companion object {
		/** Load, treating anything unreadable or malformed as "nothing imported yet" */
		fun load(file: File): ImportManifest {
			val entries = mutableMapOf<String, Entry>()
			if (!file.isFile) {
				return ImportManifest(entries)
			}
			try {
				file.forEachLine { line ->
					val parts = line.split(FIELD_SEPARATOR)
					if (parts.size == 3) {
						val size = parts[1].toLongOrNull()
						val modified = parts[2].toLongOrNull()
						if (size != null && modified != null) {
							entries[parts[0]] = Entry(size, modified)
						}
					}
				}
			} catch (e: IOException) {
				XLog.e("Could not read the import manifest, treating the library as unimported")
				XLog.e(e)
				entries.clear()
			}
			return ImportManifest(entries)
		}

		fun empty(): ImportManifest = ImportManifest(mutableMapOf())
	}
}
