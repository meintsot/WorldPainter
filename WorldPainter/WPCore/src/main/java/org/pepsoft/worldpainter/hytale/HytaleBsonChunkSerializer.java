package org.pepsoft.worldpainter.hytale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
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
        components.put(COMP_BLOCK_HEALTH_CHUNK, createBlockHealthChunkBson());
        
        // Add EnvironmentChunk component
        components.put(COMP_ENVIRONMENT_CHUNK, createEnvironmentChunkBson());
        
        // Add BlockChunk component (heightmap and tintmap)
        components.put(COMP_BLOCK_CHUNK, createBlockChunkBson(chunk));
        
        // Add EntityChunk component (empty for terrain-only export)
        components.put(COMP_ENTITY_CHUNK, createEntityChunkBson());
        
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
            
            // tint (IntBytePalette) - all zeros for now
            int[] tintmap = new int[32 * 32];
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
     * Create EntityChunk BSON (empty - no entities).
     * Format: { "Entities": [] }
     */
    private static BsonDocument createEntityChunkBson() {
        BsonDocument doc = new BsonDocument();
        doc.put("Entities", new BsonArray());
        return doc;
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
     * Format: 
     * - "Data": byte array [version(1), healthMapSize(4), fragilityMapSize(4)]
     */
    private static BsonDocument createBlockHealthChunkBson() {
        BsonDocument doc = new BsonDocument();
        
        // Serialize: version byte + empty health map + empty fragility map
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(9);
        try {
            buf.writeByte(2);  // version
            buf.writeInt(0);   // blockHealthMap.size()
            buf.writeInt(0);   // blockFragilityMap.size()
            
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            doc.put("Data", new BsonBinary(data));
        } finally {
            buf.release();
        }
        
        return doc;
    }
    
    /**
     * Create EnvironmentChunk BSON with default environment.
     * Format:
     * - "Data": byte array with environment mappings and column data
     */
    private static BsonDocument createEnvironmentChunkBson() {
        BsonDocument doc = new BsonDocument();
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            // Environment ID 0 = "Default"
            String defaultEnv = "Default";
            int defaultEnvId = 0;
            
            // Write environment count
            buf.writeInt(1);
            
            // Write mapping: id + name
            buf.writeInt(defaultEnvId);
            writeUtf(buf, defaultEnv);
            
            // Write 1024 columns (32x32), each column uses EnvironmentColumn.serialize format:
            // int n (maxYs count), then n maxYs, then n+1 values.
            // For a single environment throughout the column: n=0, values[0]=defaultEnvId
            for (int i = 0; i < 1024; i++) {
                buf.writeInt(0); // maxYs size
                buf.writeInt(defaultEnvId); // single value
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
            sections.add(createSectionHolderBson(chunkSections[i], i));
        }
        
        doc.put("Sections", sections);
        return doc;
    }
    
    /**
     * Create a section holder BSON document.
     * Each section holder has Components containing BlockSection, FluidSection, ChunkSection, BlockPhysics.
     * Order matches real Hytale: ChunkSection, BlockPhysics, Fluid, Block
     */
    private static BsonDocument createSectionHolderBson(HytaleChunk.HytaleSection section, int sectionY) {
        BsonDocument holder = new BsonDocument();
        BsonDocument components = new BsonDocument();
        
        // Add ChunkSection (empty marker component)
        components.put(COMP_CHUNK_SECTION, createChunkSectionBson());
        
        // Add BlockPhysics (empty)
        components.put(COMP_BLOCK_PHYSICS, createBlockPhysicsBson());
        
        // Add FluidSection
        components.put(COMP_FLUID_SECTION, createFluidSectionBson(section));
        
        // Add BlockSection
        components.put(COMP_BLOCK_SECTION, createBlockSectionBson(section));
        
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
     */
    private static BsonDocument createBlockSectionBson(HytaleChunk.HytaleSection section) {
        BsonDocument doc = new BsonDocument();
        doc.put("Version", new BsonInt32(BLOCK_SECTION_VERSION));
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            // Block migration version
            buf.writeInt(0);
            
            Material[] blocks = section.getBlocks();
            Map<Material, Integer> paletteIndex = new HashMap<>();
            List<Material> palette = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            int[] blockIndices = new int[blocks.length];
            boolean allAir = true;

            for (int i = 0; i < blocks.length; i++) {
                Material effective = toBlockMaterial(blocks[i]);
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
                // Empty rotation section
                buf.writeByte(PALETTE_TYPE_EMPTY);
                // Local light (full skylight)
                writeFullSkyLightData(buf);
                // Global light (full skylight)
                writeFullSkyLightData(buf);
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

                // Rotation section (empty)
                buf.writeByte(PALETTE_TYPE_EMPTY);

                // Local light (full skylight)
                writeFullSkyLightData(buf);

                // Global light (full skylight)
                writeFullSkyLightData(buf);

                // Change counters
                buf.writeShort(0);
                buf.writeShort(0);
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
     */
    private static void writeHalfByteBlockData(ByteBuf buf, int[] blockIndices) {
        for (int i = 0; i < blockIndices.length; i += 2) {
            int idx1 = blockIndices[i] & 0x0F;
            int idx2 = (i + 1 < blockIndices.length) ? (blockIndices[i + 1] & 0x0F) : 0;
            // Pack two nibbles into one byte (low nibble first)
            buf.writeByte(idx1 | (idx2 << 4));
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
     * Create FluidSection BSON.
     * Format: { "Data": [paletteType, paletteData..., hasLevelData, levelData?] }
     */
    private static BsonDocument createFluidSectionBson(HytaleChunk.HytaleSection section) {
        BsonDocument doc = new BsonDocument();
        
        Material[] blocks = section.getBlocks();
        boolean hasWater = false;
        boolean hasLava = false;
        for (Material block : blocks) {
            if (block == Material.WATER) {
                hasWater = true;
            } else if (block == Material.LAVA) {
                hasLava = true;
            }
            if (hasWater && hasLava) {
                break;
            }
        }
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        try {
            if (!hasWater && !hasLava) {
                // Empty palette type
                buf.writeByte(PALETTE_TYPE_EMPTY);
                // No level data
                buf.writeBoolean(false);
            } else {
                // Build fluid palette: Empty + Water + Lava (if present)
                List<String> palette = new ArrayList<>();
                palette.add("Empty");
                if (hasWater) {
                    palette.add(HytaleBlockMapping.HY_WATER);
                }
                if (hasLava) {
                    palette.add(HytaleBlockMapping.HY_LAVA);
                }
                
                int paletteType = PALETTE_TYPE_HALF_BYTE;
                buf.writeByte(paletteType);
                
                // Build indices and counts
                int[] indices = new int[blocks.length];
                int[] counts = new int[palette.size()];
                for (int i = 0; i < blocks.length; i++) {
                    Material block = blocks[i];
                    int idx = 0;
                    if (block == Material.WATER) {
                        idx = hasWater ? 1 : 0;
                    } else if (block == Material.LAVA) {
                        idx = hasWater ? 2 : 1;
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
                        setLevelNibble(levelData, i, 8);
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
