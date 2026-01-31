package org.pepsoft.worldpainter.platforms;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.exporting.*;
import org.pepsoft.worldpainter.hytale.*;
import org.pepsoft.worldpainter.plugins.BlockBasedPlatformProvider;
import org.pepsoft.worldpainter.plugins.PlatformProvider;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.pepsoft.util.IconUtils.loadUnscaledImage;
import static org.pepsoft.util.IconUtils.scaleIcon;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Platform provider for Hytale world format.
 * 
 * Hytale uses:
 * - 32x32 block chunks (vs Minecraft's 16x16)
 * - 320 block height (10 sections of 32 blocks)
 * - IndexedStorageFile format with Zstd compression
 * - String-based block IDs like "hytale:stone"
 */
public class HytalePlatformProvider extends AbstractPlatformProvider implements BlockBasedPlatformProvider {
    
    public HytalePlatformProvider() {
        super(Version.VERSION, Collections.singletonList(HYTALE), "HytalePlatformProvider");
    }
    
    // BlockBasedPlatformProvider implementation
    
    @Override
    public int[] getDimensions(Platform platform, File worldDir) {
        ensurePlatformSupported(platform);
        // Check if chunks directory exists
        File chunksDir = new File(worldDir, "chunks");
        if (chunksDir.isDirectory()) {
            File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
            if (regionFiles != null && regionFiles.length > 0) {
                return new int[] { DIM_NORMAL };
            }
        }
        return new int[0];
    }
    
    @Override
    public Chunk createChunk(Platform platform, int x, int z, int minHeight, int maxHeight) {
        ensurePlatformSupported(platform);
        return new HytaleChunk(x, z, minHeight, maxHeight);
    }
    
    @Override
    public ChunkStore getChunkStore(Platform platform, File worldDir, int dimension) {
        ensurePlatformSupported(platform);
        return new HytaleChunkStore(worldDir, platform.minZ, platform.standardMaxHeight);
    }
    
    @Override
    public PostProcessor getPostProcessor(Platform platform) {
        ensurePlatformSupported(platform);
        return new HytalePostProcessor();
    }
    
    // PlatformProvider implementation
    
    @Override
    public WorldExporter getExporter(World2 world, WorldExportSettings exportSettings) {
        ensurePlatformSupported(world.getPlatform());
        return new HytaleWorldExporter(world, exportSettings);
    }
    
    @Override
    public File getDefaultExportDir(Platform platform) {
        ensurePlatformSupported(platform);
        // Try to find Hytale installation directory
        // For now, return user's home directory
        return new File(System.getProperty("user.home"));
    }
    
    @Override
    public File selectBackupDir(File exportDir) throws IOException {
        return new File(exportDir.getParentFile(), "backups");
    }
    
    @Override
    public MapInfo identifyMap(File dir) {
        // Check for Hytale world by looking for config.json
        File configFile = new File(dir, "config.json");
        if (configFile.isFile()) {
            File chunksDir = new File(dir, "chunks");
            if (chunksDir.isDirectory()) {
                // This looks like a Hytale world
                return new MapInfo(dir, HYTALE, dir.getName(), ICON, 0, 320);
            }
        }
        return null;
    }
    
    @Override
    public ExportSettings getDefaultExportSettings(Platform platform) {
        return null; // No special export settings for Hytale
    }
    
    @Override
    public ExportSettingsEditor getExportSettingsEditor(Platform platform) {
        throw new UnsupportedOperationException("Hytale has no export settings");
    }
    
    @Override
    public String isCompatible(Platform platform, World2 world) {
        ensurePlatformSupported(platform);
        // Hytale supports single dimension only for now
        if (world.getDimension(new Dimension.Anchor(Constants.DIM_NETHER, Dimension.Role.DETAIL, false, 0)) != null) {
            return "Hytale export does not support Nether dimension";
        }
        if (world.getDimension(new Dimension.Anchor(Constants.DIM_END, Dimension.Role.DETAIL, false, 0)) != null) {
            return "Hytale export does not support End dimension";
        }
        return super.isCompatible(platform, world);
    }
    
    // Use a generic world icon - a Hytale-specific icon can be added later
    public static final Icon ICON = new ImageIcon(scaleIcon(loadUnscaledImage("org/pepsoft/worldpainter/mapexplorer/maproot.png"), 16));
}
