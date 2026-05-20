package org.pepsoft.worldpainter.hytale.chunk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.hytale.HytaleBlock;
import org.pepsoft.worldpainter.hytale.HytaleBlockMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pepsoft.worldpainter.hytale.chunk.HytaleBsonPaletteCodec.PALETTE_TYPE_BYTE;
import static org.pepsoft.worldpainter.hytale.chunk.HytaleBsonPaletteCodec.PALETTE_TYPE_EMPTY;
import static org.pepsoft.worldpainter.hytale.chunk.HytaleBsonPaletteCodec.PALETTE_TYPE_HALF_BYTE;
import static org.pepsoft.worldpainter.hytale.chunk.HytaleBsonPaletteCodec.PALETTE_TYPE_SHORT;

/**
 * Encodes the per-section bodies (BlockSection and FluidSection) used by
 * {@link HytaleBsonChunkSerializer}.
 *
 * <p>Each section in a chunk column has its own block palette, optional rotation
 * palette, fluid palette and (currently empty) light data. Splitting this code
 * out keeps the top-level orchestrator focused on chunk-level components.</p>
 */
final class HytaleSectionBodyEncoder {

    private static final Logger logger = LoggerFactory.getLogger(HytaleSectionBodyEncoder.class);

    // Codec version from decompiled sources
    private static final int BLOCK_SECTION_VERSION = 6;

    private HytaleSectionBodyEncoder() {
        // Utility class
    }

    /**
     * Create BlockSection BSON with block palette data.
     * Format from BlockSection.CODEC (version 6):
     * - "Version": 6
     * - "Data": byte array with serialized section data
     *
     * The section data format (from AbstractByteSectionPalette.serialize()):
     * - int: block migration version
     * - byte: palette type ordinal
     * - short: palette entry count
     * - For each palette entry:
     *   - byte: internal palette index
     *   - short + chars: block type string (UTF with length prefix)
     *   - short: count of this block type in section
     * - bytes: raw block data array (16384 for HalfByte, 32768 for Byte palette)
     * - short: ticking blocks cardinality (if palette != empty)
     * - short: ticking blocks bitset length
     * - longs: ticking blocks bitset data
     * - byte: filler section palette type
     * - filler section data...
     * - byte: rotation section palette type
     * - rotation section data...
     * - ChunkLightData: local light
     * - ChunkLightData: global light
     * - short: local change counter
     * - short: global change counter
     *
     * @param section The section data.
     * @param sectionY The section index (0-9).
     * @param chunk The parent chunk, needed for heightmap-based lighting.
     */
    static BsonDocument createBlockSectionBson(HytaleSection section, int sectionY, HytaleChunk chunk) {
        BsonDocument doc = new BsonDocument();
        doc.put("Version", new BsonInt32(BLOCK_SECTION_VERSION));

        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            // Block migration version
            buf.writeInt(0);

            HytaleBlock[] hytaleBlocks = section.getHytaleBlocks();
            boolean useHytaleBlocks = section.hasHytaleBlocks();

            if (useHytaleBlocks) {
                Map<String, Integer> paletteIndex = new HashMap<>();
                List<String> palette = new ArrayList<>();
                List<Integer> counts = new ArrayList<>();
                int[] blockIndices = new int[hytaleBlocks.length];
                boolean allAir = true;

                for (int i = 0; i < hytaleBlocks.length; i++) {
                    HytaleBlock effective = (hytaleBlocks[i] != null) ? hytaleBlocks[i] : HytaleBlock.EMPTY;
                    if (effective.isFluid()) {
                        effective = HytaleBlock.EMPTY;
                    }
                    if (!effective.isEmpty()) {
                        allAir = false;
                    }
                    String id = effective.id;
                    Integer idx = paletteIndex.get(id);
                    if (idx == null) {
                        idx = palette.size();
                        paletteIndex.put(id, idx);
                        palette.add(id);
                        counts.add(0);
                    }
                    blockIndices[i] = idx;
                    counts.set(idx, counts.get(idx) + 1);
                }

                if (allAir) {
                    // Empty palette type
                    buf.writeByte(PALETTE_TYPE_EMPTY);
                    // Empty filler section
                    buf.writeByte(PALETTE_TYPE_EMPTY);
                    // Rotation section - use shared method
                    writeRotationSection(buf, section);
                    byte[] localLightData = createCalculatedLightData(chunk, sectionY);
                    buf.writeBytes(localLightData);
                    writeEmptyLightData(buf);
                    // Change counters
                    buf.writeShort(0);
                    buf.writeShort(0);
                } else {
                    int paletteType;
                    if (palette.size() <= 16) {
                        paletteType = PALETTE_TYPE_HALF_BYTE;
                    } else if (palette.size() <= 256) {
                        paletteType = PALETTE_TYPE_BYTE;
                    } else {
                        paletteType = PALETTE_TYPE_SHORT;
                    }

                    buf.writeByte(paletteType);

                    buf.writeShort(palette.size());
                    for (int i = 0; i < palette.size(); i++) {
                        String blockId = palette.get(i);
                        buf.writeByte(i);
                        HytaleBsonPaletteCodec.writeUtf(buf, blockId);
                        buf.writeShort(counts.get(i));
                    }

                    switch (paletteType) {
                        case PALETTE_TYPE_HALF_BYTE:
                            HytaleBsonPaletteCodec.writeHalfByteBlockData(buf, blockIndices);
                            break;
                        case PALETTE_TYPE_BYTE:
                            for (int idx : blockIndices) {
                                buf.writeByte(idx);
                            }
                            break;
                        case PALETTE_TYPE_SHORT:
                            for (int idx : blockIndices) {
                                buf.writeShort(idx);
                            }
                            break;
                    }

                    buf.writeShort(0);
                    buf.writeShort(0);

                    buf.writeByte(PALETTE_TYPE_EMPTY);

                    writeRotationSection(buf, section);

                    byte[] localLightData = createCalculatedLightData(chunk, sectionY);
                    buf.writeBytes(localLightData);
                    writeEmptyLightData(buf);

                    buf.writeShort(0);
                    buf.writeShort(0);
                }
            } else {
                Material[] blocks = section.getBlocks();
                Map<Material, Integer> paletteIndex = new HashMap<>();
                List<Material> palette = new ArrayList<>();
                List<Integer> counts = new ArrayList<>();
                int sectionSize = (blocks != null) ? blocks.length : section.getHytaleBlocks().length;
                int[] blockIndices = new int[sectionSize];
                boolean allAir = true;

                for (int i = 0; i < sectionSize; i++) {
                    Material effective = (blocks != null)
                            ? toBlockMaterial(blocks[i])
                            : Material.AIR;
                    if (effective != Material.AIR) {
                        allAir = false;
                    }
                    Integer idx = paletteIndex.get(effective);
                    if (idx == null) {
                        idx = palette.size();
                        paletteIndex.put(effective, idx);
                        palette.add(effective);
                        counts.add(0);
                    }
                    blockIndices[i] = idx;
                    counts.set(idx, counts.get(idx) + 1);
                }

                if (allAir) {
                // Empty palette type
                buf.writeByte(PALETTE_TYPE_EMPTY);
                // Empty filler section
                buf.writeByte(PALETTE_TYPE_EMPTY);
                // Rotation section - use shared method
                writeRotationSection(buf, section);
                byte[] localLightData = createCalculatedLightData(chunk, sectionY);
                buf.writeBytes(localLightData);
                writeEmptyLightData(buf);
                // Change counters
                buf.writeShort(0);
                buf.writeShort(0);
                } else {
                // Determine palette type based on size
                int paletteType;
                if (palette.size() <= 16) {
                    paletteType = PALETTE_TYPE_HALF_BYTE;
                } else if (palette.size() <= 256) {
                    paletteType = PALETTE_TYPE_BYTE;
                } else {
                    paletteType = PALETTE_TYPE_SHORT;
                }

                buf.writeByte(paletteType);

                // Write palette entries
                buf.writeShort(palette.size());
                for (int i = 0; i < palette.size(); i++) {
                    Material mat = palette.get(i);
                    String hytaleId = HytaleBlockMapping.toHytale(mat);

                    buf.writeByte(i); // internal palette index
                    HytaleBsonPaletteCodec.writeUtf(buf, hytaleId); // block type string
                    buf.writeShort(counts.get(i)); // count of this block type
                }

                // Write raw block data based on palette type
                switch (paletteType) {
                    case PALETTE_TYPE_HALF_BYTE:
                        // HalfByte: 4 bits per block, packed into 16384 bytes
                        HytaleBsonPaletteCodec.writeHalfByteBlockData(buf, blockIndices);
                        break;
                    case PALETTE_TYPE_BYTE:
                        // Byte: 1 byte per block = 32768 bytes
                        for (int idx : blockIndices) {
                            buf.writeByte(idx);
                        }
                        break;
                    case PALETTE_TYPE_SHORT:
                        // Short: 2 bytes per block = 65536 bytes
                        for (int idx : blockIndices) {
                            buf.writeShort(idx);
                        }
                        break;
                }

                // Ticking blocks bitset (empty)
                buf.writeShort(0); // cardinality
                buf.writeShort(0); // bitset array length

                // Filler section (empty)
                buf.writeByte(PALETTE_TYPE_EMPTY);

                // Rotation section - write actual rotations if present
                writeRotationSection(buf, section);

                byte[] localLightData = createCalculatedLightData(chunk, sectionY);
                buf.writeBytes(localLightData);
                writeEmptyLightData(buf);

                // Change counters
                buf.writeShort(0);
                buf.writeShort(0);
                }
            }

            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            doc.put("Data", new BsonBinary(data));
        } finally {
            buf.release();
        }

        return doc;
    }

    /**
     * Write the rotation section for a block section.
     * Rotation values are 0-63, representing rx*16 + ry*4 + rz where each axis is 0-3 (90° increments).
     */
    private static void writeRotationSection(ByteBuf buf, HytaleSection section) {
        if (!section.hasRotations()) {
            // All rotations are 0, write empty palette
            buf.writeByte(PALETTE_TYPE_EMPTY);
            return;
        }

        byte[] rotations = section.getRotations();

        // Build rotation palette
        List<Byte> palette = new ArrayList<>();
        Map<Byte, Integer> paletteIndex = new HashMap<>();
        int[] rotationIndices = new int[rotations.length];
        int[] counts;

        for (int i = 0; i < rotations.length; i++) {
            byte rotation = rotations[i];
            Integer idx = paletteIndex.get(rotation);
            if (idx == null) {
                idx = palette.size();
                paletteIndex.put(rotation, idx);
                palette.add(rotation);
            }
            rotationIndices[i] = idx;
        }

        // Count occurrences for each palette entry
        counts = new int[palette.size()];
        for (int idx : rotationIndices) {
            counts[idx]++;
        }

        // Determine palette type based on size
        int paletteType;
        if (palette.size() <= 16) {
            paletteType = PALETTE_TYPE_HALF_BYTE;
        } else {
            paletteType = PALETTE_TYPE_BYTE;
        }

        buf.writeByte(paletteType);

        // Write palette entries
        // Format: count (short), then for each: index (byte), value (byte), count (short)
        buf.writeShort(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            buf.writeByte(i); // internal palette index
            buf.writeByte(palette.get(i)); // rotation value (0-63)
            buf.writeShort(counts[i]); // count
        }

        // Write raw rotation data based on palette type
        switch (paletteType) {
            case PALETTE_TYPE_HALF_BYTE:
                HytaleBsonPaletteCodec.writeHalfByteBlockData(buf, rotationIndices);
                break;
            case PALETTE_TYPE_BYTE:
                for (int idx : rotationIndices) {
                    buf.writeByte(idx);
                }
                break;
        }
    }

    /**
     * Write empty light data.
     */
    private static void writeEmptyLightData(ByteBuf buf) {
        // ChunkLightData.EMPTY format
        buf.writeShort(0); // changeId
        buf.writeBoolean(false); // hasData
    }

    /**
     * Write uniform full skylight data (sky channel = 15, others = 0).
     */
    private static void writeFullSkyLightData(ByteBuf buf) {
        short value = (short) (0xF << 12);
        buf.writeShort(0); // changeId
        buf.writeBoolean(true); // hasData
        buf.writeInt(17); // octree length
        buf.writeByte(0); // mask: no children
        for (int i = 0; i < 8; i++) {
            buf.writeShort(value);
        }
    }

    /**
     * Write calculated sky light data based on heightmap.
     * Underground blocks (below heightmap) get sky=0, surface/sky blocks get sky=15.
     *
     * @param chunk The chunk containing heightmap data.
     * @param sectionY The section index (0-9).
     */
    private static byte[] createCalculatedLightData(HytaleChunk chunk, int sectionY) {
        // Write empty light data so that Hytale calculates lighting natively.
        // Pre-baked values caused visual artefacts when players interacted with
        // blocks because Hytale's runtime recalculation produced different
        // results from our export-time approximation.
        ByteBuf lightBuffer = ByteBufAllocator.DEFAULT.buffer(3);
        try {
            writeEmptyLightData(lightBuffer);
            byte[] lightData = new byte[lightBuffer.readableBytes()];
            lightBuffer.readBytes(lightData);
            return lightData;
        } finally {
            lightBuffer.release();
        }
    }

    private static void writeCalculatedSkyLightData(ByteBuf buf, HytaleChunk chunk, int sectionY) {
        HytaleChunkLightDataBuilder builder = null;
        try {
            boolean hasAnyLight = false;
            boolean fullSky = true;
            int sectionBaseY = sectionY * HytaleChunk.SECTION_HEIGHT;
            for (int localY = 0; localY < HytaleChunk.SECTION_HEIGHT; localY++) {
                int worldY = sectionBaseY + localY;
                for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                    for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                        int blockLight = chunk.getBlockLightLevel(x, worldY, z);
                        int skyLight;
                        if (worldY >= chunk.getHeight(x, z)) {
                            skyLight = 15;
                        } else {
                            // Blocks below their own heightmap are underground (skyLight=0),
                            // UNLESS they are laterally exposed to sky through an adjacent
                            // shorter column. This prevents pitch-black faces at shorelines
                            // and terrain steps where subsurface blocks face open air/water.
                            skyLight = 0;
                            if ((x > 0 && worldY >= chunk.getHeight(x - 1, z))
                                    || (x < HytaleChunk.CHUNK_SIZE - 1 && worldY >= chunk.getHeight(x + 1, z))
                                    || (z > 0 && worldY >= chunk.getHeight(x, z - 1))
                                    || (z < HytaleChunk.CHUNK_SIZE - 1 && worldY >= chunk.getHeight(x, z + 1))) {
                                skyLight = 13;
                            }
                        }
                        if (blockLight != 0 || skyLight != 0) {
                            hasAnyLight = true;
                        }
                        if (blockLight != 0 || skyLight != 15) {
                            fullSky = false;
                        }
                        if (blockLight != 0 || skyLight != 15) {
                            if (builder == null) {
                                // Default must be FULL_SKYLIGHT so that blocks we
                                // skip (blockLight==0 && skyLight==15) keep their
                                // sky light instead of falling to zero.
                                builder = new HytaleChunkLightDataBuilder((short) 0,
                                        HytaleChunkLightDataBuilder.FULL_SKYLIGHT);
                            }
                            builder.setLight(x, localY, z, blockLight, blockLight, blockLight, skyLight);
                        }
                    }
                }
            }
            if (!hasAnyLight) {
                writeEmptyLightData(buf);
                return;
            }
            if (fullSky) {
                writeFullSkyLightData(buf);
                return;
            }
            builder.serialize(buf);
        } finally {
            if (builder != null) {
                builder.release();
            }
        }
    }

    /**
     * Map fluids out of block palette.
     */
    private static Material toBlockMaterial(Material material) {
        if (material == null || material == Material.AIR) {
            return Material.AIR;
        }
        if (material == Material.WATER || material == Material.LAVA) {
            return Material.AIR;
        }
        return material;
    }

    /**
     * Set a 4-bit level in the packed fluid level data (2 entries per byte).
     */
    private static void setLevelNibble(byte[] levelData, int index, int level) {
        int byteIndex = index >> 1;
        int nibble = level & 0xF;
        if ((index & 1) == 0) {
            levelData[byteIndex] = (byte) ((levelData[byteIndex] & 0xF0) | nibble);
        } else {
            levelData[byteIndex] = (byte) ((levelData[byteIndex] & 0x0F) | (nibble << 4));
        }
    }

    /**
     * Determine a sane default fluid level when explicit level data is missing.
     * In Hytale assets, source fluids use max level 1 while spreading fluids use max level 8.
     */
    private static int defaultFluidLevel(String fluidName) {
        if (fluidName == null || fluidName.isEmpty() || "Empty".equals(fluidName)) {
            return 0;
        }
        return fluidName.endsWith("_Source") ? 1 : 8;
    }

    /**
     * Create FluidSection BSON.
     * Format: { "Data": [paletteType, paletteData..., hasLevelData, levelData?] }
     */
    static BsonDocument createFluidSectionBson(HytaleSection section) {
        BsonDocument doc = new BsonDocument();

        List<String> fluidPalette = section.getFluidPalette();
        byte[] fluidIds = section.getFluidIds();
        byte[] fluidLevels = section.getFluidLevels();
        HytaleBlock[] hytaleBlocks = section.getHytaleBlocks();

        // Also check block materials for water/lava (backward compatibility)
        Material[] blocks = section.getBlocks();
        boolean hasWaterFromBlocks = false;
        boolean hasLavaFromBlocks = false;
        boolean hasHytaleFluids = false;
        if (blocks != null) {
            for (Material block : blocks) {
                if (block == Material.WATER) {
                    hasWaterFromBlocks = true;
                } else if (block == Material.LAVA) {
                    hasLavaFromBlocks = true;
                }
            }
        }

        // Build combined palette
        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>();
        palette.add("Empty");
        paletteIndex.put("Empty", 0);

        // Add fluids from section palette
        for (int i = 1; i < fluidPalette.size(); i++) {
            String fluid = fluidPalette.get(i);
            if (!paletteIndex.containsKey(fluid)) {
                paletteIndex.put(fluid, palette.size());
                palette.add(fluid);
            }
        }

        // Add water/lava if detected in blocks but not in fluid palette
        if (hasWaterFromBlocks && !paletteIndex.containsKey(HytaleBlockMapping.HY_WATER)) {
            paletteIndex.put(HytaleBlockMapping.HY_WATER, palette.size());
            palette.add(HytaleBlockMapping.HY_WATER);
        }
        if (hasLavaFromBlocks && !paletteIndex.containsKey(HytaleBlockMapping.HY_LAVA)) {
            paletteIndex.put(HytaleBlockMapping.HY_LAVA, palette.size());
            palette.add(HytaleBlockMapping.HY_LAVA);
        }
        if (section.hasHytaleBlocks()) {
            for (HytaleBlock block : hytaleBlocks) {
                if (block != null && block.isFluid()) {
                    hasHytaleFluids = true;
                    if (!paletteIndex.containsKey(block.id)) {
                        paletteIndex.put(block.id, palette.size());
                        palette.add(block.id);
                    }
                }
            }
        }

        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            if (palette.size() == 1 && !hasWaterFromBlocks && !hasLavaFromBlocks && !hasHytaleFluids) {
                // Empty palette type - no fluids
                buf.writeByte(PALETTE_TYPE_EMPTY);
                buf.writeBoolean(false);
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Fluid section palette: {}", palette);
                }
                int paletteType = PALETTE_TYPE_HALF_BYTE;
                buf.writeByte(paletteType);

                // Build indices array, combining explicit fluids and block-based water/lava
                int sectionSize = (blocks != null) ? blocks.length : hytaleBlocks.length;
                int[] indices = new int[sectionSize];
                int[] counts = new int[palette.size()];

                for (int i = 0; i < sectionSize; i++) {
                    int idx = 0;

                    // First check explicit fluid storage
                    if (fluidIds[i] != 0 && fluidIds[i] < fluidPalette.size()) {
                        String fluidName = fluidPalette.get(fluidIds[i] & 0xFF);
                        idx = paletteIndex.getOrDefault(fluidName, 0);
                    }
                    // Then check block materials for water/lava
                    else if ((blocks != null) && (blocks[i] == Material.WATER)) {
                        idx = paletteIndex.getOrDefault(HytaleBlockMapping.HY_WATER, 0);
                    }
                    else if ((blocks != null) && (blocks[i] == Material.LAVA)) {
                        idx = paletteIndex.getOrDefault(HytaleBlockMapping.HY_LAVA, 0);
                    }
                    // Finally check Hytale blocks for fluids
                    else if (section.hasHytaleBlocks()) {
                        HytaleBlock block = hytaleBlocks[i];
                        if (block != null && block.isFluid()) {
                            idx = paletteIndex.getOrDefault(block.id, 0);
                        }
                    }

                    indices[i] = idx;
                    counts[idx]++;
                }

                // Write palette entries
                buf.writeShort(palette.size());
                for (int i = 0; i < palette.size(); i++) {
                    buf.writeByte(i);
                    HytaleBsonPaletteCodec.writeUtf(buf, palette.get(i));
                    buf.writeShort((short) counts[i]);
                }

                // Write raw block data (HalfByte)
                HytaleBsonPaletteCodec.writeHalfByteBlockData(buf, indices);

                // Level data
                buf.writeBoolean(true);
                byte[] levelData = new byte[16384];
                for (int i = 0; i < indices.length; i++) {
                    if (indices[i] != 0) {
                        int level = fluidLevels[i] & 0xF;
                        if (level == 0) {
                            String fluidName = (indices[i] < palette.size()) ? palette.get(indices[i]) : null;
                            level = defaultFluidLevel(fluidName);
                        }
                        setLevelNibble(levelData, i, level);
                    }
                }
                buf.writeBytes(levelData);
            }

            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            doc.put("Data", new BsonBinary(data));
        } finally {
            buf.release();
        }

        return doc;
    }
}
