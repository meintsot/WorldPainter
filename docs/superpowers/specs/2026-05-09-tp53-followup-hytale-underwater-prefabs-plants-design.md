# TP-53 Follow-up: Underwater Bundled Prefabs and Terrain Plants on Hytale

Date: 2026-05-09

YouTrack: [TP-53](https://fsproject.youtrack.cloud/issue/TP-53)

## Background

Commit `6503a06a` resolved the Bo2 / Custom Layer slice of TP-53: imported `.bo2` / `.schematic` objects with the **Under Water** checkbox can now be placed in flooded columns on Hytale, and their blocks survive the post-export water-sealing pass. That fix did **not** reach two other underwater-capable code paths, both of which the user has reproduced as still-broken:

1. **Bundled Hytale prefabs** painted via `HytalePrefabLayer` (e.g. the catalog-shipped `Plants/Seaweed`).
2. **Terrain plants** painted with the plant brush, written into `HytalePlantsLayer`.

This spec defines the follow-up fix for both.

## Problem

Both failures end with an empty block at `terrain + 1` on flooded columns, but the wipe happens at two different places in the export pipeline.

- **Bundled prefabs** never go through `Bo2LayerExporter` at all. They are pasted by `HytalePrefabPaster.paste()` (`HytalePrefabPaster.java:58-102`) via `chunk.setHytaleBlock(...)` with no seal-protection or support-value call. Prefab pasting is **deferred** (`pendingPrefabPastes` at `HytaleWorldExporter.java:1693`, applied at line 1755) so it runs AFTER the per-pixel chunk-fill loop. The block survives until the post-export **seal pass** at `HytaleWorldExporter.sealAboveTerrainColumn` (lines 1227-1238), which clears any block with `SUPPORT_NONE` and `!isSealProtected()` then writes water fluid into the cleared voxel. The TP-53 Bo2 fix does not apply, so pasted prefab blocks default to `SUPPORT_NONE` + unprotected and get wiped at the seal pass.
- **Terrain plants** are placed at `height + 1` at `HytaleWorldExporter.java:1617-1630` — but this is **inline** inside `populateChunkFromTile`, in the same per-pixel iteration that runs the **inline fluid loop** at lines 1645-1661. On flooded columns, the inline fluid loop runs `chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY)` from `terrain+1` up through `waterLevel`. `HytaleChunk.setHytaleBlock` (lines 197-201) clears both block content and any seal-protected flag whenever a block becomes empty. So even if the plant placement set `setSealProtected(true)`, the inline fluid loop would clear it again, and by the time the post-export seal pass runs the plant is already gone. The plant gets `setDecorative()` only when the world's `plantsPhysicsExempt` flag is enabled — but decorative state controls Hytale runtime physics (gravity + gathering interaction — see comment at lines 1610-1616: decorative blocks disable the "configured drops" interaction so the player gets the block itself, e.g. `Plant_Bush` instead of berries), not export-time block survival.

The user-facing symptoms are: paint a seaweed prefab on water → nothing visible. Paint a plant on water → nothing visible.

## Goals

- Bundled Hytale prefabs survive the seal pass when painted on flooded tiles.
- Terrain plants painted via the plant brush survive the seal pass when painted on flooded tiles.
- Multi-block prefabs that span the water surface (submerged base + above-water top) render correctly: full prefab intact, water fills around the submerged portion only.
- No new UI surface (no checkbox parallel to the Bo2 "Under Water" attribute, no per-plant aquatic flag).
- No regressions on dry tiles or on the existing Bo2 path.

## Non-Goals

- **Floating-on-water-surface plants** (lily-pad-style placement at `waterLevel`). Out of scope for this ticket; spin off if a real Hytale asset needs it.
- **Per-asset aquatic metadata.** Hytale's separate block/fluid layers make it unnecessary — any block at `height + 1` in a flooded column already *is* underwater visually once it survives the seal pass.
- **Minecraft path changes.** Minecraft's `PlantLayerExporter` / `Category.WATER_PLANTS` system is unaffected and stays as-is.

## Design

The mental model is **the same one TP-53 already established for Bo2**: on Hytale, blocks placed by layer exporters above terrain height should be seal-protected so the post-export water-sealing pass leaves them alone. Hytale's fluid layer is separate from its block layer, so a seal-protected block coexists with water in the same voxel — the same mechanism that already makes underwater Bo2 trees work today.

The seal pass at `HytaleWorldExporter.java:1227-1238` clears a block only when **both** `getSupportValue(...) == SUPPORT_NONE` **and** `!isSealProtected(...)`. Setting either flag is sufficient to survive. The fluid write at line 1234 always runs and lives in the separate fluid layer — that is what produces the visual "block in water" effect.

### Change 1 — Seal-protect bundled prefab pastes

In `HytalePrefabPaster.paste()` (`HytalePrefabPaster.java:58-102`), every `chunk.setHytaleBlock(bx, by, bz, hBlock)` call is followed by `chunk.setSealProtected(bx, by, bz, true)` for the same voxel. This applies to all blocks of the prefab unconditionally — blocks above the water surface are outside the seal pass's range, so the flag is a no-op for them.

`setDecorative()` is **not** called here. Decorative state has a real runtime trade-off (it disables the gathering interaction, per the existing comment at `HytaleWorldExporter.java:1610-1616`) and would change the player-facing drop behavior of every block in every prefab. Seal-protection alone is the canonical mechanism for "survive the seal pass" — the seal pass directly checks `isSealProtected()`.

### Change 2 — Move plant placement after the fluid loop, and always seal-protect terrain plants

Two coordinated edits inside `populateChunkFromTile`'s per-pixel loop:

1. **Move** the plant-overlay block (currently at `HytaleWorldExporter.java:1603-1631`) so it runs **after** the inline fluid layer block (currently ending at line 1661). The new per-pixel order is: terrain → fluid → plant overlay → environment → entity. On flooded columns, the fluid loop has already run `setHytaleBlock(EMPTY)` and `setFluid(water)` for cells in `[terrain+1, waterLevel]`; the plant placement that follows then writes the plant block on top of the EMPTY block while the fluid layer (separate) keeps the water. On non-flooded columns the fluid loop's `if (localWaterLevel > height)` guard makes it a no-op, so plant behavior is unchanged.
2. **Add** `chunk.setSealProtected(localX, height + 1, localZ, true)` immediately after `chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock)` in the moved plant block, **unconditionally** — independent of the existing `plantsPhysicsExempt`-gated `setDecorative()` call. The existing decorative call stays exactly as it is. This protects the plant against the post-export seal pass at `sealAboveTerrainColumn`, which would otherwise re-wipe the plant when iterating the flooded column a second time.

Why both edits are needed:

- Without the move, the seal-protection flag the plant sets is cleared again by the inline fluid loop when it calls `setHytaleBlock(y, EMPTY)` — `HytaleChunk.setHytaleBlock` clears the seal-protected flag whenever a block becomes empty. So the one-line addition alone has no effect.
- Without the seal-protection line, the post-export seal pass at `sealAboveTerrainColumn` clears the moved plant on flooded columns the same way it would clear any unprotected `SUPPORT_NONE` block.

This decouples the two concerns:

| Flag | Concern | When set |
|------|---------|----------|
| `setDecorative()` | Hytale runtime physics + gathering interaction (no gravity, block drops self instead of configured drops) | Only when `plantsPhysicsExempt` is enabled (unchanged from current) |
| `setSealProtected()` | Export-time water-sealing pass leaves the block alone | Always, for every plant on Hytale (new) |

The seal-protection flag is purely an export-time concern: it does not change what gets written to the final BSON beyond keeping the placed plant in place. The runtime physics + drop behavior stays gated on the existing user setting.

### Why not Approach 2 (conditionally seal-protect only on flooded columns)

The seal pass at `HytaleWorldExporter.java:1227-1238` only iterates from `terrainHeight + 1` to `waterLevel`, so non-flooded columns have a zero-length iteration range and the seal-protection flag has no effect there. Conditional protection would add branching logic for zero behavioral difference. Rejected.

### Why not Approach 3 (per-asset aquatic metadata)

Hytale's separate block/fluid layers mean every block can coexist with water already. There is no rendering distinction between a "land plant placed on water" and a "water plant" — both are simply "block + fluid in the same voxel." Adding metadata would create a maintenance burden (tag every prefab and plant), reintroduce the very per-asset toggle the user wanted to avoid for the plant brush, and solve no concrete problem. Rejected.

### Multi-block prefabs spanning the water surface

Worked example: terrain height 60, water level 65, prefab spans 61 → 70.

- Seal pass iterates `[61, 65]`. Each voxel in that range: the prefab block is `isSealProtected() == true`, so the clear at lines 1230-1232 is skipped; the fluid write at line 1234 still executes, placing water in the fluid layer at that voxel.
- Voxels `[66, 70]`: outside the seal pass range, untouched. The seal-protected flag set on those blocks is a no-op.
- Final state: full prefab intact at `[61, 70]`; fluid present in the underwater voxels `[61, 65]` only.

This is the desired visual: a tall seaweed sticking out of the water, with water around its submerged base.

## Files Changed

- `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPaster.java` — `paste()` method (line 79): add a `chunk.setSealProtected(bx, by, bz, true)` call immediately after the `chunk.setHytaleBlock(bx, by, bz, hBlock)` call inside the per-block loop.
- `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java` — `populateChunkFromTile`'s per-pixel loop:
  - Relocate the plant-overlay block (originally at lines 1603-1631) to run after the inline fluid-layer block.
  - Inside the relocated plant block, after `chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock)`, add `chunk.setSealProtected(localX, height + 1, localZ, true)` (placed inside the same `if ((plantBlock != null) && ...)` block, before the `plantsPhysicsExempt`-gated `setDecorative` call).

## Test Plan

Manual verification on a Hytale export:

1. **Submerged single-block plant (terrain plant).** Paint a plant at a flooded tile (water above seabed). Export. In-game: plant visible at seabed, water fills the column above it.
2. **Submerged single-block prefab.** Paint the bundled `Plants/Seaweed` prefab on a flooded tile. Export. In-game: seaweed visible at seabed, water fills around it.
3. **Tall multi-block prefab spanning the surface.** Paint a tall prefab where `prefab height > waterLevel - terrainHeight`. Export. In-game: full prefab intact, submerged portion has water around it, above-water portion in air.
4. **Dry-tile regression: plants.** Paint a plant on a non-flooded tile. Export. In-game: unchanged from current behavior.
5. **Dry-tile regression: prefabs.** Paint a prefab on a non-flooded tile. Export. In-game: unchanged.
6. **Bo2 underwater regression.** Paint an imported Bo2 with **Under Water** checked on a flooded tile. Export. In-game: still works as it does post-`6503a06a`.
7. **`plantsPhysicsExempt` toggle.** Verify the plant survives both with the flag on and off — seal-protection is independent of physics-exempt.

## Risks

- **Above-water blocks are seal-protected unnecessarily.** The flag is a no-op outside the seal range. Storage cost is negligible — every chunk voxel already carries seal-protection state. Acceptable.
- **Prefabs containing fluid blocks.** `HytalePrefabPaster.paste()` writes the block's "block" portion via `setHytaleBlock` and the prefab's fluids via `setFluid` separately (lines 83-99). The seal-protection call only wraps the `setHytaleBlock` call, not the `setFluid` call, so prefab-supplied fluids are unaffected. Confirmed safe.
- **Plant-block move could affect non-flooded plant placement.** Moving the plant overlay to run after the fluid layer changes the per-pixel order from terrain → plant → fluid → environment to terrain → fluid → plant → environment. On non-flooded columns the fluid block's `if (localWaterLevel > height)` guard makes it a no-op, so the order change is observably equivalent. Verified by `Tp60PlantSubstrateTest` (plant on stone substrate, no water) and `Tp60PlantsPhysicsExemptToggleTest` (the physics-exempt toggle behavior) both passing post-move.
- **Test coverage.** New JUnit tests at `Tp53UnderwaterPrefabPasterTest` and `Tp53UnderwaterPlantTest` exercise both fixes. The plant test is end-to-end (export → readback), so it doubles as a smoke test for the export pipeline. Manual in-game smoke testing (Task 3 in the plan) is still required to verify visual rendering and the multi-block-spanning-surface case.
