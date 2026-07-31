# Importing

The importer copies a user-chosen SAF directory into app storage. Upstream it deleted both trees,
re-copied everything, and regenerated every GIF; adding one pack cost a full rebuild and the keyboard
was empty throughout. It is now incremental and non-destructive.

Measured on a 358-sticker library with 53 animated stickers:

| | Before | After |
|---|---|---|
| Reload, nothing changed | 32 s | ~0.3 s |
| Add one 3-file pack | 358 files + 53 GIFs | 3 files |

## The sequence

1. **List** the tree with one `contentResolver.query` per directory, projecting document id, name,
   mime, size and last-modified (`SourceListing`). `DocumentFile` caches nothing — every
   `name`/`type`/`isFile` is its own Binder round trip, so the old walk cost thousands of them and
   still did not yield the metadata an incremental import needs.
2. **Full rebuild only**, delete the trees — *after* the listing has succeeded, never before.
3. **Plan** sequentially: reject unsupported mimes, classify variants, enforce the per-pack cap. All
   in one pass so the concurrent stage has no shared state to mutate.
4. **Adopt** destinations already holding exactly the source's byte count into the manifest, so a
   first run after upgrading costs nothing rather than a full re-copy.
5. **Copy** what changed, eight at a time, into a temp file then rename.
6. **Prune** what the source no longer has.
7. **Save the manifest**, under `NonCancellable`, and forget entries the source no longer offers so
   it cannot grow forever.
8. **Generate** missing GIF versions, if enabled.

## The manifest

`filesDir/import-manifest.tsv`, one line per file: `pack/name<TAB>size<TAB>modified`. A file is
up to date when its manifest entry matches the source *and* the destination is present at the same
length.

A provider reporting `0` for size or mtime is treated as **unknown**, never as a matching value —
otherwise such files look unchanged forever.

## Pruning is the dangerous part

Deleting is the only thing here that can lose data that no source can restore: the generated GIFs
have no counterpart in the sticker folder. Three guards:

- **An incomplete listing never prunes.** `SourceListing` reports whether every directory could be
  read. One unreadable directory is indistinguishable from the user having deleted that whole pack.
- **A listing accounting for under half of what is imported never prunes.** Catches a stale grant or
  an empty cursor, and points the user at Full re-import.
- **Variants are judged differently from stickers.** A sticker is wanted if it is in the plan. A
  variant is dropped only when its sticker is gone, or when that sticker's content actually changed
  *and* a generation pass will follow to replace it.

### Why staleness is not decided by timestamps

The obvious rule — "variant older than its sticker means stale" — is wrong, because **copying
rewrites the destination and moves its mtime forward**. Every sticker touched by a run then looks
newer than its variant. On a first run, where nothing has a manifest entry and everything is
re-copied, that means *every* variant.

This shipped briefly and the log shows exactly it:

```
copied 358, removed 53
Wrote 53 / 53 GIF variant(s)
```

All 53 GIFs deleted and rebuilt, for files whose content never changed. They came back only because
GIF generation was enabled; with it off — the default — 66MB would have been lost with nothing to
restore it. Staleness now comes from what the manifest says actually changed.

## Deleting a sticker

Long-pressing a sticker offers a bin, armed by the first tap and acted on by the second — it is
armed for four seconds and then forgets. Two taps because this is the only place in the app that
removes files from the user's own storage.

Order matters, and the source goes first:

1. **The source file**, via `SourceListing.removeDocument`. That returns `REMOVED`, `ABSENT` or
   `FAILED`, and `FAILED` aborts the whole thing — treating "I could not find out" as "it is already
   gone" would delete the imported copy of a sticker the next import brings straight back.
2. **The sibling `.gif`** that `tools/gif-variants.py` writes next to a webp. Leaving it behind means
   the next import no longer sees a webp of that name, so the orphan imports as a sticker in its own
   right and the deleted sticker reappears as a GIF.
3. The imported copy, the generated variant, the cached PNG fallback, and its entries in favourites
   and recents.
4. **The pack directory**, if it just emptied — from app storage, the variants tree, and the source.

Then packs are reloaded and the content view is rebuilt. That last step matters: `jumpToPack` reuses
an already-built all-packs list and only scrolls it, which is right when the selection changed and
wrong after a deletion — the adapter keeps a cell for a file that has gone, and it draws as a blank
gap instead of the grid closing up.

### Removing a source directory is the riskiest thing here

`DocumentsContract.deleteDocument` on a directory is **recursive**. `deletePackIfEmpty` therefore
requires four independent things to agree before it runs:

- a **complete** walk of the tree,
- **no file anywhere in it** belonging to that pack,
- **exactly one** directory of that name, matched on `MIME_TYPE_DIR` so a *file* named like a pack
  cannot be deleted,
- an emptiness check **asked twice** that rejects a cursor still reporting `EXTRA_LOADING`.

A single zero-row cursor is not evidence. A provider that loads children asynchronously returns an
empty, still-loading cursor right after a delete invalidates its cache — and taking that at face
value would destroy a pack directory and every unimported file in it.

## Full re-import

A separate button, because the normal path no longer rebuilds anything. It lists first, then deletes
both trees and the manifest, then proceeds as above. Ordering matters: deleting first and *then*
discovering the source is unreadable — an unmounted card, a grant gone stale — destroys the library
in precisely the situation the user reached for that button because something already looked wrong.

## Concurrency

`MainActivity` serialises every library-mutating operation behind one guard (`runLibraryOperation`):
import, full re-import, generate and delete. All buttons in both cards are disabled for the duration
and restored in a `finally`. Without it, a delete could wipe the variants tree underneath a running
generation, and returning to the screen mid-generation re-enabled the buttons so a second generator
would convert everything twice into the same files.
