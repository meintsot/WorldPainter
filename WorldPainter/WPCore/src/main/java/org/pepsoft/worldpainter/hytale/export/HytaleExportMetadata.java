package org.pepsoft.worldpainter.hytale.export;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reads/writes the TalePainter export sidecar that records the block offset a Hytale world was
 * exported with, so later merges reuse the exact same alignment instead of recomputing a centering
 * offset that drifts when the world's tile bounds change.
 *
 * <p>Stored as a sidecar next to {@code config.json} (not inside it): Hytale rewrites config.json
 * during play and may drop unknown fields, whereas it ignores — and therefore preserves — this file.
 */
final class HytaleExportMetadata {

    static final String SIDECAR_NAME = ".talepainter-export.json";
    private static final int VERSION = 1;
    private static final Logger logger = LoggerFactory.getLogger(HytaleExportMetadata.class);

    private HytaleExportMetadata() {
    }

    /** Write the export block offset to {@code <worldDir>/.talepainter-export.json}. */
    static void writeBlockOffset(File worldDir, int blockOffsetX, int blockOffsetZ) {
        final JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("blockOffsetX", blockOffsetX);
        json.addProperty("blockOffsetZ", blockOffsetZ);
        final File file = new File(worldDir, SIDECAR_NAME);
        try {
            Files.write(file.toPath(), new Gson().toJson(json).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warn("Could not write export sidecar {}: {}", file, e.getMessage());
        }
    }

    /** Read the stored block offset, or {@code null} if the sidecar is absent or unreadable. */
    static Point readBlockOffset(File worldDir) {
        final File file = new File(worldDir, SIDECAR_NAME);
        if (! file.isFile()) {
            return null;
        }
        try {
            final String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            final JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if ((! json.has("blockOffsetX")) || (! json.has("blockOffsetZ"))) {
                return null;
            }
            return new Point(json.get("blockOffsetX").getAsInt(), json.get("blockOffsetZ").getAsInt());
        } catch (Exception e) {
            logger.warn("Could not read export sidecar {}: {}", file, e.getMessage());
            return null;
        }
    }
}
