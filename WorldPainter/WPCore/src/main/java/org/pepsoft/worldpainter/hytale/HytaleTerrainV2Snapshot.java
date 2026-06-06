package org.pepsoft.worldpainter.hytale;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconstructs the pre-TP-57 Hytale terrain ordering for the legacy save migration.
 *
 * <p>Painted Hytale terrain is stored per pixel as a 1-based ordinal into
 * {@link HytaleTerrain} {@code ALL_TERRAINS} = the curated pick list followed by
 * auto-generated terrains for every remaining {@link HytaleBlockRegistry} block in
 * global alphabetical order. Commit a5812f65 (TP-57) added 249 new block ids
 * (registry grew 1614 -&gt; 1863), shifting every auto-generated terrain that sorts
 * after an inserted block, so a world saved before TP-57 that stored e.g.
 * {@code Soil_Clay} now reads back as a different (alphabetically earlier) block.</p>
 *
 * <p>Worlds saved before the self-describing palette ({@code hytaleTerrainVersion 3})
 * existed carry no palette, so we reconstruct the pre-TP-57 ordering here: the
 * curated prefix is unchanged (TP-57 did not touch {@code HytaleTerrain}); the
 * auto-generated tail is the current tail with the 249 TP-57 additions removed.
 * Verified that {@code HytaleBlockRegistry} had no other changes between TP-57 and
 * this fix, so the reconstruction is exact for the pre-TP-57 era (best-effort for
 * any older era). See {@code HytaleTerrainPalette}.</p>
 */
final class HytaleTerrainV2Snapshot {

    private HytaleTerrainV2Snapshot() {}

    /** The 249 block ids added to HytaleBlockRegistry by commit a5812f65 (TP-57). */
    private static final Set<String> TP57_ADDED_BLOCK_IDS = Set.of(
        "Metal_Bronze", "Metal_Bronze_Decorative", "Metal_Bronze_Fence",
        "Metal_Bronze_Half", "Metal_Bronze_Ornate", "Metal_Bronze_Pipe_Chimney",
        "Metal_Bronze_Pipe_Corner", "Metal_Bronze_Pipe_Large", "Metal_Bronze_Pipe_Large_Corner",
        "Metal_Bronze_Pipe_Large_Mouthpiece", "Metal_Bronze_Pipe_Long", "Metal_Bronze_Pipe_Short",
        "Metal_Bronze_Roof", "Metal_Bronze_Roof_Flat", "Metal_Bronze_Roof_Shallow",
        "Metal_Bronze_Roof_Steep", "Metal_Bronze_Smooth", "Metal_Bronze_Smooth_Half",
        "Metal_Bronze_Stairs", "Metal_Copper", "Metal_Copper_Decorative",
        "Metal_Copper_Fence", "Metal_Copper_Half", "Metal_Copper_Ornate",
        "Metal_Copper_Pipe_Chimney", "Metal_Copper_Pipe_Corner", "Metal_Copper_Pipe_Large",
        "Metal_Copper_Pipe_Large_Corner", "Metal_Copper_Pipe_Large_Mouthpiece", "Metal_Copper_Pipe_Long",
        "Metal_Copper_Pipe_Short", "Metal_Copper_Roof", "Metal_Copper_Roof_Flat",
        "Metal_Copper_Roof_Shallow", "Metal_Copper_Roof_Steep", "Metal_Copper_Smooth",
        "Metal_Copper_Smooth_Half", "Metal_Copper_Stairs", "Metal_Gold_Pipe_Chimney",
        "Metal_Gold_Pipe_Corner", "Metal_Gold_Pipe_Large_Corner", "Metal_Gold_Pipe_Short",
        "Metal_Iron", "Metal_Iron_Decorative", "Metal_Iron_Fence",
        "Metal_Iron_Gutter", "Metal_Iron_Half", "Metal_Iron_Ornate",
        "Metal_Iron_Pipe_Chimney", "Metal_Iron_Pipe_Corner", "Metal_Iron_Pipe_Large",
        "Metal_Iron_Pipe_Large_Corner", "Metal_Iron_Pipe_Large_Mouthpiece", "Metal_Iron_Pipe_Long",
        "Metal_Iron_Pipe_Short", "Metal_Iron_Roof", "Metal_Iron_Roof_Flat",
        "Metal_Iron_Roof_Shallow", "Metal_Iron_Roof_Steep", "Metal_Iron_Smooth",
        "Metal_Iron_Smooth_Half", "Metal_Iron_Stairs", "Metal_Zinc",
        "Metal_Zinc_Decorative", "Metal_Zinc_Fence", "Metal_Zinc_Half",
        "Metal_Zinc_Ornate", "Metal_Zinc_Pipe_Chimney", "Metal_Zinc_Pipe_Corner",
        "Metal_Zinc_Pipe_Large", "Metal_Zinc_Pipe_Large_Corner", "Metal_Zinc_Pipe_Large_Mouthpiece",
        "Metal_Zinc_Pipe_Long", "Metal_Zinc_Pipe_Short", "Metal_Zinc_Roof",
        "Metal_Zinc_Roof_Flat", "Metal_Zinc_Roof_Shallow", "Metal_Zinc_Roof_Steep",
        "Metal_Zinc_Smooth", "Metal_Zinc_Smooth_Half", "Metal_Zinc_Stairs",
        "Plant_Leaves_Apple", "Plant_Leaves_Fir_Tip", "Plant_Leaves_Fir_Tip_Snow",
        "Plant_Leaves_Frostwood", "Plant_Sapling_Amber", "Plant_Sapling_Apple",
        "Plant_Sapling_Aspen", "Plant_Sapling_Azure", "Plant_Sapling_Bamboo",
        "Plant_Sapling_Banyan", "Plant_Sapling_Bottletree", "Plant_Sapling_Camphor",
        "Plant_Sapling_Fig_Blue", "Plant_Sapling_Fire", "Plant_Sapling_Gumboab",
        "Plant_Sapling_Ice", "Plant_Sapling_Jungle", "Plant_Sapling_Maple",
        "Plant_Sapling_Palo", "Plant_Sapling_Petrified", "Plant_Sapling_Sallow",
        "Plant_Sapling_Spiral", "Plant_Sapling_Stormbark", "Plant_Sapling_Wisteria_Wild",
        "Rock_Chalk_Beam", "Rock_Chalk_Brick_Roof_Hollow", "Rock_Chalk_Brick_Roof_Shallow",
        "Rock_Chalk_Brick_Roof_Steep", "Rock_Chalk_Cobble_Roof_Hollow", "Rock_Chalk_Cobble_Roof_Shallow",
        "Rock_Chalk_Cobble_Roof_Steep", "Rock_Chalk_Half", "Rock_Chalk_Stairs",
        "Rock_Magma_Cooled_Beam", "Rock_Magma_Cooled_Brick", "Rock_Magma_Cooled_Brick_Beam",
        "Rock_Magma_Cooled_Brick_Decorative", "Rock_Magma_Cooled_Brick_Half", "Rock_Magma_Cooled_Brick_Ornate",
        "Rock_Magma_Cooled_Brick_Pillar_Base", "Rock_Magma_Cooled_Brick_Pillar_Middle", "Rock_Magma_Cooled_Brick_Roof",
        "Rock_Magma_Cooled_Brick_Roof_Flap", "Rock_Magma_Cooled_Brick_Roof_Flat", "Rock_Magma_Cooled_Brick_Roof_Hollow",
        "Rock_Magma_Cooled_Brick_Roof_Shallow", "Rock_Magma_Cooled_Brick_Roof_Steep", "Rock_Magma_Cooled_Brick_Roof_Vertical",
        "Rock_Magma_Cooled_Brick_Smooth", "Rock_Magma_Cooled_Brick_Smooth_Half", "Rock_Magma_Cooled_Brick_Stairs",
        "Rock_Magma_Cooled_Brick_Wall", "Rock_Magma_Cooled_Cobble", "Rock_Magma_Cooled_Cobble_Beam",
        "Rock_Magma_Cooled_Cobble_Half", "Rock_Magma_Cooled_Cobble_Roof", "Rock_Magma_Cooled_Cobble_Roof_Flap",
        "Rock_Magma_Cooled_Cobble_Roof_Flat", "Rock_Magma_Cooled_Cobble_Roof_Hollow", "Rock_Magma_Cooled_Cobble_Roof_Shallow",
        "Rock_Magma_Cooled_Cobble_Roof_Steep", "Rock_Magma_Cooled_Cobble_Roof_Vertical", "Rock_Magma_Cooled_Cobble_Stairs",
        "Rock_Magma_Cooled_Cobble_Wall", "Rock_Magma_Cooled_Stairs", "Rock_Magma_Cooled_Stalactite_Large",
        "Rock_Magma_Cooled_Stalactite_Small", "Rock_Slate_Beam", "Rock_Slate_Brick_Roof_Hollow",
        "Rock_Slate_Brick_Roof_Shallow", "Rock_Slate_Brick_Roof_Steep", "Rock_Slate_Cobble_Corner",
        "Rock_Slate_Cobble_Roof_Hollow", "Rock_Slate_Cobble_Roof_Shallow", "Rock_Slate_Cobble_Roof_Steep",
        "Rock_Slate_Cracked", "Rock_Slate_Half", "Rock_Slate_Stairs",
        "Wood_Amber_Trunk_Half", "Wood_Amber_Trunk_Stairs", "Wood_Apple_Branch_Corner",
        "Wood_Apple_Branch_Long", "Wood_Apple_Branch_Short", "Wood_Apple_Roots",
        "Wood_Apple_Trunk", "Wood_Apple_Trunk_Full", "Wood_Apple_Trunk_Half",
        "Wood_Apple_Trunk_Stairs", "Wood_Ash_Trunk_Half", "Wood_Ash_Trunk_Stairs",
        "Wood_Aspen_Trunk_Half", "Wood_Aspen_Trunk_Stairs", "Wood_Azure_Trunk_Half",
        "Wood_Azure_Trunk_Stairs", "Wood_Bamboo_Branch_Corner", "Wood_Bamboo_Branch_Corner_Deco",
        "Wood_Bamboo_Branch_Long_Deco", "Wood_Bamboo_Branch_Short", "Wood_Bamboo_Branch_Short_Deco",
        "Wood_Bamboo_Roots", "Wood_Bamboo_Roots_Deco", "Wood_Bamboo_Trunk_Deco",
        "Wood_Bamboo_Trunk_Full", "Wood_Bamboo_Trunk_Full_Deco", "Wood_Bamboo_Trunk_Half",
        "Wood_Bamboo_Trunk_Half_Deco", "Wood_Bamboo_Trunk_Stairs", "Wood_Bamboo_Trunk_Stairs_Deco",
        "Wood_Banyan_Trunk_Half", "Wood_Banyan_Trunk_Stairs", "Wood_Beech_Trunk_Half",
        "Wood_Beech_Trunk_Stairs", "Wood_Birch_Trunk_Half", "Wood_Birch_Trunk_Stairs",
        "Wood_Bottletree_Trunk_Half", "Wood_Bottletree_Trunk_Stairs", "Wood_Burnt_Trunk_Half",
        "Wood_Burnt_Trunk_Stairs", "Wood_Camphor_Trunk_Half", "Wood_Camphor_Trunk_Stairs",
        "Wood_Cedar_Trunk_Half", "Wood_Cedar_Trunk_Stairs", "Wood_Crystal_Trunk_Half",
        "Wood_Crystal_Trunk_Stairs", "Wood_Dry_Trunk_Half", "Wood_Dry_Trunk_Stairs",
        "Wood_Fig_Blue_Trunk_Half", "Wood_Fig_Blue_Trunk_Stairs", "Wood_Fir_Trunk_Half",
        "Wood_Fir_Trunk_Stairs", "Wood_Fire_Roots", "Wood_Fire_Trunk_Half",
        "Wood_Fire_Trunk_Stairs", "Wood_Gumboab_Trunk_Half", "Wood_Gumboab_Trunk_Stairs",
        "Wood_Ice_Branch_Corner", "Wood_Ice_Branch_Long", "Wood_Ice_Branch_Short",
        "Wood_Ice_Roots", "Wood_Ice_Trunk_Full", "Wood_Ice_Trunk_Half",
        "Wood_Ice_Trunk_Stairs", "Wood_Jungle_Trunk_Half", "Wood_Jungle_Trunk_Stairs",
        "Wood_Maple_Trunk_Half", "Wood_Maple_Trunk_Stairs", "Wood_Oak_Trunk_Half",
        "Wood_Oak_Trunk_Stairs", "Wood_Palm_Trunk_Half", "Wood_Palm_Trunk_Stairs",
        "Wood_Palo_Trunk_Half", "Wood_Palo_Trunk_Stairs", "Wood_Petrified_Trunk_Half",
        "Wood_Petrified_Trunk_Stairs", "Wood_Poisoned_Trunk_Half", "Wood_Poisoned_Trunk_Stairs",
        "Wood_Redwood_Trunk_Half", "Wood_Redwood_Trunk_Stairs", "Wood_Sallow_Trunk_Half",
        "Wood_Sallow_Trunk_Stairs", "Wood_Spiral_Trunk_Half", "Wood_Spiral_Trunk_Stairs",
        "Wood_Stormbark_Trunk_Half", "Wood_Stormbark_Trunk_Stairs", "Wood_Windwillow_Trunk_Half",
        "Wood_Windwillow_Trunk_Stairs", "Wood_Wisteria_Wild_Trunk_Half", "Wood_Wisteria_Wild_Trunk_Stairs"
        
    );

    /**
     * The pre-TP-57 {@code stored-index -> block-id} palette, reconstructed from the
     * current terrain ordering by removing the TP-57 additions from the
     * auto-generated tail. The curated prefix keeps its indices; each removed
     * addition shifts every later pre-TP-57 index down by one.
     */
    static Map<Integer, String> asPalette() {
        List<String> current = HytaleTerrain.currentTerrainPaletteBlockIds();
        int curatedCount = HytaleTerrain.getDefaultTerrains().size();
        Map<Integer, String> palette = new HashMap<>(current.size() * 2);
        int oldIndex = 0;
        for (int i = 0; i < current.size(); i++) {
            String id = current.get(i);
            boolean curated = (i < curatedCount);
            if ((! curated) && (id != null) && TP57_ADDED_BLOCK_IDS.contains(id)) {
                continue; // did not exist pre-TP-57; it shifts the later entries
            }
            oldIndex++;
            if (id != null) {
                palette.put(oldIndex, id);
            }
        }
        return palette;
    }
}
