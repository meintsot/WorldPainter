package org.pepsoft.worldpainter.hytale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.BsonBinaryWriter;
import org.bson.BsonWriter;
import org.pepsoft.minecraft.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes WorldPainter chunks to Hytale's expected BSON format.
 * 
 * Hytale chunk structure (from decompiled sources):
 * - Root document has "Components" field containing all component data
 * - Components include: WorldChunk, BlockChunk, ChunkColumn, EntityChunk, etc.
 * - Each component is serialized according to its BuilderCodec
 * 
 * Key component formats:
 * - BlockChunk (version 3): Contains heightmap, tintmap data
 * - ChunkColumn: Contains array of 10 section holders
 * - BlockSection (version 6): Contains palette-based block data
 */
public class HytaleBsonChunkSerializer {
    
    private static final Logger logger = LoggerFactory.getLogger(HytaleBsonChunkSerializer.class);
    
    // Codec versions from decompiled sources
    private static final int BLOCK_CHUNK_VERSION = 3;
    private static final int BLOCK_SECTION_VERSION = 6;
    
    // Component IDs from LegacyModule registrations
    private static final String COMP_WORLD_CHUNK = "WorldChunk";
    private static final String COMP_BLOCK_CHUNK = "BlockChunk";
    private static final String COMP_ENTITY_CHUNK = "EntityChunk";
    private static final String COMP_BLOCK_COMPONENT_CHUNK = "BlockComponentChunk";
    private static final String COMP_ENVIRONMENT_CHUNK = "EnvironmentChunk";
    private static final String COMP_CHUNK_COLUMN = "ChunkColumn";
    private static final String COMP_BLOCK_HEALTH_CHUNK = "BlockHealthChunk";
    private static final String COMP_WP_METADATA = "WorldPainterMetadata";
    private static final String COMP_BLOCK_SECTION = "Block";
    private static final String COMP_FLUID_SECTION = "Fluid";
    private static final String COMP_CHUNK_SECTION = "ChunkSection";
    private static final String COMP_BLOCK_PHYSICS = "BlockPhysics";
    
    // Palette type ordinals from PaletteType enum
    private static final int PALETTE_TYPE_EMPTY = 0;
    private static final int PALETTE_TYPE_HALF_BYTE = 1;
    private static final int PALETTE_TYPE_BYTE = 2;
    private static final int PALETTE_TYPE_SHORT = 3;
    
    private static final BsonDocumentCodec CODEC = new BsonDocumentCodec();
    private static final EncoderContext ENCODER_CONTEXT = EncoderContext.builder().build();
    
    /**
     * Serialize a HytaleChunk to BSON bytes that Hytale can read.
     */
    public static byte[] serializeChunk(HytaleChunk chunk) {
        BsonDocument root = new BsonDocument();
        BsonDocument components = new BsonDocument();
        
        // Add BlockComponentChunk component (empty for terrain-only export)
        components.put(COMP_BLOCK_COMPONENT_CHUNK, createBlockComponentChunkBson());
        
        // Add ChunkColumn component with sections
        components.put(COMP_CHUNK_COLUMN, createChunkColumnBson(chunk));
        
        // Add WorldChunk component (minimal - just needs to exist)
        components.put(COMP_WORLD_CHUNK, createWorldChunkBson());
        
        // Add BlockHealthChunk component
        components.put(COMP_BLOCK_HEALTH_CHUNK, createBlockHealthChunkBson(chunk));
        
        // Add EnvironmentChunk component
        components.put(COMP_ENVIRONMENT_CHUNK, createEnvironmentChunkBson(chunk));
        
        // Add BlockChunk component (heightmap and tintmap)
        components.put(COMP_BLOCK_CHUNK, createBlockChunkBson(chunk));
        
        // Add EntityChunk component
        components.put(COMP_ENTITY_CHUNK, createEntityChunkBson(chunk));
        
        // Add WorldPainter metadata (water tints, spawn density, prefab markers)
        BsonDocument wpMeta = createWorldPainterMetadataBson(chunk);
        if (wpMeta != null) {
            components.put(COMP_WP_METADATA, wpMeta);
        }
        
        root.put("Components", components);
        
        return bsonToBytes(root);
    }
    
    /**
     * Create WorldChunk BSON (minimal - codec has no data fields in version 0)
     */
    private static BsonDocument createWorldChunkBson() {
        // WorldChunk.CODEC has no versioned data fields
        return new BsonDocument();
    }
    
    /**
     * Create BlockChunk BSON with heightmap and tintmap.
     * Format from BlockChunk.CODEC (version 3):
     * - "Version": 3
     * - "Data": byte array containing [needsPhysics:boolean, height:ShortBytePalette, tint:IntBytePalette]
     */
    private static BsonDocument createBlockChunkBson(HytaleChunk chunk) {
        BsonDocument doc = new BsonDocument();
        doc.put("Version", new BsonInt32(BLOCK_CHUNK_VERSION));
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            // needsPhysics (boolean)
            buf.writeBoolean(false);
            
            // height (ShortBytePalette) - 32x32 heightmap
            // ShortBytePalette format: palette size + palette entries + indices
            short[] heightmap = chunk.getHeightmap();
            writeShortBytePalette(buf, heightmap);
            
            // tint (IntBytePalette) - tint colors from chunk data
            int[] tintmap = chunk.getTints();
            writeIntBytePalette(buf, tintmap);
            
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            doc.put("Data", new BsonBinary(data));
        } finally {
            buf.release();
        }
        
        return doc;
    }
    
    /**
     * Write a ShortBytePalette (used for heightmap).
     * Format: palette optimization based on unique values
     */
    private static void writeShortBytePalette(ByteBuf buf, short[] values) {
        // Find unique values for palette
        List<Short> palette = new ArrayList<>();
        for (short v : values) {
            if (!palette.contains(v)) {
                palette.add(v);
            }
        }
        
        // Build indices for 32x32 (1024 entries)
        int[] indices = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            indices[i] = palette.indexOf(values[i]);
        }
        
        // Write palette (little-endian like ShortBytePalette)
        buf.writeShortLE(palette.size());
        for (short v : palette) {
            buf.writeShortLE(v);
        }
        
        // Write BitFieldArr (10 bits * 1024 = 1280 bytes)
        byte[] bitfield = buildBitFieldArray(10, 1024, indices);
        buf.writeIntLE(bitfield.length);
        buf.writeBytes(bitfield);
    }
    
    /**
     * Write an IntBytePalette (used for tintmap).
     */
    private static void writeIntBytePalette(ByteBuf buf, int[] values) {
        // Find unique values for palette
        List<Integer> palette = new ArrayList<>();
        for (int v : values) {
            if (!palette.contains(v)) {
                palette.add(v);
            }
        }
        
        int[] indices = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            indices[i] = palette.indexOf(values[i]);
        }
        
        // Write palette (little-endian like IntBytePalette)
        buf.writeShortLE(palette.size());
        for (int v : palette) {
            buf.writeIntLE(v);
        }
        
        // Write BitFieldArr (10 bits * 1024 = 1280 bytes)
        byte[] bitfield = buildBitFieldArray(10, 1024, indices);
        buf.writeIntLE(bitfield.length);
        buf.writeBytes(bitfield);
    }

    /**
     * Build a BitFieldArr byte array with given bits per entry.
     */
    private static byte[] buildBitFieldArray(int bits, int length, int[] values) {
        int byteLength = (length * bits) / 8;
        byte[] array = new byte[byteLength];
        for (int index = 0; index < length; index++) {
            int value = values[index];
            int bitIndex = index * bits;
            for (int i = 0; i < bits; i++) {
                int bit = (value >> i) & 1;
                int arrIndex = (bitIndex + i) / 8;
                int bitOffset = (bitIndex + i) % 8;
                if (bit == 0) {
                    array[arrIndex] = (byte) (array[arrIndex] & ~(1 << bitOffset));
                } else {
                    array[arrIndex] = (byte) (array[arrIndex] | (1 << bitOffset));
                }
            }
        }
        return array;
    }
    
    /**
     * Create EntityChunk BSON with native Hytale entities.
     * Format: { "Entities": [array of entity holder BSON] }
     * 
     * Each entity holder contains:
     * - "Value": { "EntityType": string, "Components": { ... } }
     * 
     * @param chunk The chunk containing entities to serialize.
     * @return BSON document for entity chunk data.
     */
    private static BsonDocument createEntityChunkBson(HytaleChunk chunk) {
        BsonDocument doc = new BsonDocument();
        BsonArray entities = new BsonArray();
        
        // Serialize native Hytale entities
        for (HytaleEntity entity : chunk.getHytaleEntities()) {
            entities.add(entity.toBson());
        }
        
        doc.put("Entities", entities);
        return doc;
    }

    /**
     * Create WorldPainter metadata BSON with water tints, spawn configuration,
     * and prefab markers. Returns null if there's no custom data to write.
     *
     * <p>This component is stored as a custom BSON document that Hytale server
     * plugins can read to apply WorldPainter-specific features.</p>
     */
    private static BsonDocument createWorldPainterMetadataBson(HytaleChunk chunk) {
        boolean hasData = false;
        BsonDocument doc = new BsonDocument();

        // ── Water Tints ──
        String[] waterTints = chunk.getWaterTints();
        BsonDocument tintDoc = new BsonDocument();
        for (int i = 0; i < waterTints.length; i++) {
            if (waterTints[i] != null) {
                int x = i % HytaleChunk.CHUNK_SIZE;
                int z = i / HytaleChunk.CHUNK_SIZE;
                tintDoc.put(x + "," + z, new BsonString(waterTints[i]));
                hasData = true;
            }
        }
        if (!tintDoc.isEmpty()) {
            doc.put("WaterTints", tintDoc);
        }

        // ── Spawn Densities ──
        float[] spawnDensities = chunk.getSpawnDensities();
        String[] spawnTags = chunk.getSpawnTags();
        BsonArray spawnArr = new BsonArray();
        for (int i = 0; i < spawnDensities.length; i++) {
            if (spawnDensities[i] >= 0.0f || spawnTags[i] != null) {
                int x = i % HytaleChunk.CHUNK_SIZE;
                int z = i / HytaleChunk.CHUNK_SIZE;
                BsonDocument entry = new BsonDocument();
                entry.put("x", new BsonInt32(x));
                entry.put("z", new BsonInt32(z));
                if (spawnDensities[i] >= 0.0f) {
                    entry.put("density", new BsonDouble(spawnDensities[i]));
                }
                if (spawnTags[i] != null) {
                    entry.put("tag", new BsonString(spawnTags[i]));
                }
                spawnArr.add(entry);
                hasData = true;
            }
        }
        if (!spawnArr.isEmpty()) {
            doc.put("SpawnOverrides", spawnArr);
        }

        // ── Prefab Markers ──
        List<HytaleChunk.PrefabMarker> prefabs = chunk.getPrefabMarkers();
        if (!prefabs.isEmpty()) {
            BsonArray prefabArr = new BsonArray();
            for (HytaleChunk.PrefabMarker pm : prefabs) {
                BsonDocument entry = new BsonDocument();
                entry.put("x", new BsonInt32(pm.x));
                entry.put("y", new BsonInt32(pm.y));
                entry.put("z", new BsonInt32(pm.z));
                entry.put("category", new BsonString(pm.category));
                entry.put("path", new BsonString(pm.prefabPath));
                prefabArr.add(entry);
            }
            doc.put("PrefabMarkers", prefabArr);
            hasData = true;
        }

        return hasData ? doc : null;
    }
    
    /**
     * Create BlockComponentChunk BSON (empty - no block components).
     * Format: { "BlockComponents": {} }
     */
    private static BsonDocument createBlockComponentChunkBson() {
        BsonDocument doc = new BsonDocument();
        doc.put("BlockComponents", new BsonDocument());
        return doc;
    }
    
    /**
     * Create BlockHealthChunk BSON.
     * Format (version 2):
     * - byte: version (2)
     * - int: healthMapSize
     * - For each health entry:
     *   - int x, int y, int z
     *   - float health (0.0-1.0)
     *   - long lastDamageTime
     * - int: fragilityMapSize (always 0 for terrain)
     * 
     * @param chunk The chunk containing block health data.
     */
    private static BsonDocument createBlockHealthChunkBson(HytaleChunk chunk) {
        BsonDocument doc = new BsonDocument();
        
        Map<Integer, HytaleChunk.BlockHealthData> healthMap = chunk.getBlockHealthMap();
        
        // Calculate required buffer size
        // version(1) + healthMapSize(4) + entries(healthMap.size() * 24) + fragilityMapSize(4)
        int bufferSize = 1 + 4 + (healthMap.size() * 24) + 4;
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(bufferSize);
        try {
            buf.writeByte(2);  // version
            buf.writeInt(healthMap.size());
            
            // Write health entries
            for (Map.Entry<Integer, HytaleChunk.BlockHealthData> entry : healthMap.entrySet()) {
                int key = entry.getKey();
                HytaleChunk.BlockHealthData data = entry.getValue();
                
                // Unpack coordinates from key
                int x = HytaleChunk.unpackX(key);
                int y = HytaleChunk.unpackY(key);
                int z = HytaleChunk.unpackZ(key);
                
                buf.writeInt(x);
                buf.writeInt(y);
                buf.writeInt(z);
                buf.writeFloat(data.health);
                buf.writeLong(data.lastDamageTime);
            }
            
            // Fragility map (empty - not used for terrain generation)
            buf.writeInt(0);
            
            byte[] dataBytes = new byte[buf.readableBytes()];
            buf.readBytes(dataBytes);
            doc.put("Data", new BsonBinary(dataBytes));
        } finally {
            buf.release();
        }
        
        return doc;
    }
    
    /**
     * Create EnvironmentChunk BSON with environments from chunk data.
     * Format:
     * - "Data": byte array with environment mappings and column data
     */
    private static BsonDocument createEnvironmentChunkBson(HytaleChunk chunk) {
        BsonDocument doc = new BsonDocument();
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            String[] environments = chunk.getEnvironments();
            
            // Build environment palette from unique environments in chunk
            List<String> palette = new ArrayList<>();
            Map<String, Integer> envToId = new HashMap<>();
            
            for (String env : environments) {
                if (!envToId.containsKey(env)) {
                    envToId.put(env, palette.size());
                    palette.add(env);
                }
            }
            
            // Write environment count
            buf.writeInt(palette.size());
            
            // Write mappings: id + name
            for (int i = 0; i < palette.size(); i++) {
                buf.writeInt(i);
                writeUtf(buf, palette.get(i));
            }
            
            // Write 1024 columns (32x32), each column uses EnvironmentColumn.serialize format:
            // int n (maxYs count), then n maxYs, then n+1 values.
            // For a single environment throughout the column: n=0, values[0]=envId
            for (int i = 0; i < 1024; i++) {
                int envId = envToId.get(environments[i]);
                buf.writeInt(0); // maxYs size (no Y-layer transitions)
                buf.writeInt(envId); // single value for entire column
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
     * Create ChunkColumn BSON with 10 section holders.
     * Format from ChunkColumn.CODEC:
     * - "Sections": array of 10 Holder documents
     */
    private static BsonDocument createChunkColumnBson(HytaleChunk chunk) {
        BsonDocument doc = new BsonDocument();
        BsonArray sections = new BsonArray();
        
        HytaleChunk.HytaleSection[] chunkSections = chunk.getSections();
        for (int i = 0; i < HytaleChunk.SECTION_COUNT; i++) {
            sections.add(createSectionHolderBson(chunkSections[i], i, chunk));
        }
        
        doc.put("Sections", sections);
        return doc;
    }
    
    /**
     * Create a section holder BSON document.
     * Each section holder has Components containing BlockSection, FluidSection, ChunkSection, BlockPhysics.
     * Order matches real Hytale: ChunkSection, BlockPhysics, Fluid, Block
     * 
     * @param section The section data.
     * @param sectionY The section index (0-9).
     * @param chunk The parent chunk, needed for heightmap-based lighting.
     */
    private static BsonDocument createSectionHolderBson(HytaleChunk.HytaleSection section, int sectionY, HytaleChunk chunk) {
        BsonDocument holder = new BsonDocument();
        BsonDocument components = new BsonDocument();
        
        // Add ChunkSection (empty marker component)
        components.put(COMP_CHUNK_SECTION, createChunkSectionBson());
        
        // Add BlockPhysics (empty)
        components.put(COMP_BLOCK_PHYSICS, createBlockPhysicsBson());
        
        // Add FluidSection
        components.put(COMP_FLUID_SECTION, createFluidSectionBson(section));
        
        // Add BlockSection
        components.put(COMP_BLOCK_SECTION, createBlockSectionBson(section, sectionY, chunk));
        
        holder.put("Components", components);
        return holder;
    }
    
    /**
     * Create ChunkSection BSON (empty marker component).
     */
    private static BsonDocument createChunkSectionBson() {
        // ChunkSection has no data fields in its codec
        return new BsonDocument();
    }
    
    /**
     * Create BlockPhysics BSON.
     * Format: { "Data": [false] } for empty (no support data)
     */
    private static BsonDocument createBlockPhysicsBson() {
        BsonDocument doc = new BsonDocument();
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(1);
        try {
            buf.writeBoolean(false); // no support data
            
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            doc.put("Data", new BsonBinary(data));
        } finally {
            buf.release();
        }
        
        return doc;
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
    private static BsonDocument createBlockSectionBson(HytaleChunk.HytaleSection section, int sectionY, HytaleChunk chunk) {
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
                    // Local light (calculated from heightmap)
                    writeCalculatedSkyLightData(buf, chunk, sectionY);
                    // Global light (same as local for now)
                    writeCalculatedSkyLightData(buf, chunk, sectionY);
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
                        writeUtf(buf, blockId);
                        buf.writeShort(counts.get(i));
                    }

                    switch (paletteType) {
                        case PALETTE_TYPE_HALF_BYTE:
                            writeHalfByteBlockData(buf, blockIndices);
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

                    writeCalculatedSkyLightData(buf, chunk, sectionY);
                    writeCalculatedSkyLightData(buf, chunk, sectionY);

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
                // Local light (calculated from heightmap)
                writeCalculatedSkyLightData(buf, chunk, sectionY);
                // Global light (same as local for now)
                writeCalculatedSkyLightData(buf, chunk, sectionY);
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
                    writeUtf(buf, hytaleId); // block type string
                    buf.writeShort(counts.get(i)); // count of this block type
                }

                // Write raw block data based on palette type
                switch (paletteType) {
                    case PALETTE_TYPE_HALF_BYTE:
                        // HalfByte: 4 bits per block, packed into 16384 bytes
                        writeHalfByteBlockData(buf, blockIndices);
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

                // Local light (calculated from heightmap)
                writeCalculatedSkyLightData(buf, chunk, sectionY);

                // Global light (same as local for now)
                writeCalculatedSkyLightData(buf, chunk, sectionY);

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
     * Write block indices as half-byte (nibble) packed data.
     * Each byte contains two 4-bit palette indices.
     * Total: 32768 blocks / 2 = 16384 bytes
     * 
     * IMPORTANT: Hytale's BitUtil uses the convention:
     * - Even indices → HIGH nibble (bits 4-7)
     * - Odd indices → LOW nibble (bits 0-3)
     */
    private static void writeHalfByteBlockData(ByteBuf buf, int[] blockIndices) {
        for (int i = 0; i < blockIndices.length; i += 2) {
            int idx1 = blockIndices[i] & 0x0F;      // Even index → HIGH nibble
            int idx2 = (i + 1 < blockIndices.length) ? (blockIndices[i + 1] & 0x0F) : 0;  // Odd index → LOW nibble
            // Pack: even index in high nibble, odd in low nibble (Hytale convention)
            buf.writeByte((idx1 << 4) | idx2);
        }
    }
    
    /**
     * Write the rotation section for a block section.
     * Rotation values are 0-63, representing rx*16 + ry*4 + rz where each axis is 0-3 (90° increments).
     */
    private static void writeRotationSection(ByteBuf buf, HytaleChunk.HytaleSection section) {
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
                writeHalfByteBlockData(buf, rotationIndices);
                break;
            case PALETTE_TYPE_BYTE:
                for (int idx : rotationIndices) {
                    buf.writeByte(idx);
                }
                break;
        }
    }
    
    /**
     * Write UTF-8 string in Hytale format.
     */
    private static void writeUtf(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
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
     * @param buf Output buffer.
     * @param chunk The chunk containing heightmap data.
     * @param sectionY The section index (0-9).
     */
    private static void writeCalculatedSkyLightData(ByteBuf buf, HytaleChunk chunk, int sectionY) {
        int sectionBaseY = sectionY * HytaleChunk.SECTION_HEIGHT;
        int sectionTopY = sectionBaseY + HytaleChunk.SECTION_HEIGHT - 1;
        
        // Check if entire section is above or below terrain for optimization
        // Heightmap = topmost solid block (e.g., Y=62)
        // Skylight applies to blocks AT and ABOVE heightmap (>= height)
        // So Y=62 (surface) and Y=63+ (air above) all get skylight
        boolean allAbove = true;
        boolean allBelow = true;
        
        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                int height = chunk.getHeight(x, z);
                // Section is all above/at if its base is at or above all terrain
                if (sectionBaseY < height) {
                    allAbove = false;
                }
                // Section is all below if its top ends below all terrain
                if (sectionTopY >= height) {
                    allBelow = false;
                }
            }
        }
        
        if (allAbove) {
            // Entire section is at or above terrain - full skylight
            writeFullSkyLightData(buf);
            return;
        }
        
        if (allBelow) {
            // Entire section is below terrain - no skylight
            short noLight = HytaleChunkLightDataBuilder.NO_LIGHT;
            buf.writeShort(0); // changeId
            buf.writeBoolean(true); // hasData
            buf.writeInt(17); // octree length
            buf.writeByte(0); // mask: no children
            for (int i = 0; i < 8; i++) {
                buf.writeShort(noLight);
            }
            return;
        }
        
        // Mixed section - write simplified lighting
        // To avoid octree pointer bugs, write full bright sky for now
        // TODO: Implement proper per-block lighting when octree builder is stable
        writeFullSkyLightData(buf);
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
    private static BsonDocument createFluidSectionBson(HytaleChunk.HytaleSection section) {
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
                    writeUtf(buf, palette.get(i));
                    buf.writeShort((short) counts[i]);
                }
                
                // Write raw block data (HalfByte)
                writeHalfByteBlockData(buf, indices);
                
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
    
    /**
     * Convert BSON document to bytes.
     */
    private static byte[] bsonToBytes(BsonDocument doc) {
        try (BasicOutputBuffer buffer = new BasicOutputBuffer()) {
            CODEC.encode(new BsonBinaryWriter(buffer), doc, ENCODER_CONTEXT);
            return buffer.toByteArray();
        }
    }
    
    /**
     * Deserialize BSON bytes to document (for debugging).
     */
    public static BsonDocument bytesToBson(byte[] bytes) {
        org.bson.BsonBinaryReader reader = new org.bson.BsonBinaryReader(ByteBuffer.wrap(bytes));
        return CODEC.decode(reader, org.bson.codecs.DecoderContext.builder().build());
    }
}
