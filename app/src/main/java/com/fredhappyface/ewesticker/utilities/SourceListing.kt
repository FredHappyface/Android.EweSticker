package com.fredhappyface.ewesticker.utilities

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.elvishew.xlog.XLog

/**
 * One file in the user's sticker directory, with the metadata needed to tell an unchanged file from
 * a changed one.
 *
 * @property pack String name of the directory the file sits in
 * @property size Long size in bytes as reported by the provider
 * @property modified Long last-modified time in milliseconds as reported by the provider
 */
data class SourceFile(
	val pack: String,
	val name: String,
	val mimeType: String?,
	val size: Long,
	val modified: Long,
	val uri: Uri,
) {
	/** Identifies this file across imports, and matches the layout of the imported tree */
	val key: String
		get() = "$pack/$name"

	val baseName: String
		get() = name.substringBeforeLast('.')
}

/**
 * The outcome of walking a source tree.
 *
 * @property files List<SourceFile> everything found
 * @property complete Boolean false if any directory could not be read, in which case the listing
 * understates the source and must not be used to decide what to delete
 */
data class SourceTree(val files: List<SourceFile>, val complete: Boolean)

/**
 * Lists a SAF tree with one query per directory.
 *
 * DocumentFile caches nothing: listFiles() projects only the document id, and every subsequent
 * name/type/isFile/length/lastModified read is its own ContentResolver round trip. Walking a library
 * of a few hundred stickers that way costs thousands of Binder calls and still does not yield the
 * size and modification time an incremental import has to compare against, which this does in the
 * same pass.
 */
object SourceListing {
	private val COLUMNS = arrayOf(
		DocumentsContract.Document.COLUMN_DOCUMENT_ID,
		DocumentsContract.Document.COLUMN_DISPLAY_NAME,
		DocumentsContract.Document.COLUMN_MIME_TYPE,
		DocumentsContract.Document.COLUMN_SIZE,
		DocumentsContract.Document.COLUMN_LAST_MODIFIED,
	)

	/**
	 * Walk every file under a tree uri, breadth first.
	 *
	 * @param context Context used for its ContentResolver
	 * @param treeUri Uri the persisted tree the user granted
	 * @param limit Int stop after this many files
	 * @return SourceTree every file found, and whether the walk was complete
	 */
	fun list(context: Context, treeUri: Uri, limit: Int): SourceTree {
		val resolver = context.contentResolver
		val rootId = try {
			DocumentsContract.getTreeDocumentId(treeUri)
		} catch (e: IllegalArgumentException) {
			XLog.e("'$treeUri' is not a tree uri")
			XLog.e(e)
			return SourceTree(emptyList(), complete = false)
		}

		val results = mutableListOf<SourceFile>()
		var complete = true
		// Files sitting directly in the chosen folder belong to a pack named after that folder,
		// which is what the DocumentFile walk this replaces produced.
		val queue = ArrayDeque<Pair<String, String>>()
		queue.addLast(rootId to displayName(context, treeUri, rootId))

		while (queue.isNotEmpty() && results.size < limit) {
			val (documentId, pack) = queue.removeFirst()
			val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
			try {
				resolver.query(children, COLUMNS, null, null, null)?.use { cursor ->
					while (cursor.moveToNext() && results.size < limit) {
						val id = cursor.getString(0) ?: continue
						val name = cursor.getString(1) ?: continue
						// Hidden entries are never stickers. A '.nomedia' is how the sticker folder
						// is kept out of the gallery, and without this it is rejected as an
						// unsupported mimetype and toasts on every import; a hidden directory such
						// as '.thumbnails' would import as a pack in its own right.
						//
						// Filtering here rather than in StickerImporter.plan also keeps these out
						// of the MAX_FILES count, the manifest and pruning. It does not open a
						// deletion path: deletePackIfEmpty corroborates with settledEmpty, which
						// counts raw cursor rows, so a directory holding only a hidden file still
						// reads as non-empty and is never removed.
						if (name.startsWith(".")) continue
						val mime = cursor.getString(2)
						if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
							queue.addLast(id to name)
						} else {
							results.add(
								SourceFile(
									pack = pack,
									name = name,
									mimeType = mime,
									size = cursor.getLong(3),
									modified = cursor.getLong(4),
									uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
								),
							)
						}
					}
				}
			} catch (e: Exception) {
				// A provider can fail on an individual directory. Carrying on gives the user what we
				// could read, but the caller has to know the listing is short: taken at face value it
				// looks exactly like the user having deleted that whole pack.
				XLog.e("Could not list '$documentId', the listing is incomplete")
				XLog.e(e)
				complete = false
			}
		}
		return SourceTree(results, complete)
	}

	/**
	 * Find one file in the source tree by pack and name.
	 *
	 * Used to delete a sticker at its origin: the keyboard only ever holds the imported copy, and
	 * removing that alone would be undone by the next import, which would copy it straight back.
	 *
	 * @return Uri? the document, or null if the tree no longer has it
	 */
	/** What happened when asking the source folder to let go of a file */
	enum class Removal { REMOVED, ABSENT, FAILED }

	/**
	 * Delete one file from the source tree.
	 *
	 * Returns FAILED rather than ABSENT when the lookup itself could not be completed, because the
	 * caller must not read "I could not find out" as "it is already gone" and go on to delete the
	 * imported copy -- that would leave the sticker in the source, absent from the app, and back again
	 * at the next import.
	 */
	fun removeDocument(context: Context, treeUri: Uri, pack: String, name: String): Removal {
		val document = try {
			findDocument(context, treeUri, pack, name)
		} catch (e: Exception) {
			XLog.e("Could not look up '$pack/$name' in the source tree")
			XLog.e(e)
			return Removal.FAILED
		} ?: return Removal.ABSENT

		return try {
			if (DocumentsContract.deleteDocument(context.contentResolver, document)) {
				Removal.REMOVED
			} else {
				Removal.FAILED
			}
		} catch (e: Exception) {
			XLog.e("The source folder would not let go of '$pack/$name'")
			XLog.e(e)
			Removal.FAILED
		}
	}

	fun findDocument(context: Context, treeUri: Uri, pack: String, name: String): Uri? {
		val rootId = try {
			DocumentsContract.getTreeDocumentId(treeUri)
		} catch (e: IllegalArgumentException) {
			XLog.e("'$treeUri' is not a tree uri")
			XLog.e(e)
			return null
		}
		// Files sitting directly in the chosen folder are filed under that folder's own name.
		val packId = if (pack == displayName(context, treeUri, rootId)) {
			rootId
		} else {
			childId(context, treeUri, rootId, pack) ?: return null
		}
		val fileId = childId(context, treeUri, packId, name) ?: return null
		return DocumentsContract.buildDocumentUriUsingTree(treeUri, fileId)
	}

	/**
	 * Remove a pack directory from the source tree, but only on strong evidence that it is empty.
	 *
	 * This is the most dangerous call in the app: DocumentsContract.deleteDocument on a directory
	 * removes everything beneath it, in the user's own storage, where the generated GIFs have no
	 * counterpart to restore from. A single zero-row cursor is not evidence -- a provider may return
	 * an empty cursor that is still loading, which is exactly why StickerImporter.prune refuses to act
	 * on a listing it cannot corroborate.
	 *
	 * So four things must all hold: a complete walk of the tree, no file anywhere in it belonging to
	 * this pack, exactly one directory of that name, and a settled empty listing for it.
	 *
	 * @return Boolean true if the directory was removed
	 */
	fun deletePackIfEmpty(context: Context, treeUri: Uri, pack: String, limit: Int): Boolean {
		val tree = list(context, treeUri, limit)
		if (!tree.complete) {
			XLog.w("Not removing '$pack': the tree could not be read in full")
			return false
		}
		if (tree.files.any { it.pack == pack }) {
			return false
		}

		val candidates = directoriesNamed(context, treeUri, pack, limit)
		if (candidates.size != 1) {
			XLog.w("Not removing '$pack': found ${candidates.size} directories with that name")
			return false
		}
		val packId = candidates.single()
		if (packId == DocumentsContract.getTreeDocumentId(treeUri)) {
			// The chosen folder itself, never ours to delete.
			return false
		}

		if (!settledEmpty(context, treeUri, packId)) {
			XLog.w("Not removing '$pack': its listing did not settle as empty")
			return false
		}

		return try {
			val removed = DocumentsContract.deleteDocument(
				context.contentResolver,
				DocumentsContract.buildDocumentUriUsingTree(treeUri, packId),
			)
			if (removed) {
				XLog.i("Removed the now-empty pack directory '$pack'")
			}
			removed
		} catch (e: Exception) {
			XLog.e("Could not remove the empty pack directory '$pack'")
			XLog.e(e)
			false
		}
	}

	/** Every directory in the tree with this display name, at any depth */
	private fun directoriesNamed(
		context: Context,
		treeUri: Uri,
		name: String,
		limit: Int,
	): List<String> {
		val rootId = try {
			DocumentsContract.getTreeDocumentId(treeUri)
		} catch (e: IllegalArgumentException) {
			return emptyList()
		}
		val found = mutableListOf<String>()
		val queue = ArrayDeque<String>()
		queue.addLast(rootId)
		var visited = 0
		while (queue.isNotEmpty() && visited < limit) {
			val parent = queue.removeFirst()
			visited++
			val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent)
			try {
				context.contentResolver.query(
					children,
					arrayOf(
						DocumentsContract.Document.COLUMN_DOCUMENT_ID,
						DocumentsContract.Document.COLUMN_DISPLAY_NAME,
						DocumentsContract.Document.COLUMN_MIME_TYPE,
					),
					null,
					null,
					null,
				)?.use { cursor ->
					while (cursor.moveToNext()) {
						if (cursor.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR) {
							continue
						}
						val id = cursor.getString(0) ?: continue
						if (cursor.getString(1) == name) {
							found.add(id)
						}
						queue.addLast(id)
					}
				}
			} catch (e: Exception) {
				// A directory we cannot read might be the one we are about to delete, or might hide a
				// second match; either way this is no longer safe.
				XLog.e("Could not scan '$parent' while looking for '$name'")
				XLog.e(e)
				return emptyList()
			}
		}
		return found
	}

	/**
	 * Is this directory empty, asked twice, and never trusting a cursor that says it is still loading.
	 */
	private fun settledEmpty(context: Context, treeUri: Uri, documentId: String): Boolean {
		val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
		repeat(2) {
			val empty = try {
                context.contentResolver.query(
                    children,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null,
                )?.use { cursor ->
					val loading =
						cursor.extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) == true
					!loading && cursor.count == 0
				} ?: false
			} catch (e: Exception) {
				XLog.e("Could not check whether '$documentId' is empty")
				XLog.e(e)
				false
			}
			if (!empty) {
				return false
			}
		}
		return true
	}

	private fun childId(context: Context, treeUri: Uri, parentId: String, name: String): String? {
		val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
		return try {
			context.contentResolver.query(
				children,
				arrayOf(
					DocumentsContract.Document.COLUMN_DOCUMENT_ID,
					DocumentsContract.Document.COLUMN_DISPLAY_NAME,
				),
				null,
				null,
				null,
			)?.use { cursor ->
				while (cursor.moveToNext()) {
					if (cursor.getString(1) == name) {
						return@use cursor.getString(0)
					}
				}
				null
			}
		} catch (e: Exception) {
			XLog.e("Could not look up '$name' under '$parentId'")
			XLog.e(e)
			null
		}
	}

	private fun displayName(context: Context, treeUri: Uri, documentId: String): String {
		val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
		return try {
			context.contentResolver.query(
				uri,
				arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
				null,
				null,
				null,
			)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "__default__"
		} catch (e: Exception) {
			XLog.e("Could not read the name of the root document")
			XLog.e(e)
			"__default__"
		}
	}
}
