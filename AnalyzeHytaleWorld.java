import com.github.luben.zstd.Zstd;
import org.bson.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class AnalyzeHytaleWorld {
    private static final byte[] MAGIC = "HytaleIndexedStorage".getBytes(StandardCharsets.UTF_8);
    private static final int HEADER_LEN = 32;
    private static final int BLOB_COUNT = 1024;

    private record RegionHeader(int version, int blobCount, int segmentSize) {}

    private static class ChunkStats {
        int worldChunkX;
        int worldChunkZ;
        int columnsWithFluid;
        int fluidBlocks;
        int waterBlocks;
        int nonWaterFluidBlocks;
        int minFluidY = Integer.MAX_VALUE;
        int maxFluidY = Integer.MIN_VALUE;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int minDelta = Integer.MAX_VALUE;
        int maxDelta = Integer.MIN_VALUE;
        int waterSections;
        final Map<String, Integer> environmentCounts = new LinkedHashMap<>();

        final int[] topFluidY = new int[1024];
        final int[] topBlockY = new int[1024];
        final int[] heights = new int[1024];

        ChunkStats() {
            Arrays.fill(topFluidY, Integer.MIN_VALUE);
            Arrays.fill(topBlockY, Integer.MIN_VALUE);
            Arrays.fill(heights, Integer.MIN_VALUE);
        }
    }

    public static void main(String[] args) throws Exception {
        Path broken = Paths.get("C:/Users/Sotirios/Desktop/KOC/run/universe/worlds/default/chunks");
        Path working = Paths.get("C:/Users/Sotirios/Desktop/WorldPainter/universe/worlds/default/chunks");

        System.out.println("=== BROKEN WORLD ===");
        analyzeWorld(broken, 12);

        System.out.println("\n=== WORKING WORLD ===");
        analyzeWorld(working, 12);
    }

    private static void analyzeWorld(Path chunksDir, int sampleLimit) throws Exception {
        if (!Files.isDirectory(chunksDir)) {
            System.out.println("Missing dir: " + chunksDir);
            return;
        }

        List<Path> regions = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(chunksDir, "*.region.bin")) {
            for (Path p : ds) {
                regions.add(p);
            }
        }
        regions.sort(Comparator.comparing(Path::getFileName));

        int chunkCount = 0;
        int chunkWithFluid = 0;
        int sampled = 0;

        for (Path region : regions) {
            String name = region.getFileName().toString();
            String[] parts = name.split("\\.");
            if (parts.length < 4) continue;
            int regionX = Integer.parseInt(parts[0]);
            int regionZ = Integer.parseInt(parts[1]);

            byte[] fileBytes = Files.readAllBytes(region);
            RegionHeader header = readHeader(fileBytes);
            int[] blobIdx = readBlobIndex(fileBytes, header.blobCount());

            for (int blob = 0; blob < blobIdx.length; blob++) {
                int firstSeg = blobIdx[blob];
                if (firstSeg == 0) continue;
                chunkCount++;

                byte[] raw = readAndDecompressChunk(fileBytes, header, firstSeg);
                BsonDocument doc = new RawBsonDocument(raw);
                ChunkStats stats = analyzeChunk(doc);

                int localX = blob & 31;
                int localZ = blob >> 5;
                stats.worldChunkX = (regionX << 5) + localX;
                stats.worldChunkZ = (regionZ << 5) + localZ;

                if (stats.fluidBlocks > 0) {
                    chunkWithFluid++;
                    if (sampled < sampleLimit) {
                        sampled++;
                        printChunk(stats);
                    }
                }
            }
        }

        System.out.println("Total chunks: " + chunkCount);
        System.out.println("Chunks with fluid: " + chunkWithFluid);
        System.out.println("Regions scanned: " + regions.size());
    }

    private static void printChunk(ChunkStats s) {
        String envSummary = s.environmentCounts.isEmpty() ? "{}" : s.environmentCounts.toString();
        System.out.println("Chunk (" + s.worldChunkX + "," + s.worldChunkZ + ")"
                + " fluidBlocks=" + s.fluidBlocks
                + " waterBlocks=" + s.waterBlocks
                + " nonWater=" + s.nonWaterFluidBlocks
                + " columnsWithFluid=" + s.columnsWithFluid
                + " fluidY=" + s.minFluidY + ".." + s.maxFluidY
                + " height=" + s.minHeight + ".." + s.maxHeight
                + " topFluid-height delta=" + s.minDelta + ".." + s.maxDelta
                + " waterSections=" + s.waterSections
                + " envs=" + envSummary);
    }

    private static ChunkStats analyzeChunk(BsonDocument doc) {
        ChunkStats stats = new ChunkStats();

        BsonDocument components = doc.getDocument("Components", new BsonDocument());

        BsonDocument blockChunk = components.getDocument("BlockChunk", new BsonDocument());
        byte[] blockChunkData = bsonBinary(blockChunk, "Data");
        if (blockChunkData != null) {
            decodeHeights(blockChunkData, stats.heights);
            for (int h : stats.heights) {
                if (h == Integer.MIN_VALUE) continue;
                stats.minHeight = Math.min(stats.minHeight, h);
                stats.maxHeight = Math.max(stats.maxHeight, h);
            }
        }

        BsonDocument envChunk = components.getDocument("EnvironmentChunk", new BsonDocument());
        byte[] envData = bsonBinary(envChunk, "Data");
        if (envData != null) {
            decodeEnvironmentCounts(envData, stats.environmentCounts);
        }

        BsonDocument chunkColumn = components.getDocument("ChunkColumn", new BsonDocument());
        BsonArray sections = chunkColumn.getArray("Sections", new BsonArray());

        for (int sectionY = 0; sectionY < sections.size(); sectionY++) {
            BsonDocument sectionHolder = sections.get(sectionY).asDocument();
            BsonDocument sectionComps = sectionHolder.getDocument("Components", new BsonDocument());

            byte[] blockData = null;
            if (sectionComps.containsKey("Block")) {
                BsonDocument blockDoc = sectionComps.getDocument("Block");
                blockData = bsonBinary(blockDoc, "Data");
            }
            if (blockData != null) {
                decodeTopBlocks(blockData, sectionY, stats.topBlockY);
            }

            byte[] fluidData = null;
            if (sectionComps.containsKey("Fluid")) {
                BsonDocument fluidDoc = sectionComps.getDocument("Fluid");
                fluidData = bsonBinary(fluidDoc, "Data");
            }
            if (fluidData != null) {
                int added = decodeFluids(fluidData, sectionY, stats);
                if (added > 0) stats.waterSections++;
            }
        }

        for (int i = 0; i < 1024; i++) {
            int tf = stats.topFluidY[i];
            if (tf != Integer.MIN_VALUE) {
                stats.columnsWithFluid++;
                int h = stats.heights[i];
                if (h != Integer.MIN_VALUE) {
                    int d = tf - h;
                    stats.minDelta = Math.min(stats.minDelta, d);
                    stats.maxDelta = Math.max(stats.maxDelta, d);
                }
            }
        }

        if (stats.minFluidY == Integer.MAX_VALUE) stats.minFluidY = Integer.MIN_VALUE;
        if (stats.maxFluidY == Integer.MIN_VALUE) stats.maxFluidY = Integer.MIN_VALUE;
        if (stats.minHeight == Integer.MAX_VALUE) stats.minHeight = Integer.MIN_VALUE;
        if (stats.maxHeight == Integer.MIN_VALUE) stats.maxHeight = Integer.MIN_VALUE;
        if (stats.minDelta == Integer.MAX_VALUE) stats.minDelta = Integer.MIN_VALUE;
        if (stats.maxDelta == Integer.MIN_VALUE) stats.maxDelta = Integer.MIN_VALUE;

        return stats;
    }

    private static void decodeHeights(byte[] data, int[] outHeights) {
        int p = 0;
        if (data.length < 3) return;
        p += 1; // needsPhysics

        int paletteCount = u16le(data, p); p += 2;
        short[] palette = new short[paletteCount];
        for (int i = 0; i < paletteCount && p + 1 < data.length; i++) {
            palette[i] = (short) u16le(data, p);
            p += 2;
        }
        if (p + 3 >= data.length) return;
        int bitfieldLen = u32le(data, p); p += 4;
        if (p + bitfieldLen > data.length) return;

        for (int i = 0; i < 1024; i++) {
            int idx = readBits(data, p, i * 10, 10);
            if (idx >= 0 && idx < palette.length) {
                outHeights[i] = palette[idx];
            }
        }
    }

    private static void decodeTopBlocks(byte[] data, int sectionY, int[] topBlockY) {
        int p = 0;
        if (data.length < 5) return;
        p += 4; // migration version
        int paletteType = u8(data, p++);
        if (paletteType == 0) return;

        int entryCount = u16be(data, p); p += 2;
        String[] names = new String[Math.max(256, entryCount + 1)];
        for (int i = 0; i < entryCount; i++) {
            int internalId = u8(data, p++);
            int sl = u16be(data, p); p += 2;
            String name = new String(data, p, sl, StandardCharsets.UTF_8);
            p += sl;
            p += 2; // count
            if (internalId < names.length) names[internalId] = name;
        }

        int blockDataLen = switch (paletteType) {
            case 1 -> 16384;
            case 2 -> 32768;
            case 3 -> 65536;
            default -> 0;
        };
        if (blockDataLen == 0 || p + blockDataLen > data.length) return;

        for (int i = 0; i < 32768; i++) {
            int idx = switch (paletteType) {
                case 1 -> ((i & 1) == 0)
                        ? ((data[p + (i >> 1)] >> 4) & 0xF)
                        : (data[p + (i >> 1)] & 0xF);
                case 2 -> u8(data, p + i);
                case 3 -> u16be(data, p + i * 2);
                default -> 0;
            };
            if (idx == 0) continue;
            String name = (idx < names.length) ? names[idx] : null;
            if (name == null || name.equals("Empty")) continue;

            int x = i & 31;
            int z = (i >> 5) & 31;
            int y = (i >> 10) & 31;
            int col = (z << 5) | x;
            int gy = sectionY * 32 + y;
            if (gy > topBlockY[col]) topBlockY[col] = gy;
        }
    }

    private static int decodeFluids(byte[] data, int sectionY, ChunkStats stats) {
        int p = 0;
        if (data.length < 1) return 0;

        int paletteType = u8(data, p++);
        if (paletteType == 0) {
            return 0;
        }

        int entryCount = u16be(data, p); p += 2;
        String[] names = new String[Math.max(256, entryCount + 1)];
        for (int i = 0; i < entryCount; i++) {
            int internalId = u8(data, p++);
            int sl = u16be(data, p); p += 2;
            String name = new String(data, p, sl, StandardCharsets.UTF_8);
            p += sl;
            p += 2; // count
            if (internalId < names.length) names[internalId] = name;
        }

        int indexDataLen = switch (paletteType) {
            case 1 -> 16384;
            case 2 -> 32768;
            case 3 -> 65536;
            default -> 0;
        };
        if (indexDataLen == 0 || p + indexDataLen > data.length) return 0;
        int indexStart = p;
        p += indexDataLen;

        boolean hasLevels = p < data.length && data[p++] != 0;
        int levelStart = p;

        int found = 0;
        for (int i = 0; i < 32768; i++) {
            int fluidIdx = switch (paletteType) {
                case 1 -> ((i & 1) == 0)
                        ? ((data[indexStart + (i >> 1)] >> 4) & 0xF)
                        : (data[indexStart + (i >> 1)] & 0xF);
                case 2 -> u8(data, indexStart + i);
                case 3 -> u16be(data, indexStart + i * 2);
                default -> 0;
            };
            if (fluidIdx == 0) continue;

            String fluidName = (fluidIdx < names.length) ? names[fluidIdx] : null;
            int level = 0;
            if (hasLevels && levelStart + (i >> 1) < data.length) {
                int lvByte = u8(data, levelStart + (i >> 1));
                level = ((i & 1) == 0) ? (lvByte & 0xF) : ((lvByte >> 4) & 0xF);
            }

            found++;
            stats.fluidBlocks++;
            if (fluidName != null && fluidName.toLowerCase(Locale.ROOT).contains("water")) {
                stats.waterBlocks++;
            } else {
                stats.nonWaterFluidBlocks++;
            }

            int x = i & 31;
            int z = (i >> 5) & 31;
            int y = (i >> 10) & 31;
            int gy = sectionY * 32 + y;
            int col = (z << 5) | x;
            if (gy > stats.topFluidY[col]) stats.topFluidY[col] = gy;

            stats.minFluidY = Math.min(stats.minFluidY, gy);
            stats.maxFluidY = Math.max(stats.maxFluidY, gy);

            // treat invalid level as still fluid, but keep a sanity counter if needed
            if (level == 0) {
                // no-op: just indicates odd encoding but still present for our diagnostics
            }
        }

        return found;
    }

    private static void decodeEnvironmentCounts(byte[] data, Map<String, Integer> out) {
        int p = 0;
        if (data.length < 4) return;

        int mappingCount = u32be(data, p); p += 4;
        Map<Integer, String> idToName = new HashMap<>();
        for (int i = 0; i < mappingCount && p + 5 < data.length; i++) {
            int serialId = u32be(data, p); p += 4;
            int sl = u16be(data, p); p += 2;
            if (p + sl > data.length) return;
            String key = new String(data, p, sl, StandardCharsets.UTF_8);
            p += sl;
            idToName.put(serialId, key);
        }

        for (int col = 0; col < 1024 && p + 3 < data.length; col++) {
            int n = u32be(data, p); p += 4;
            p += n * 4; // maxY transitions
            int valueCount = n + 1;
            for (int i = 0; i < valueCount && p + 3 < data.length; i++) {
                int envId = u32be(data, p); p += 4;
                String name = idToName.getOrDefault(envId, "#" + envId);
                out.merge(name, 1, Integer::sum);
            }
        }
    }

    private static RegionHeader readHeader(byte[] file) {
        if (file.length < HEADER_LEN) throw new IllegalArgumentException("file too small");
        for (int i = 0; i < MAGIC.length; i++) {
            if (file[i] != MAGIC[i]) {
                throw new IllegalArgumentException("invalid magic");
            }
        }
        int version = u32be(file, 20);
        int blobCount = u32be(file, 24);
        int segmentSize = u32be(file, 28);
        return new RegionHeader(version, blobCount, segmentSize);
    }

    private static int[] readBlobIndex(byte[] file, int blobCount) {
        int[] idx = new int[blobCount];
        int p = HEADER_LEN;
        for (int i = 0; i < blobCount; i++) {
            idx[i] = u32be(file, p);
            p += 4;
        }
        return idx;
    }

    private static byte[] readAndDecompressChunk(byte[] file, RegionHeader h, int firstSeg) throws IOException {
        long segmentsBase = HEADER_LEN + (long) h.blobCount * 4L;
        long pos = segmentsBase + (long) (firstSeg - 1) * h.segmentSize;
        if (pos < 0 || pos + 8 > file.length) throw new IOException("invalid segment position");

        int p = (int) pos;
        int srcLen = u32be(file, p);
        int compLen = u32be(file, p + 4);
        int dataStart = p + 8;
        if (dataStart + compLen > file.length) throw new IOException("invalid compressed length");

        byte[] compressed = Arrays.copyOfRange(file, dataStart, dataStart + compLen);
        byte[] out = new byte[srcLen];
        long r = Zstd.decompress(out, compressed);
        if (r != srcLen) {
            throw new IOException("decompress size mismatch: expected " + srcLen + " got " + r);
        }
        return out;
    }

    private static byte[] bsonBinary(BsonDocument doc, String key) {
        if (doc == null || !doc.containsKey(key)) return null;
        BsonValue v = doc.get(key);
        if (!v.isBinary()) return null;
        return v.asBinary().getData();
    }

    private static int readBits(byte[] data, int base, int bitIndex, int bitCount) {
        int value = 0;
        for (int i = 0; i < bitCount; i++) {
            int absolute = bitIndex + i;
            int byteIndex = base + (absolute >> 3);
            if (byteIndex >= data.length) return -1;
            int bit = (u8(data, byteIndex) >> (absolute & 7)) & 1;
            value |= (bit << i);
        }
        return value;
    }

    private static int u8(byte[] a, int p) { return a[p] & 0xFF; }
    private static int u16be(byte[] a, int p) { return ((a[p] & 0xFF) << 8) | (a[p + 1] & 0xFF); }
    private static int u16le(byte[] a, int p) { return (a[p] & 0xFF) | ((a[p + 1] & 0xFF) << 8); }
    private static int u32be(byte[] a, int p) { return ((a[p] & 0xFF) << 24) | ((a[p + 1] & 0xFF) << 16) | ((a[p + 2] & 0xFF) << 8) | (a[p + 3] & 0xFF); }
    private static int u32le(byte[] a, int p) { return (a[p] & 0xFF) | ((a[p + 1] & 0xFF) << 8) | ((a[p + 2] & 0xFF) << 16) | ((a[p + 3] & 0xFF) << 24); }
}
