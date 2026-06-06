package org.pepsoft.worldpainter.hytale.chunk;

import org.pepsoft.worldpainter.hytale.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
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

    // Codec versions from decompiled sources
    private static final int BLOCK_CHUNK_VERSION = 3;

    // Component IDs from LegacyModule registrations
    private static final String COMP_WORLD_CHUNK = "WorldChunk";
    private static final String COMP_BLOCK_CHUNK = "BlockChunk";
    private static final String COMP_ENTITY_CHUNK = "EntityChunk";
    private static final String COMP_BLOCK_COMPONENT_CHUNK = "BlockComponentChunk";
    private static final String COMP_ENVIRONMENT_CHUNK = "EnvironmentChunk";
    private static final String COMP_CHUNK_COLUMN = "ChunkColumn";
    private static final String COMP_BLOCK_HEALTH_CHUNK = "BlockHealthChunk";
    private static final String COMP_WP_METADATA = "TalePainterMetadata";
    private static final String COMP_BLOCK_SECTION = "Block";
    private static final String COMP_FLUID_SECTION = "Fluid";
    private static final String COMP_CHUNK_SECTION = "ChunkSection";
    private static final String COMP_BLOCK_PHYSICS = "BlockPhysics";

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
            // needsPhysics (boolean) — false; the game computes support
            // values on-demand when blocks are disturbed
            buf.writeBoolean(false);

            // height (ShortBytePalette) - 32x32 heightmap
            // ShortBytePalette format: palette size + palette entries + indices
            short[] heightmap = chunk.getHeightmap();
            HytaleBsonPaletteCodec.writeShortBytePalette(buf, heightmap);

            // tint (IntBytePalette) - tint colors from chunk data
            int[] tintmap = chunk.getTints();
            HytaleBsonPaletteCodec.writeIntBytePalette(buf, tintmap);

            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            doc.put("Data", new BsonBinary(data));
        } finally {
            buf.release();
        }

        return doc;
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
                entry.put("rotation", new BsonDouble(pm.rotation));
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
                HytaleBsonPaletteCodec.writeUtf(buf, palette.get(i));
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
     *
     * <p>Most sections leave block physics support values unset so Hytale can
     * compute them on-demand when blocks are disturbed. Sections containing
     * explicitly marked decorative blocks serialize only those support
     * overrides.
     *
     * Format from ChunkColumn.CODEC:
     * - "Sections": array of 10 Holder documents
     */
    private static BsonDocument createChunkColumnBson(HytaleChunk chunk) {
        BsonDocument doc = new BsonDocument();
        BsonArray sections = new BsonArray();

        HytaleSection[] chunkSections = chunk.getSections();
        for (int i = 0; i < chunk.getSectionCount(); i++) {
            sections.add(createSectionHolderBson(chunkSections[i], i, chunk, chunkSections[i].getSupportData()));
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
     * @param supportData Precomputed nibble-packed support values for this section (16384 bytes), or null if empty.
     */
    private static BsonDocument createSectionHolderBson(HytaleSection section, int sectionY, HytaleChunk chunk, byte[] supportData) {
        BsonDocument holder = new BsonDocument();
        BsonDocument components = new BsonDocument();

        // Add ChunkSection (empty marker component)
        components.put(COMP_CHUNK_SECTION, createChunkSectionBson());

        // Add BlockPhysics with precomputed support values
        components.put(COMP_BLOCK_PHYSICS, createBlockPhysicsBson(supportData));

        // Add FluidSection
        components.put(COMP_FLUID_SECTION, HytaleSectionBodyEncoder.createFluidSectionBson(section));

        // Add BlockSection
        components.put(COMP_BLOCK_SECTION, HytaleSectionBodyEncoder.createBlockSectionBson(section, sectionY, chunk));

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
     * Create BlockPhysics BSON with precomputed support data.
     *
     * <p>Support values control Hytale's block physics cascade system:
     * <ul>
     *   <li>0 = no support / air — block breaks when disturbed if it has support requirements</li>
     *   <li>1-14 = propagated support distance from a support provider</li>
     *   <li>15 (IS_DECO) = decorative — exempt from physics checks</li>
     * </ul>
     *
     * @param supportData Nibble-packed support values (16384 bytes), or null for empty section.
     */
    private static BsonDocument createBlockPhysicsBson(byte[] supportData) {
        BsonDocument doc = new BsonDocument();
        doc.put("Version", new BsonInt32(0));

        if (supportData == null) {
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(1);
            try {
                buf.writeBoolean(false);
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                doc.put("Data", new BsonBinary(data));
            } finally {
                buf.release();
            }
        } else {
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(1 + supportData.length);
            try {
                buf.writeBoolean(true);
                buf.writeBytes(supportData);
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                doc.put("Data", new BsonBinary(data));
            } finally {
                buf.release();
            }
        }

        return doc;
    }

    /**
     * Convert BSON document to bytes. Public for use by HytaleEntity for
     * round-trip entity BSON preservation during import/export.
     */
    public static byte[] bsonToBytes(BsonDocument doc) {
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
