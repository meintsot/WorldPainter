package org.pepsoft.worldpainter.hytale.chunk;

import org.pepsoft.worldpainter.hytale.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.pepsoft.minecraft.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Deserializes Hytale chunk BSON payloads into {@link HytaleChunk} instances.
 *
 * <p>This is the read-side counterpart to {@link HytaleBsonChunkSerializer}. It was
 * extracted from {@link HytaleRegionFile} to separate the IndexedStorageFile
 * file-format protocol from BSON chunk decoding.</p>
 *
 * <p>All methods are static; the class is a stateless utility (aside from
 * package-private diagnostic counters used to gate verbose first-chunk log output).</p>
 */
final class HytaleBsonChunkDeserializer {

    private static final Logger logger = LoggerFactory.getLogger(HytaleBsonChunkDeserializer.class);

    // Palette type constants matching HytaleBsonChunkSerializer
    private static final int PALETTE_TYPE_EMPTY = 0;
    private static final int PALETTE_TYPE_HALF_BYTE = 1;
    private static final int PALETTE_TYPE_BYTE = 2;
    private static final int PALETTE_TYPE_SHORT = 3;
    private static final int SECTION_VOLUME = 32 * 32 * 32; // 32768

    // Diagnostic counters: gate verbose log output for the first few chunks/sections seen
    // by the process. Originally per-HytaleRegionFile instance fields; moved here as
    // static state on extraction. They drive only log verbosity, not behaviour.
    private static int deserializeCount = 0;
    private static int bsonFailCount = 0;
    private static int sectionReadCount = 0;

    private HytaleBsonChunkDeserializer() {
        // Utility class — not instantiable.
    }

    /**
     * Deserialize a chunk from BSON bytes, restoring all block types, fluids,
     * heightmap, tintmap, environments, rotations and entities.
     */
    static HytaleChunk deserializeChunk(byte[] data, int localX, int localZ, int minHeight, int maxHeight) {
        BsonDocument root;
        try {
            root = HytaleBsonChunkSerializer.bytesToBson(data);
        } catch (Exception e) {
            bsonFailCount++;
            logger.error("Failed to parse BSON for chunk {},{} (failure #{}): {}", localX, localZ, bsonFailCount, e.getMessage(), e);
            return null;
        }

        HytaleChunk chunk = new HytaleChunk(localX, localZ, minHeight, maxHeight);

        BsonDocument components = root.containsKey("Components") ? root.getDocument("Components") : root;

        if (deserializeCount < 3) {
            logger.info("Chunk {},{} BSON components: {}", localX, localZ, components.keySet());
        }

        // --- BlockChunk: heightmap + tintmap ---
        if (components.containsKey("BlockChunk")) {
            try {
                readBlockChunk(components.getDocument("BlockChunk"), chunk);
            } catch (Exception e) {
                logger.warn("Error reading BlockChunk for {},{}: {}", localX, localZ, e.getMessage(), e);
            }
        } else {
            logger.warn("Chunk {},{} has no BlockChunk component", localX, localZ);
        }

        // --- ChunkColumn: sections with blocks, fluids, rotations ---
        if (components.containsKey("ChunkColumn")) {
            try {
                readChunkColumn(components.getDocument("ChunkColumn"), chunk);
            } catch (Exception e) {
                logger.warn("Error reading ChunkColumn for {},{}: {}", localX, localZ, e.getMessage(), e);
            }
        } else {
            logger.warn("Chunk {},{} has no ChunkColumn component", localX, localZ);
        }

        // --- EnvironmentChunk ---
        if (components.containsKey("EnvironmentChunk")) {
            try {
                readEnvironmentChunk(components.getDocument("EnvironmentChunk"), chunk);
            } catch (Exception e) {
                logger.warn("Error reading EnvironmentChunk for {},{}: {}", localX, localZ, e.getMessage(), e);
            }
        }

        // --- EntityChunk ---
        if (components.containsKey("EntityChunk")) {
            try {
                readEntityChunk(components.getDocument("EntityChunk"), chunk);
            } catch (Exception e) {
                logger.warn("Error reading EntityChunk for {},{}: {}", localX, localZ, e.getMessage(), e);
            }
        }

        // --- TalePainterMetadata (water tints, spawn overrides, prefab markers) ---
        if (components.containsKey("TalePainterMetadata")) {
            try {
                readTalePainterMetadata(components.getDocument("TalePainterMetadata"), chunk);
            } catch (Exception e) {
                logger.warn("Error reading TalePainterMetadata for {},{}: {}", localX, localZ, e.getMessage(), e);
            }
        }

        // --- BlockHealthChunk (damaged block health values) ---
        if (components.containsKey("BlockHealthChunk")) {
            try {
                readBlockHealthChunk(components.getDocument("BlockHealthChunk"), chunk);
            } catch (Exception e) {
                logger.warn("Error reading BlockHealthChunk for {},{}: {}", localX, localZ, e.getMessage(), e);
            }
        }

        // Log diagnostic info for first few chunks
        if (deserializeCount < 3) {
            int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
            int nonAirBlocks = 0;
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    int h = chunk.getHeight(x, z);
                    if (h < minH) minH = h;
                    if (h > maxH) maxH = h;
                }
            }
            for (int sy = 0; sy < chunk.getSectionCount(); sy++) {
                HytaleBlock[] blocks = chunk.getSections()[sy].getHytaleBlocks();
                if (blocks == null) {
                    continue;
                }
                for (HytaleBlock b : blocks) {
                    if (b != null && !b.isEmpty()) nonAirBlocks++;
                }
            }
            logger.info("Chunk {},{} diagnostics: heightmap range [{}, {}], non-air blocks: {}",
                localX, localZ, minH, maxH, nonAirBlocks);
        }
        deserializeCount++;

        return chunk;
    }

    /**
     * Read BlockChunk BSON (heightmap + tintmap).
     */
    private static void readBlockChunk(BsonDocument blockChunkDoc, HytaleChunk chunk) {
        if (!blockChunkDoc.containsKey("Data")) return;
        byte[] rawData = blockChunkDoc.getBinary("Data").getData();
        ByteBuf buf = Unpooled.wrappedBuffer(rawData);

        try {
            // needsPhysics (boolean)
            buf.readBoolean();

            // Heightmap (ShortBytePalette)
            short[] heights = readShortBytePalette(buf, 1024);
            for (int i = 0; i < 1024; i++) {
                int x = i % 32;
                int z = i / 32;
                chunk.setHeight(x, z, heights[i]);
            }

            // Tintmap (IntBytePalette)
            int[] tints = readIntBytePalette(buf, 1024);
            for (int i = 0; i < 1024; i++) {
                int x = i % 32;
                int z = i / 32;
                chunk.setTint(x, z, tints[i]);
            }
        } finally {
            buf.release();
        }
    }

    /**
     * Read a ShortBytePalette (used for heightmap).
     */
    private static short[] readShortBytePalette(ByteBuf buf, int count) {
        int paletteSize = buf.readShortLE() & 0xFFFF;
        short[] palette = new short[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = buf.readShortLE();
        }
        int bitfieldLen = buf.readIntLE();
        byte[] bitfield = new byte[bitfieldLen];
        buf.readBytes(bitfield);

        short[] values = new short[count];
        for (int i = 0; i < count; i++) {
            int idx = readBitFieldValue(bitfield, i, 10);
            values[i] = (idx < paletteSize) ? palette[idx] : 0;
        }
        return values;
    }

    /**
     * Read an IntBytePalette (used for tintmap).
     */
    private static int[] readIntBytePalette(ByteBuf buf, int count) {
        int paletteSize = buf.readShortLE() & 0xFFFF;
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = buf.readIntLE();
        }
        int bitfieldLen = buf.readIntLE();
        byte[] bitfield = new byte[bitfieldLen];
        buf.readBytes(bitfield);

        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            int idx = readBitFieldValue(bitfield, i, 10);
            values[i] = (idx < paletteSize) ? palette[idx] : 0;
        }
        return values;
    }

    /**
     * Read a value from a bit field array at the given index with the specified bits per entry.
     */
    private static int readBitFieldValue(byte[] array, int index, int bits) {
        int bitIndex = index * bits;
        int value = 0;
        for (int i = 0; i < bits; i++) {
            int arrIndex = (bitIndex + i) / 8;
            int bitOffset = (bitIndex + i) % 8;
            if (arrIndex < array.length && ((array[arrIndex] >> bitOffset) & 1) == 1) {
                value |= (1 << i);
            }
        }
        return value;
    }

    /**
     * Read ChunkColumn BSON (sections with block/fluid data).
     */
    private static void readChunkColumn(BsonDocument chunkColDoc, HytaleChunk chunk) {
        if (!chunkColDoc.containsKey("Sections")) return;
        BsonArray sections = chunkColDoc.getArray("Sections");

        HytaleSection[] chunkSections = chunk.getSections();
        int sectionCount = Math.min(sections.size(), chunkSections.length);

        for (int i = 0; i < sectionCount; i++) {
            BsonDocument holder = sections.get(i).asDocument();
            if (!holder.containsKey("Components")) continue;
            BsonDocument sectionComponents = holder.getDocument("Components");

            int sectionBaseY = i * HytaleChunk.SECTION_HEIGHT;

            // Read Block section
            if (sectionComponents.containsKey("Block")) {
                readBlockSection(sectionComponents.getDocument("Block"), chunkSections[i], sectionBaseY);
            }

            // Read Fluid section
            if (sectionComponents.containsKey("Fluid")) {
                readFluidSection(sectionComponents.getDocument("Fluid"), chunkSections[i]);
            }
        }
    }

    /**
     * Read a Block section from BSON: palette + block indices + rotations.
     */
    private static void readBlockSection(BsonDocument blockDoc, HytaleSection section, int sectionBaseY) {
        if (!blockDoc.containsKey("Data")) return;

        // Check if there's a version field
        int version = blockDoc.containsKey("Version") ? blockDoc.getInt32("Version").getValue() : -1;
        byte[] rawData = blockDoc.getBinary("Data").getData();
        ByteBuf buf = Unpooled.wrappedBuffer(rawData);

        if (sectionReadCount < 5) {
            logger.info("Block section at baseY={}: version={}, data length={}", sectionBaseY, version, rawData.length);
        }

        try {
            // Block migration version
            int migrationVersion = buf.readInt();

            int paletteType = buf.readByte() & 0xFF;
            if (sectionReadCount < 5) {
                logger.info("  migrationVersion={}, paletteType={}, remaining={}", migrationVersion, paletteType, buf.readableBytes());
            }
            if (paletteType == PALETTE_TYPE_EMPTY) {
                // Section is empty (all air) - skip remaining fields
                skipEmptySectionTrailer(buf);
                sectionReadCount++;
                return;
            }

            // Read palette entries keyed by internal ID (which may be non-sequential
            // in real Hytale worlds where blocks have been added/removed)
            int paletteSize = buf.readShort() & 0xFFFF;
            Map<Integer, String> palette = new HashMap<>(paletteSize);
            for (int p = 0; p < paletteSize; p++) {
                int internalId = buf.readByte() & 0xFF;
                String blockName = readUtf(buf);
                int count = buf.readShort() & 0xFFFF; // count (unused on read)
                palette.put(internalId, blockName);
                if (sectionReadCount < 2) {
                    logger.info("    palette[{}] = '{}' (count={})", internalId, blockName, count);
                }
            }

            if (sectionReadCount < 5) {
                logger.info("  paletteSize={}, paletteType={}, remaining before blockData={}", paletteSize, paletteType, buf.readableBytes());
            }

            // Read block indices based on palette type
            int[] blockIndices = new int[SECTION_VOLUME];
            switch (paletteType) {
                case PALETTE_TYPE_HALF_BYTE:
                    readHalfByteData(buf, blockIndices);
                    break;
                case PALETTE_TYPE_BYTE:
                    for (int j = 0; j < SECTION_VOLUME; j++) {
                        blockIndices[j] = buf.readByte() & 0xFF;
                    }
                    break;
                case PALETTE_TYPE_SHORT:
                    for (int j = 0; j < SECTION_VOLUME; j++) {
                        blockIndices[j] = buf.readShort() & 0xFFFF;
                    }
                    break;
            }

            if (sectionReadCount < 5) {
                logger.info("  remaining after blockData={}", buf.readableBytes());
            }

            // Pre-build Material cache for palette entries (hytale:-prefixed for round-trip)
            Map<Integer, Material> paletteMaterials = new HashMap<>(paletteSize);
            for (Map.Entry<Integer, String> entry : palette.entrySet()) {
                paletteMaterials.put(entry.getKey(), "Empty".equals(entry.getValue())
                    ? Material.AIR
                    : Material.get("hytale:" + entry.getValue()));
            }

            // Set blocks on the section using native HytaleBlock IDs
            int blocksSet = 0;
            int missingPaletteEntries = 0;
            for (int idx = 0; idx < SECTION_VOLUME; idx++) {
                int internalId = blockIndices[idx];
                String blockId = palette.get(internalId);
                if (blockId == null) {
                    missingPaletteEntries++;
                } else if (!"Empty".equals(blockId)) {
                    // Decode index to coordinates: y << 10 | z << 5 | x
                    int y = idx >> 10;
                    int z = (idx >> 5) & 31;
                    int x = idx & 31;
                    section.setHytaleBlock(x, y, z, HytaleBlock.of(blockId));
                    section.setMaterial(x, y, z, paletteMaterials.get(internalId));
                    blocksSet++;
                }
            }

            if (sectionReadCount < 5 || missingPaletteEntries > 0) {
                logger.info("  blocksSet={}, missingPaletteEntries={}", blocksSet, missingPaletteEntries);
            }

            // Read ticking blocks bitset
            int tickCardinality = buf.readShort() & 0xFFFF;
            int tickBitsetLen = buf.readShort() & 0xFFFF;
            buf.skipBytes(tickBitsetLen * 8); // longs

            if (sectionReadCount < 5) {
                logger.info("  tickCardinality={}, tickBitsetLen={}, remaining after ticking={}", tickCardinality, tickBitsetLen, buf.readableBytes());
            }

            // Read filler section palette type
            // Filler uses short keys (ByteBuf::writeShort), not UTF strings
            int fillerType = buf.readByte() & 0xFF;
            if (fillerType != PALETTE_TYPE_EMPTY) {
                if (sectionReadCount < 5) {
                    logger.info("  filler paletteType={}, remaining before skip={}", fillerType, buf.readableBytes());
                }
                skipFillerPaletteSection(buf, fillerType);
            }

            if (sectionReadCount < 5) {
                logger.info("  remaining before rotation={}", buf.readableBytes());
            }

            // Read rotation section
            readRotationSection(buf, section);

            if (sectionReadCount < 5) {
                logger.info("  remaining before light={}", buf.readableBytes());
            }

            readSectionLightData(buf, section);

            if (sectionReadCount < 5) {
                logger.info("  remaining after full section read={}", buf.readableBytes());
            }

        } catch (Exception e) {
            logger.warn("Error reading block section data at baseY={}: {}", sectionBaseY, e.getMessage(), e);
        } finally {
            buf.release();
        }
        sectionReadCount++;
    }

    /**
     * Read rotation section from the buffer.
     * Rotation palette uses byte keys (ByteBuf::writeByte/readUnsignedByte).
     */
    private static void readRotationSection(ByteBuf buf, HytaleSection section) {
        if (buf.readableBytes() < 1) return;
        int rotType = buf.readByte() & 0xFF;
        if (rotType == PALETTE_TYPE_EMPTY) return;

        // Read palette keyed by internal ID (may be non-sequential)
        int rotPaletteSize = buf.readShort() & 0xFFFF;
        Map<Integer, Byte> rotPalette = new HashMap<>(rotPaletteSize);
        for (int p = 0; p < rotPaletteSize; p++) {
            int internalId = buf.readByte() & 0xFF;
            byte rotationValue = buf.readByte(); // rotation value (external key, 1 byte)
            buf.readShort(); // count
            rotPalette.put(internalId, rotationValue);
        }

        int[] rotIndices = new int[SECTION_VOLUME];
        switch (rotType) {
            case PALETTE_TYPE_HALF_BYTE:
                readHalfByteData(buf, rotIndices);
                break;
            case PALETTE_TYPE_BYTE:
                for (int j = 0; j < SECTION_VOLUME; j++) {
                    rotIndices[j] = buf.readByte() & 0xFF;
                }
                break;
        }

        for (int idx = 0; idx < SECTION_VOLUME; idx++) {
            int internalId = rotIndices[idx];
            Byte rotValue = rotPalette.get(internalId);
            if (rotValue != null && rotValue != 0) {
                int y = idx >> 10;
                int z = (idx >> 5) & 31;
                int x = idx & 31;
                section.setRotation(x, y, z, rotValue & 0x3F);
            }
        }
    }

    /**
     * Read half-byte (nibble) packed data.
     * Hytale's BitUtil packs even indices into the high nibble and odd indices
     * into the low nibble.
     */
    private static void readHalfByteData(ByteBuf buf, int[] output) {
        for (int i = 0; i < output.length; i += 2) {
            int b = buf.readByte() & 0xFF;
            output[i] = (b >> 4) & 0x0F;         // Even → HIGH nibble
            if (i + 1 < output.length) {
                output[i + 1] = b & 0x0F;        // Odd → LOW nibble
            }
        }
    }

    /**
     * Skip the trailing fields after an empty palette section.
     */
    private static void skipEmptySectionTrailer(ByteBuf buf) {
        // Filler section (uses short keys)
        if (buf.readableBytes() >= 1) {
            int fillerType = buf.readByte() & 0xFF;
            if (fillerType != PALETTE_TYPE_EMPTY) {
                skipFillerPaletteSection(buf, fillerType);
            }
        }
        // Rotation section (uses byte keys)
        if (buf.readableBytes() >= 1) {
            int rotType = buf.readByte() & 0xFF;
            if (rotType != PALETTE_TYPE_EMPTY) {
                skipRotationPaletteSection(buf, rotType);
            }
        }
        readSectionLightData(buf, null);
    }

    private static void readSectionLightData(ByteBuf buf, HytaleSection section) {
        ByteBuf localLight = null;
        ByteBuf globalLight = null;
        try {
            localLight = readChunkLightData(buf);
            globalLight = readChunkLightData(buf);
            if (buf.readableBytes() >= 2) {
                buf.readShort();
            }
            if (buf.readableBytes() >= 2) {
                buf.readShort();
            }
            if (section == null) {
                return;
            }
            ByteBuf effectiveLight = (globalLight != null) ? globalLight : localLight;
            if (effectiveLight == null) {
                return;
            }
            for (int idx = 0; idx < SECTION_VOLUME; idx++) {
                short light = getLightRaw(effectiveLight, idx, 0, 0);
                int y = idx >> 10;
                int z = (idx >> 5) & 31;
                int x = idx & 31;
                int red = light & 0xF;
                int green = (light >> 4) & 0xF;
                int blue = (light >> 8) & 0xF;
                int sky = (light >> 12) & 0xF;
                section.setBlockLight(x, y, z, Math.max(red, Math.max(green, blue)));
                section.setSkyLight(x, y, z, sky);
            }
        } finally {
            if (localLight != null) {
                localLight.release();
            }
            if (globalLight != null) {
                globalLight.release();
            }
        }
    }

    private static ByteBuf readChunkLightData(ByteBuf buf) {
        if (buf.readableBytes() < 3) {
            return null;
        }
        buf.readShort();
        boolean hasLight = buf.readBoolean();
        if (!hasLight) {
            return null;
        }
        if (buf.readableBytes() < 4) {
            return null;
        }
        int length = buf.readInt();
        if (buf.readableBytes() < length) {
            buf.skipBytes(buf.readableBytes());
            return null;
        }
        ByteBuf from = buf.readSlice(length);
        ByteBuf flat = Unpooled.buffer(Math.max(17, length * 2));
        flat.writerIndex(17);
        deserializeLightOctree(from, flat, 0, 0);
        return flat;
    }

    private static int deserializeLightOctree(ByteBuf from, ByteBuf to, int position, int segmentIndex) {
        byte mask = from.readByte();
        to.setByte(position * 17, mask);
        for (int i = 0; i < 8; i++) {
            int value;
            if ((mask & (1 << i)) != 0) {
                int nextSegmentIndex = ++segmentIndex;
                to.writerIndex((nextSegmentIndex + 1) * 17);
                value = nextSegmentIndex;
                segmentIndex = deserializeLightOctree(from, to, value, nextSegmentIndex);
            } else {
                value = from.readShort() & 0xFFFF;
            }
            to.setShort(position * 17 + 1 + i * 2, value);
        }
        return segmentIndex;
    }

    private static short getLightRaw(ByteBuf flatTree, int index, int segmentIndex, int depth) {
        int position = segmentIndex * 17;
        byte mask = flatTree.getByte(position);
        int childIndex = (index >> (12 - depth)) & 7;
        int childOffset = position + 1 + childIndex * 2;
        int value = flatTree.getUnsignedShort(childOffset);
        if ((mask & (1 << childIndex)) != 0) {
            return getLightRaw(flatTree, index, value, depth + 3);
        }
        return (short) value;
    }

    /**
     * Skip a filler palette section in the buffer.
     * Filler sections use short (2-byte) keys: byte(internalId) + short(fillerValue) + short(count).
     */
    private static void skipFillerPaletteSection(ByteBuf buf, int paletteType) {
        try {
            int paletteSize = buf.readShort() & 0xFFFF;
            for (int p = 0; p < paletteSize; p++) {
                buf.readByte();  // internal ID
                buf.readShort(); // filler value (short key)
                buf.readShort(); // count
            }
            skipPaletteData(buf, paletteType);
        } catch (Exception e) {
            logger.warn("Error skipping filler palette section: {}", e.getMessage());
        }
    }

    /**
     * Skip a rotation palette section in the buffer.
     * Rotation sections use byte (1-byte) keys: byte(internalId) + byte(rotationValue) + short(count).
     */
    private static void skipRotationPaletteSection(ByteBuf buf, int paletteType) {
        try {
            int paletteSize = buf.readShort() & 0xFFFF;
            for (int p = 0; p < paletteSize; p++) {
                buf.readByte(); // internal ID
                buf.readByte(); // rotation value (byte key)
                buf.readShort(); // count
            }
            skipPaletteData(buf, paletteType);
        } catch (Exception e) {
            logger.warn("Error skipping rotation palette section: {}", e.getMessage());
        }
    }

    /**
     * Skip the block data portion of a palette section.
     */
    private static void skipPaletteData(ByteBuf buf, int paletteType) {
        switch (paletteType) {
            case PALETTE_TYPE_HALF_BYTE:
                buf.skipBytes(SECTION_VOLUME / 2);
                break;
            case PALETTE_TYPE_BYTE:
                buf.skipBytes(SECTION_VOLUME);
                break;
            case PALETTE_TYPE_SHORT:
                buf.skipBytes(SECTION_VOLUME * 2);
                break;
        }
    }

    /**
     * Read Fluid section from BSON.
     */
    private static void readFluidSection(BsonDocument fluidDoc, HytaleSection section) {
        if (!fluidDoc.containsKey("Data")) return;
        byte[] rawData = fluidDoc.getBinary("Data").getData();
        ByteBuf buf = Unpooled.wrappedBuffer(rawData);

        try {
            int paletteType = buf.readByte() & 0xFF;
            if (paletteType == PALETTE_TYPE_EMPTY) {
                buf.readBoolean(); // hasLevelData flag
                return;
            }

            // Read fluid palette keyed by internal ID (may be non-sequential)
            int paletteSize = buf.readShort() & 0xFFFF;
            Map<Integer, String> palette = new HashMap<>(paletteSize);
            for (int p = 0; p < paletteSize; p++) {
                int internalId = buf.readByte() & 0xFF;
                String fluidName = readUtf(buf);
                buf.readShort(); // count
                palette.put(internalId, fluidName);
            }

            // Read fluid indices based on palette type
            int[] fluidIndices = new int[SECTION_VOLUME];
            switch (paletteType) {
                case PALETTE_TYPE_HALF_BYTE:
                    readHalfByteData(buf, fluidIndices);
                    break;
                case PALETTE_TYPE_BYTE:
                    for (int j = 0; j < SECTION_VOLUME; j++) {
                        fluidIndices[j] = buf.readByte() & 0xFF;
                    }
                    break;
                case PALETTE_TYPE_SHORT:
                    for (int j = 0; j < SECTION_VOLUME; j++) {
                        fluidIndices[j] = buf.readShort() & 0xFFFF;
                    }
                    break;
            }

            // Read level data
            boolean hasLevelData = buf.readBoolean();
            byte[] levelData = null;
            if (hasLevelData) {
                levelData = new byte[SECTION_VOLUME / 2];
                buf.readBytes(levelData);
            }

            // Set fluids on section
            for (int idx = 0; idx < SECTION_VOLUME; idx++) {
                int internalId = fluidIndices[idx];
                String fluidName = palette.get(internalId);
                if (fluidName != null && !"Empty".equals(fluidName)) {
                    int level = 1; // default
                    if (levelData != null) {
                        int byteIndex = idx >> 1;
                        if (byteIndex < levelData.length) {
                            level = ((idx & 1) == 0)
                                ? (levelData[byteIndex] & 0x0F)
                                : ((levelData[byteIndex] >> 4) & 0x0F);
                        }
                        if (level == 0) level = 1;
                    }
                    int y = idx >> 10;
                    int z = (idx >> 5) & 31;
                    int x = idx & 31;
                    section.setFluid(x, y, z, fluidName, level);
                }
            }
        } catch (Exception e) {
            logger.warn("Error reading fluid section: {}", e.getMessage(), e);
        } finally {
            buf.release();
        }
    }

    /**
     * Read EnvironmentChunk from BSON.
     */
    private static void readEnvironmentChunk(BsonDocument envDoc, HytaleChunk chunk) {
        if (!envDoc.containsKey("Data")) return;
        byte[] rawData = envDoc.getBinary("Data").getData();
        ByteBuf buf = Unpooled.wrappedBuffer(rawData);

        try {
            int envCount = buf.readInt();
            String[] environments = new String[envCount];
            for (int i = 0; i < envCount; i++) {
                buf.readInt(); // id
                environments[i] = readUtfNetty(buf);
            }

            for (int i = 0; i < 1024; i++) {
                int maxYCount = buf.readInt();
                // Skip maxYs
                for (int j = 0; j < maxYCount; j++) {
                    buf.readInt();
                }
                // Read values (maxYCount + 1 of them)
                int valueCount = maxYCount + 1;
                int envId = buf.readInt();
                // Skip remaining values for multi-layer columns
                for (int j = 1; j < valueCount; j++) {
                    buf.readInt();
                }
                if (envId >= 0 && envId < environments.length) {
                    int x = i % 32;
                    int z = i / 32;
                    chunk.setEnvironment(x, z, environments[envId]);
                }
            }
        } catch (Exception e) {
            logger.debug("Error reading environment chunk: {}", e.getMessage());
        } finally {
            buf.release();
        }
    }

    /**
     * Read EntityChunk from BSON.
     */
    private static void readEntityChunk(BsonDocument entityDoc, HytaleChunk chunk) {
        if (!entityDoc.containsKey("Entities")) return;
        BsonArray entities = entityDoc.getArray("Entities");
        for (BsonValue entityVal : entities) {
            try {
                BsonDocument entityBson = entityVal.asDocument();
                HytaleEntity entity = HytaleEntity.fromBson(entityBson);
                if (entity != null) {
                    chunk.addHytaleEntity(entity);
                }
            } catch (Exception e) {
                logger.debug("Error reading entity: {}", e.getMessage());
            }
        }
    }

    /**
     * Read TalePainterMetadata BSON component: water tints, spawn overrides, prefab markers, painted biomes and
     * auto-vegetation flags (TP-125).
     */
    private static void readTalePainterMetadata(BsonDocument metaDoc, HytaleChunk chunk) {
        // Water Tints: { "x,z": "hexColor", ... }
        if (metaDoc.containsKey("WaterTints")) {
            BsonDocument tintDoc = metaDoc.getDocument("WaterTints");
            for (String key : tintDoc.keySet()) {
                String[] parts = key.split(",");
                if (parts.length == 2) {
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[1]);
                    chunk.setWaterTint(x, z, tintDoc.getString(key).getValue());
                }
            }
        }

        // Spawn Overrides: [ { x, z, density?, tag? }, ... ]
        if (metaDoc.containsKey("SpawnOverrides")) {
            for (BsonValue val : metaDoc.getArray("SpawnOverrides")) {
                BsonDocument entry = val.asDocument();
                int x = entry.getInt32("x").getValue();
                int z = entry.getInt32("z").getValue();
                if (entry.containsKey("density")) {
                    chunk.setSpawnDensity(x, z, (float) entry.getDouble("density").getValue());
                }
                if (entry.containsKey("tag")) {
                    chunk.setSpawnTag(x, z, entry.getString("tag").getValue());
                }
            }
        }

        // Prefab Markers: [ { x, y, z, category, path, rotation? }, ... ]
        if (metaDoc.containsKey("PrefabMarkers")) {
            for (BsonValue val : metaDoc.getArray("PrefabMarkers")) {
                BsonDocument entry = val.asDocument();
                double rotation = entry.containsKey("rotation") ? entry.getDouble("rotation").getValue() : 0.0;
                chunk.addPrefabMarker(
                    entry.getInt32("x").getValue(),
                    entry.getInt32("y").getValue(),
                    entry.getInt32("z").getValue(),
                    entry.getString("category").getValue(),
                    entry.getString("path").getValue(),
                    rotation
                );
            }
        }

        // Painted Biomes (TP-125): [ { x, z, biome }, ... ]
        if (metaDoc.containsKey("PaintedBiomes")) {
            for (BsonValue val : metaDoc.getArray("PaintedBiomes")) {
                BsonDocument entry = val.asDocument();
                chunk.setPaintedBiomeName(
                    entry.getInt32("x").getValue(),
                    entry.getInt32("z").getValue(),
                    entry.getString("biome").getValue()
                );
            }
        }

        // Auto Vegetation (TP-125): 1024-column bitset, 128 bytes
        if (metaDoc.containsKey("AutoVegetation")) {
            byte[] bits = metaDoc.getBinary("AutoVegetation").getData();
            for (int i = 0; i < HytaleChunk.CHUNK_SIZE * HytaleChunk.CHUNK_SIZE && (i >> 3) < bits.length; i++) {
                if ((bits[i >> 3] & (1 << (i & 7))) != 0) {
                    chunk.setAutoVegetation(i % HytaleChunk.CHUNK_SIZE, i / HytaleChunk.CHUNK_SIZE, true);
                }
            }
        }
    }

    /**
     * Read BlockHealthChunk BSON component: damaged block health values.
     * Format (version 2): byte version, int count, then per entry: int x, int y, int z, float health, long lastDamageTime.
     */
    private static void readBlockHealthChunk(BsonDocument healthDoc, HytaleChunk chunk) {
        if (!healthDoc.containsKey("Data")) {
            return;
        }
        byte[] rawData = healthDoc.getBinary("Data").getData();
        ByteBuf buf = Unpooled.wrappedBuffer(rawData);
        try {
            int version = buf.readByte() & 0xFF;
            if (version < 2) {
                return; // Unsupported version
            }
            int healthMapSize = buf.readInt();
            for (int i = 0; i < healthMapSize; i++) {
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                float health = buf.readFloat();
                long lastDamageTime = buf.readLong();
                chunk.setBlockHealth(x, y, z, health, lastDamageTime);
            }
            // Skip fragilityMap (not used)
        } finally {
            buf.release();
        }
    }

    /**
     * Read UTF-8 string in Hytale format (short length prefix).
     */
    private static String readUtf(ByteBuf buf) {
        int len = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Read UTF-8 string with Netty-style int length prefix (used in environment serialization).
     */
    private static String readUtfNetty(ByteBuf buf) {
        // The environment serializer uses writeUtf which writes short length prefix
        int len = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
