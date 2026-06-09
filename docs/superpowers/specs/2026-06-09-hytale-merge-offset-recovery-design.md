# Hytale merge offset recovery & persistence — Design

**Date:** 2026-06-09
**Status:** Approved (design)
**Platform scope:** Hytale / TalePainter only

## Problem

A Hytale merge (in-place selective merge or full merge) places tiles using a
block offset computed by **re-centering the world's current tile set**
(`HytaleWorldMerger.determineBlockOffset` → `centeringOffset(getTileCoords())`
for painted worlds, `(0,0)` for imported). The original export used the same
`centeringOffset` formula over *its* tile set. The two agree **only if the tile
bounding box is unchanged**. When the world is revamped (tiles added/removed),
the bounding-box center moves, the recomputed offset drifts, and merged tiles
land displaced from the chunks already in the exported world.

Observed on "Calandor Map revamp": the existing export's regions are centered
(X:-3..3, Z:-3..2); a selective in-place merge created a brand-new region
`(-1,1)` instead of overwriting the selected tile's original chunks — the offset
had drifted because the map was revamped since the 6/3 export.

Root cause: **the merge has no record of the offset the existing map was written
with, so it recomputes one that no longer matches.** Not specific to the in-place
optimization — the full-merge path has the same `determineBlockOffset`.

## Goals

- Merges reuse the exact offset the existing map was written with, regardless of
  later tile-bound changes or imported/scratch status.
- Fix already-exported maps (e.g. Calandor) **in place, with no re-export** and
  no loss of in-game progress.
- Never silently misplace: if alignment can't be determined confidently, abort.

## Non-goals

- Minecraft merge (`JavaWorldMerger`) — out of scope.
- Changing the centering behavior of fresh exports.

## Decisions

1. **Store the offset durably at export** in a **sidecar file**, not `config.json`
   (Hytale rewrites `config.json` during play and may strip unknown fields; a
   sidecar is ignored by Hytale and survives play).
2. **Recover the offset for legacy maps** from the spawn point baked into the
   existing `config.json` (`saveSpawn = worldSpawn + offset`, confirmed at
   `HytaleWorldConfigWriter.java:83`), so Calandor is fixed without re-export.
3. **Validate** a recovered/heuristic offset against the existing chunks before
   trusting it. **If it can't be validated, abort** the merge with a clear
   message rather than misplace tiles.

## Design

### Sidecar storage

- File: `<innerWorldDir>/.talepainter-export.json`
- Content: `{"version":1,"blockOffsetX":<int>,"blockOffsetZ":<int>}`
- Written in `HytaleWorldExporter.exportDimension`, immediately after the offset
  is resolved and `blockOffsetX/Z` are set. This single insertion point covers
  **every** path that runs `exportDimension`: plain export, full merge
  (`super.export`), and in-place selective merge — all write/refresh the sidecar
  with the offset actually used.

### Offset resolution (merger)

Resolve **once, early in `merge()`, before any backup rename**, while `mapDir`
still holds the existing chunks and `config.json`. Cache in a field
`resolvedBlockOffset`; the `determineBlockOffset` override returns it.

```
resolveBlockOffset():
  1. sidecar = readExportSidecar(mapDir)
     if sidecar present -> return sidecar           // authoritative; future maps & Calandor after 1st fix
  2. existingRegions = readExistingRegionCoords(mapDir)
     if existingRegions empty -> return heuristicOffset()   // nothing to align to (degenerate)
  3. candidates = []
     oSpawn = recoverOffsetFromSpawn(mapDir)         // saveSpawn - world.getSpawnPoint(); null if unavailable
     if oSpawn != null: candidates += oSpawn
     candidates += heuristicOffset()                 // centeringOffset(allTiles) | (0,0) if imported
  4. best = candidate maximizing coverage(allTiles, candidate, existingRegions)
  5. if coverage(best) >= COVERAGE_THRESHOLD (0.9) -> return best
     else -> throw InvalidMapException(abort message)
```

- `heuristicOffset()` = today's behavior: `(0,0)` if `world.getImportedFrom() != null`,
  else `centeringOffset(world.getDimension(NORMAL_DETAIL).getTileCoords())`.
- `determineBlockOffset(dim, tiles)` override returns `resolvedBlockOffset` when set,
  else `heuristicOffset()`. (The else-branch preserves the existing direct-call
  unit tests `mergeReproducesExportCenteringOffsetForPaintedFromScratchWorld` /
  `mergeKeepsZeroOffsetForImportedMap`, which call `determineBlockOffset` without
  going through `merge()`.)

### Spawn recovery

`recoverOffsetFromSpawn(mapDir)`:
- Read `mapDir/config.json` (GSON). Navigate `SpawnProvider.SpawnPoint.X` / `.Z`.
- `wpSpawn = world.getSpawnPoint()` (a `java.awt.Point`; `.x`/`.y` → X/Z).
- Return `new Point(round(saveSpawnX) - wpSpawn.x, round(saveSpawnZ) - wpSpawn.y)`.
- Return `null` if `config.json` missing/unparseable, `SpawnProvider.SpawnPoint`
  absent, or `world.getSpawnPoint()` is null.

### Coverage validation

`coverage(tiles, offset, existingRegions)` = fraction of `existingRegions` that
are "explained" by some current tile under `offset`:
- For each tile `t`: block span `[t.x*128+offX, +127] × [t.y*128+offZ, +127]`;
  region span `[block>>10]` per axis (region = chunk>>5 = block>>10). Collect the
  regions the tile touches.
- `hit` = distinct existing regions touched by any tile. `coverage = |hit| / |existingRegions|`.

The correct offset lands the unchanged tiles back on the existing regions →
coverage ≈ 1.0 (minus any tiles deleted since export). A grossly wrong offset
(unrelated map, spawn fully rewritten) scores low → abort. `COVERAGE_THRESHOLD =
0.9` tolerates a modest number of deleted tiles while rejecting gross
misalignment. Selection picks the highest-coverage candidate, so when both a
correct `oSpawn` (~1.0) and a drifted `oCentering` (<1.0) are present, `oSpawn`
wins. Residual sub-region precision is trusted to the spawn anchor (exact when
the spawn is unchanged); the user verifies in-game.

### Abort message

`InvalidMapException`: "Could not determine the original block alignment of the
existing Hytale map (best match <pct>% of existing regions). Aborting so tiles
are not misplaced. This map was exported before TalePainter stored its export
offset, and the spawn-based recovery did not line up — re-check the world's
spawn point, or do a full export to reset alignment." Surfaced to the user via
the existing merge error path.

## Scope of effect

- **In-place selective merge** and **full merge** both call `merge()` →
  `resolveBlockOffset()` and `exportDimension` → sidecar write. Both fixed.
- **Plain export** writes the sidecar (no behavior change otherwise).
- After the first fixed merge/export, the sidecar exists → all later merges take
  the authoritative path 1 (no spawn dependency).

## Testing

1. **Sidecar round-trip** — write then read returns the same offset; absent file
   returns null; malformed file returns null (no throw).
2. **Sidecar is authoritative** — with a sidecar present, `resolveBlockOffset`
   returns it even when `centeringOffset(currentTiles)` would differ.
3. **Spawn recovery computes the right offset** — build an exported map, change
   the world's tile bounds (so `centeringOffset` drifts), delete the sidecar,
   merge a selection; assert the selected tile lands on its original region
   (the chunk it occupied in the export), not a drifted one.
4. **Coverage selects spawn over drifted centering** — direct test of
   `resolveBlockOffset` returning `oSpawn` when bounds changed.
5. **Bounds unchanged still works** — no sidecar, no drift; resolves to the same
   offset and merges correctly (regression).
6. **Abort when nothing aligns** — point the merger at a map whose chunks don't
   correspond to the world (and no recoverable spawn); assert `InvalidMapException`.
7. **Export writes the sidecar** — after a plain export, `.talepainter-export.json`
   exists with the export's offset.
8. **Existing merger tests stay green** — including the direct `determineBlockOffset`
   tests (which now hit the `resolvedBlockOffset == null` fallback).

## Risks

- **Spawn moved since export** → spawn recovery wrong. Mitigated by coverage
  validation (gross errors abort) + in-game verification. Sub-region spawn moves
  are not caught by region-level coverage; accepted (low likelihood; user saw the
  prior misplacement and will re-check).
- **Hytale rewrote `config.json` spawn during play** → same as above; coverage
  guards gross cases.
- **Many tiles deleted since export** → correct offset's coverage may dip below
  threshold and abort. Acceptable per the abort-on-uncertainty decision.

## Affected code

- `HytaleWorldExporter.java` — sidecar write in `exportDimension`; sidecar I/O
  helpers (or a small `HytaleExportMetadata` util).
- `HytaleWorldMerger.java` — `resolveBlockOffset()` called early in `merge()`;
  `resolvedBlockOffset` field; `determineBlockOffset` returns it; spawn recovery,
  region enumeration, coverage helpers.
- `HytaleWorldMergerTest.java` (+ possibly a small `HytaleExportMetadataTest`) — tests above.
