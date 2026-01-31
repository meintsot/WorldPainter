package org.pepsoft.worldpainter.hytale;

import org.pepsoft.util.Box;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.exporting.ExportSettings;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.exporting.PostProcessor;

/**
 * Post processor for Hytale worlds.
 * 
 * Currently a no-op since Hytale doesn't need the same post-processing as Minecraft
 * (no snow/ice melting, no grass spreading, etc.).
 */
public class HytalePostProcessor extends PostProcessor {
    
    @Override
    public void postProcess(MinecraftWorld minecraftWorld, Box volume, ExportSettings exportSettings, ProgressReceiver progressReceiver) 
            throws ProgressReceiver.OperationCancelled {
        // No post-processing needed for Hytale at this time
        // Future implementations could add:
        // - Heightmap recalculation
        // - Lighting calculation
        // - Environment chunk generation
        
        if (progressReceiver != null) {
            progressReceiver.setProgress(1.0f);
        }
    }
}
