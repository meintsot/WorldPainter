# Hytale Block Physics System

## Key Files
- `decompiled-src/com/hypixel/hytale/builtin/blockphysics/BlockPhysicsUtil.java` — core support evaluation
- `decompiled-src/com/hypixel/hytale/builtin/blockphysics/BlockPhysicsSystems.java` — ticking system
- `decompiled-src/com/hypixel/hytale/server/core/blocktype/component/BlockPhysics.java` — nibble array component
- `decompiled-src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType.java` — block type config

## Physics Nibble Array
- 4-bit per block, 16384 bytes per 32x32x32 section
- Values: 0=no support, 1-14=propagated distance, 15=IS_DECO (exempt)
- Serialized as: boolean (hasData) + raw bytes

## CRITICAL: Export Rule
**DO NOT pre-compute support values. Write ALL zeros (null support data).**

The game engine computes support values on-demand when blocks are disturbed. Any pre-computed values will be wrong because:
1. Leaves adjacent to wood get SATISFIES_SUPPORT (-2) → runtime sets value to 0
2. Any non-zero value on those leaves triggers cascading "corrections"
3. Each correction calls performBlockUpdate (ticks 27 neighbors)
4. Mass cascade can cause blocks to temporarily evaluate as unsupported → BREAK

### Failed approaches:
- IS_DECO (15) on all blocks: leaves never cascade (exempt from physics) → floating trees
- IS_DECO on wood, BFS 1-7 on leaves: wood IS_DECO causes lowestSupportDistance=1 mismatch
- BFS 1-7 on leaves, 0 on wood: off-by-one (adjacent leaves should be 0 not 1) → mass cascade

### Correct approach:
- ALL values = 0 (null support data per section)
- needsPhysics = false in BlockChunk
- Game computes correct values on first disturbance via testBlockPhysics

## testBlockPhysics Return Values
- `-1` = IGNORE (no support requirements → block never breaks)
- `-2` = SATISFIES_SUPPORT (directly supported → value set to 0)
- `-3` = WAITING_CHUNK
- `0` = NO SUPPORT (block breaks)
- `1..14` = propagated support distance

## applyBlockPhysics Behavior
- support=-2, currentValue=0 → VALID (no change) ← steady state for directly supported blocks
- support=-2, currentValue≠0 → SET to 0, cascade ← causes mass updates if pre-computed wrong
- support=0 → BREAK block
- support=N, current=N → cascade with maxDistance=N-1
- support=N, current≠N → SET to N, cascade

## Block Material Types
- `BlockMaterial.Solid`: Full cubes (wood trunks, stone, dirt). Get ALL_SUPPORTING_FACES. NO support requirements → testBlockPhysics returns -1 → never break.
- `BlockMaterial.Empty`: Non-solid blocks. Get `REQUIRED_BOTTOM_FACE_SUPPORT` if no explicit config.
- Leaves: Material.Empty but with explicit support config referencing TreeWoodAndLeaves BlockSet + maxSupportDistance=7

## canBePlacedAsDeco() Check
Only skips physics for blocks where `canBePlacedAsDeco() && value==15`. Most blocks return false for canBePlacedAsDeco(), so IS_DECO doesn't protect them.
