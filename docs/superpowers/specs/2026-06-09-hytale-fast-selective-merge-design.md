# Fast in-place selective-tile merge (Hytale) — Design

**Date:** 2026-06-09
**Status:** Approved (design); pending implementation plan
**Platform scope:** Hytale / TalePainter only

## Problem

Applying a small tile selection back into an already-exported Hytale world is
disproportionately slow: the time is governed by the **total world size**, not
by the number of selected tiles. On a huge exported world, merging a handful of
repainted tiles can take as long as a full export.

### Why it's slow today

`HytaleWorldMerger.merge()` implements a merge as a **rename-and-rebuild**:

1. Rename the entire save root to a backup (`HytaleWorldMerger.java:321`).
2. Call `super.export(...)`, which deletes/recreates the save and regenerates
   chunks — but only for the selected tiles (`HytaleWorldExporter.exportDimension`
   already filters `tileCoords` to the selection, `:355`).
3. `preserveUntouchedOriginalChunks` (`:446`) walks **every chunk in the entire
   backup**, Zstd-decompresses + BSON-parses each (`getChunk`), then BSON-encodes
   + Zstd-recompresses each (`saveChunk`) into the fresh save — for the whole
   world minus the selection.
4. `copyPreservedFiles` copies the remaining non-chunk files.

Chunk **generation** is already proportional to the selection. The cost is
step 3: an O(world-size) decompress/recompress of chunks the user never touched.

### Why a fix is cheap

Hytale region files (`*.region.bin`, `HytaleRegionFile`) are a **random-access
indexed store** (like Minecraft `.mca`): `writeChunk` (`:203`) finds free
segments, writes a single chunk blob, and patches one 4-byte index entry — it
does **not** rewrite the whole file. The only reason the current merge can't
exploit this is that `exportRegion` opens region files with `create()`
(`:546`), which **truncates** — forcing the backup + preserve dance.

## Goals

- Selective-tile merge cost ≈ generate + write **only the selected tiles**;
  independent of total world size.
- Reuse existing chunk generation and merge semantics verbatim.
- Small, surgical change.

## Non-goals

- The Minecraft (`JavaWorldMerger`) path. It already raw-copies untouched
  regions and honors the Read-Only layer; it is not the bottleneck. Unchanged.
- The full-merge path (no tile selection). It rewrites everything anyway, so
  there is nothing to save. Unchanged.
- Automatic backups. The user opted for a pre-merge warning instead (see
  "Decisions").

## Decisions (from brainstorming)

1. **In-place, no automatic backup**, with a clear pre-merge warning telling the
   user to back up first.
2. **No layout-change guard in code.** Assume the user only repainted existing
   tiles (did not add/remove tiles since export). The assumption is stated in
   the warning. Changing the map extent shifts the centering offset and can
   misplace chunks; that risk is accepted and surfaced to the user, not checked.
3. **Merge semantics for selected tiles are unchanged** — entities, block
   health, water tints, prefab markers, biomes, and the above/under-ground merge
   flags still apply, read from the live chunk before it is overwritten.

## Approach (Approach 1 — in-place region patching)

When a tile selection is active, skip the backup and the rebuild entirely. Open
each region file that contains a selected tile with `openOrCreate()` (not
`create()`), regenerate that tile's chunks, and `writeChunk` them back over
their existing blobs. Every other chunk in those regions — and every region file
with no selected tile — is never read or written.

### Architecture

Gating in `HytaleWorldMerger.merge()`:

- **Tile selection active** (`worldExportSettings.getTilesToExport() != null`)
  → `mergeSelectedTilesInPlace(progressReceiver)`.
- **No selection** → existing full-rebuild behavior, unchanged.

`mergeSelectedTilesInPlace`:

1. `performSanityChecks()` (height checks). No rename, no backup, no
   save-structure rebuild.
2. Point `originalChunkStore` at the **live** inner world dir, so
   `mergeOriginalChunkData` and `applyMergeOverrides` read the current on-disk
   chunk as the "original" before it is overwritten.
3. Call a new slim exporter entry point that writes the selected tiles **into the
   existing `chunks/` dir**.
4. Return. `config.json`, `players/`, and untouched region files are left exactly
   as they were.

In `HytaleWorldExporter`, add an **in-place mode**:

- New slim entry point (e.g. `exportSelectedTilesInPlace(worldDir, selectedTiles,
  progress)`) that calls the **existing** `exportDimension` (which already
  computes the region set from `tileCoords` and skips non-selected tiles per
  chunk, `:581`). No new generation logic.
- The only functional change inside `exportRegion`: open the region file with
  `openOrCreate()` instead of `create()` when in-place, threaded through as a flag
  or a separate code path. `writeChunk` then overwrites only the selected tiles'
  chunks; all other blobs are preserved.
- Skip directory scaffolding (`config.json`, `players/`, `resources/`, server
  boilerplate) — it already exists in the live world.

Unchanged and reused as-is: `mergeOriginalChunkData`, `applyMergeOverrides`,
`determineBlockOffset` → `centeringOffset(all tile coords)`, region-level layer
passes, the full-merge path.

### Single-read/single-write safety

Within a region, `exportRegion` reads all originals during the populate/merge
phase and writes all chunks afterward (the `:709` write loop) — every read
precedes every write. Across regions the executor works on distinct files. So
the live read (via `originalChunkStore`) never races the overwrite, even though
two handles (read via store, read/write via the region file) are open on the
same file. After a region's writes, the store's cached read handle for that
region is stale but is never read again.

### Data flow

1. `blockOffset = determineBlockOffset(all tile coords)` — equals the offset the
   live world was written with under the unchanged-layout assumption.
2. Region set built from the **selected** tiles only; each runs `exportRegion`
   with `openOrCreate()`.
3. Per chunk slot: tile not selected → `continue` (slot untouched on disk);
   selected → read live chunk as original, regenerate, merge metadata + apply
   flags, stash.
4. Region-level layers run on the stashed (selected) chunks only.
5. Write loop `writeChunk`s only the stashed chunks; flush.

### Edge cases

- **Region holds selected + unselected tiles.** Only selected chunks are in
  `chunksByCoords`, so only their blobs are overwritten; the unselected chunks in
  the same file keep their blobs. (This is exactly what `openOrCreate` fixes
  versus `create()`.)
- **Selected tile became Void.** Its chunks are regenerated empty/void-enforced
  and written, overwriting the old terrain. Matches "paint void to erase."
- **Selected tile's chunk absent in the live file.** `writeChunk` allocates a
  new blob in the existing region file.
- **Whole region file absent.** `openOrCreate` creates it fresh and writes just
  that tile's chunks (graceful boundary of the unchanged-layout assumption).
- **Larger compressed blob on overwrite.** `findFreeSegments` reuses the freed
  segments of the overwritten chunk or appends; other chunks' segments are marked
  used at `open()` and never reused. Worst case: slight file growth / a hole. No
  compaction needed.
- **Cancel/crash mid-run.** No backup → already-written regions are updated, the
  rest are original — a partial but non-corrupt world. Covered by the warning; no
  rollback attempted.
- **Progress** is already proportional — `exportDimension` builds its progress
  manager over the selected region set.

### UI & warning

No new controls. The existing "merge N selected tiles" radio
(`MergeWorldDialog:93`) is the trigger; the merger decides fast-vs-full
internally based on the presence of a selection.

Before launching `MergeProgressDialog` for a Hytale merge with a selection
active, show a blocking confirm dialog:

> **Fast in-place merge**
> This will update only the N selected tiles, written directly into your exported
> world. No backup is made.
>
> Before continuing, make sure:
> • You have your own backup of this world.
> • You have not added or removed tiles since the last export — only repainted
>   existing ones. (Changing the map's extent shifts coordinates and can misplace
>   chunks.)
>
> Continue?  [Cancel]  [Merge in place]

- Default button is **Cancel**.
- Shown only for Hytale + selection-active; full-merge and Minecraft paths
  unchanged.
- The same two bullets are folded into the existing warnings HTML
  (`MergeWorldDialog:~272`) so the caveat is visible in the dialog itself.

### Error handling

No rollback (no backup, by decision). Each region is flushed after its writes.
Errors propagate with clear context; the in-place path does not invoke the
full-merge catch block's backup-restore (there is no backup to restore).
Cancellation is honored between regions via the existing `abort` mechanism.

## Testing

Extend `HytaleWorldMergerTest`:

1. **Selected tile updated** — repaint one tile, merge its selection, assert the
   reloaded chunk shows the new terrain.
2. **Unselected chunks byte-preserved** — capture an untouched region file's
   bytes (or `lastModified`) before; assert unchanged after. Direct proof we did
   not rebuild the world.
3. **Mixed region** — merge one tile of a shared region; assert the selected
   tile's chunks changed and the other tile's chunks in the same file are still
   equal to the original.
4. **Metadata preserved** — entity / block-health / prefab marker in the original
   chunk survives a repaint+merge (proves `mergeOriginalChunkData` runs in-place).
5. **Void erase** — paint a selected tile to Void, merge, assert its chunks are
   empty where they had terrain.
6. **New-region tolerance** — select a tile whose region file is absent; assert
   the merge creates it and writes only that tile's chunks.

Manual (`verify` skill) on a real large world: select 2–3 tiles, run the in-place
merge, confirm it finishes in seconds (not full-export time) and the world loads
in Hytale with edits applied and everything else intact.

Out of scope: Minecraft path and full-merge path (unchanged) — confirm existing
tests still pass.

## Risks & mitigations

- **Writes to the live world without a backup.** Accepted by decision; surfaced
  via the pre-merge warning.
- **Unchanged-layout assumption violated** (user changed tile extent). Not checked
  by code; can misplace chunks. Surfaced in the warning. Degrades gracefully for
  the absent-region case but is not guaranteed correct if the offset shifted.
- **Two open handles on one region file.** Mitigated by the read-before-write
  ordering within a region and distinct files across regions.

## Affected code

- `HytaleWorldMerger.java` — gating branch + `mergeSelectedTilesInPlace`.
- `HytaleWorldExporter.java` — slim in-place entry point + `create()` →
  `openOrCreate()` open-mode switch threaded into `exportRegion`.
- `MergeWorldDialog.java` — pre-merge confirm dialog + warning-text line (Hytale +
  selection only).
- `HytaleWorldMergerTest.java` — new test cases above.
