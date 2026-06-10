package org.pepsoft.worldpainter.hytale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.pepsoft.worldpainter.hytale.export.HytaleWorldExporter;
import org.pepsoft.worldpainter.hytale.imports.HytaleMapImporter;
import org.pepsoft.worldpainter.importing.MapImporter;
import org.pepsoft.worldpainter.layers.Biome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * TP-125: importing a world that TalePainter itself exported must restore the TalePainter-specific painted data,
 * not just the terrain. Historically the importer restored heights/terrain/water/environments but silently dropped:
 *
 * <ul>
 *     <li>the painted {@link Biome} layer (biome names were never serialized to the save at all),</li>
 *     <li>painted plants (present in the save as real blocks, but skipped as "surface-only" during import),</li>
 *     <li>the auto-vegetation layer (editor-only concept, not serialized).</li>
 * </ul>
 *
 * <p>This test paints all three, exports, re-imports, and asserts each one survives the round trip.
 */
public class Tp125ImportRoundTripTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    private static final long SEED = 42L;

    @Test
    public void importRestoresPaintedBiomePlantsAndAutoVegetation() throws Exception {
        final HytaleTerrain plantTerrain = HytaleTerrain.getByBlockId("Plant_Grass_Lush_Tall");
        assertNotNull("Plant_Grass_Lush_Tall must be registered", plantTerrain);
        final HytaleBiome paintedBiome = HytaleBiome.AZURE_FOREST;

        // ── Build a world with four tiles symmetric about the origin (centering offset 0 by symmetry) ──
        final World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp125World");
        world.setCreateGoodiesChest(false);
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                SEED, Terrain.GRASS, 0, 320, TERRAIN_HEIGHT, 0, false, false);
        final Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        final Dimension dim = new Dimension(world, "Surface", SEED, tileFactory, anchor);
        dim.setEventsInhibited(true);
        for (int tx = -1; tx <= 0; tx++) {
            for (int ty = -1; ty <= 0; ty++) {
                dim.addTile(tileFactory.createTile(tx, ty));
            }
        }

        // ── Paint the three kinds of TalePainter data in tile (0, 0) ──
        dim.setLayerValueAt(Biome.INSTANCE, 10, 10, paintedBiome.getId());
        final Tile tile00 = dim.getTile(0, 0);
        HytalePlantsLayer.setPlantIndex(tile00, 20, 20, plantTerrain.getLayerIndex());
        dim.setBitLayerValueAt(HytaleAutoVegetationLayer.INSTANCE, 30, 30, true);
        world.addDimension(dim);

        // ── Export ──
        final File exportDir = tempDir.newFolder("tp125_roundtrip");
        new HytaleWorldExporter(world, new WorldExportSettings()).export(exportDir, "Tp125World", null, null);
        final File worldDir = new File(exportDir, "Tp125World/universe/worlds/default");
        assertTrue("Exported world dir must exist: " + worldDir, worldDir.isDirectory());

        // ── Import the exported save back ──
        final TileFactory importTileFactory = TileFactoryFactory.createFlatTileFactory(
                SEED, Terrain.GRASS, 0, 320, TERRAIN_HEIGHT, 0, false, false);
        final HytaleMapImporter importer = new HytaleMapImporter(
                worldDir, importTileFactory, null, MapImporter.ReadOnlyOption.NONE);
        final World2 imported = importer.doImport(null);
        final Dimension importedDim = imported.getDimension(anchor);
        assertNotNull("Imported world must have a surface dimension", importedDim);

        // The save's chunks sit at WP + export offset; the importer maps them back 1:1, so painted markers are
        // expected at their original coordinates shifted by the offset recorded in the export sidecar.
        final java.awt.Point offset = readExportOffset(worldDir);

        // ── Painted biome must survive the round trip ──
        assertEquals("Painted biome must be restored on import",
                paintedBiome.getId(), importedDim.getLayerValueAt(Biome.INSTANCE, 10 + offset.x, 10 + offset.y));

        // ── Painted plant must survive the round trip (as a plants-layer value) ──
        final int plantX = 20 + offset.x, plantZ = 20 + offset.y;
        final Tile importedPlantTile = importedDim.getTile(plantX >> TILE_SIZE_BITS, plantZ >> TILE_SIZE_BITS);
        assertNotNull("Imported world must contain the tile with the painted plant", importedPlantTile);
        assertEquals("Painted plant must be restored into the plants layer on import",
                plantTerrain.getLayerIndex(),
                HytalePlantsLayer.getPlantIndex(importedPlantTile, plantX & (org.pepsoft.worldpainter.Constants.TILE_SIZE - 1), plantZ & (org.pepsoft.worldpainter.Constants.TILE_SIZE - 1)));

        // ── Auto-vegetation layer must survive the round trip ──
        assertTrue("Auto-vegetation layer must be restored on import",
                importedDim.getBitLayerValueAt(HytaleAutoVegetationLayer.INSTANCE, 30 + offset.x, 30 + offset.y));
    }

    /** Read the export block offset from the sidecar written next to config.json, or (0, 0) if absent. */
    private static java.awt.Point readExportOffset(File worldDir) throws Exception {
        final File sidecar = new File(worldDir, ".talepainter-export.json");
        if (! sidecar.isFile()) {
            return new java.awt.Point(0, 0);
        }
        final JsonObject json = JsonParser.parseString(
                new String(Files.readAllBytes(sidecar.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        return new java.awt.Point(json.get("blockOffsetX").getAsInt(), json.get("blockOffsetZ").getAsInt());
    }
}
