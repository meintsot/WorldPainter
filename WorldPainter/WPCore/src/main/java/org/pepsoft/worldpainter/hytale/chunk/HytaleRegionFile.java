package org.pepsoft.worldpainter.hytale.chunk;

import org.pepsoft.worldpainter.hytale.*;

import com.github.luben.zstd.Zstd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;

/**
 * Implements Hytale's IndexedStorageFile format for storing chunks in region files.
 *
 * File format:
 * - Header: "HytaleIndexedStorage" (20 bytes) + version (4) + blobCount (4) + segmentSize (4) = 32 bytes
 * - Blob index: blobCount * 4 bytes (index pointing to first segment for each blob)
 * - Segments: variable length data segments containing compressed chunk data
 *
 * Each blob (chunk) has a header:
 * - srcLength (4 bytes): uncompressed size
 * - compressedLength (4 bytes): compressed size
 * - data: Zstd compressed chunk data
 *
 * <p>Chunk BSON encoding and decoding are delegated to {@link HytaleBsonChunkSerializer}
 * and {@link HytaleBsonChunkDeserializer} respectively.</p>
 */
public class HytaleRegionFile implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(HytaleRegionFile.class);

    public static final String MAGIC_STRING = "HytaleIndexedStorage";
    public static final byte[] MAGIC_BYTES = MAGIC_STRING.getBytes(StandardCharsets.UTF_8);
    public static final int VERSION = 1;
    public static final int DEFAULT_BLOB_COUNT = 1024; // 32*32 chunks per region
    public static final int DEFAULT_SEGMENT_SIZE = 4096;
    public static final int DEFAULT_COMPRESSION_LEVEL = 3;

    // Header offsets
    public static final int MAGIC_OFFSET = 0;
    public static final int MAGIC_LENGTH = 20;
    public static final int VERSION_OFFSET = 20;
    public static final int BLOB_COUNT_OFFSET = 24;
    public static final int SEGMENT_SIZE_OFFSET = 28;
    public static final int HEADER_LENGTH = 32;

    // Blob header
    public static final int SRC_LENGTH_OFFSET = 0;
    public static final int COMPRESSED_LENGTH_OFFSET = 4;
    public static final int BLOB_HEADER_LENGTH = 8;

    private final Path path;
    private FileChannel fileChannel;
    private int blobCount;
    private int segmentSize;
    private int compressionLevel = DEFAULT_COMPRESSION_LEVEL;
    private ByteBuffer blobIndexBuffer;
    private final BitSet usedSegments = new BitSet();

    public HytaleRegionFile(Path path) {
        this.path = path;
    }

    /**
     * Create a new region file.
     */
    public void create() throws IOException {
        create(DEFAULT_BLOB_COUNT, DEFAULT_SEGMENT_SIZE);
    }

    /**
     * Create a new region file with specified parameters.
     */
    public void create(int blobCount, int segmentSize) throws IOException {
        this.blobCount = blobCount;
        this.segmentSize = segmentSize;

        Files.createDirectories(path.getParent());
        fileChannel = FileChannel.open(path,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);

        writeHeader();
        initializeBlobIndex();

        logger.debug("Created region file: {} with {} blobs, {} segment size", path, blobCount, segmentSize);
    }

    /**
     * Open an existing region file.
     */
    public void open() throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Region file does not exist: " + path);
        }

        fileChannel = FileChannel.open(path,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE);

        readHeader();
        loadBlobIndex();
        readUsedSegments();

        logger.debug("Opened region file: {} with {} blobs", path, blobCount);
    }

    /**
     * Open or create a region file.
     */
    public void openOrCreate() throws IOException {
        if (Files.exists(path) && Files.size(path) > 0) {
            open();
        } else {
            create();
        }
    }

    private void writeHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH);
        header.order(ByteOrder.BIG_ENDIAN);
        header.put(MAGIC_BYTES);
        header.putInt(VERSION);
        header.putInt(blobCount);
        header.putInt(segmentSize);
        header.flip();
        fileChannel.write(header, 0);
    }

    private void readHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH);
        header.order(ByteOrder.BIG_ENDIAN);
        fileChannel.read(header, 0);
        header.flip();

        byte[] magic = new byte[MAGIC_LENGTH];
        header.get(magic);
        String magicStr = new String(magic, StandardCharsets.UTF_8);
        if (!MAGIC_STRING.equals(magicStr)) {
            throw new IOException("Invalid magic string: " + magicStr);
        }

        int version = header.getInt();
        if (version != VERSION) {
            throw new IOException("Unsupported version: " + version);
        }

        blobCount = header.getInt();
        segmentSize = header.getInt();
    }

    private void initializeBlobIndex() throws IOException {
        blobIndexBuffer = ByteBuffer.allocate(blobCount * 4);
        blobIndexBuffer.order(ByteOrder.BIG_ENDIAN);
        // Initialize all blob indices to 0 (unassigned)
        for (int i = 0; i < blobCount; i++) {
            blobIndexBuffer.putInt(0);
        }
        blobIndexBuffer.flip();
        fileChannel.write(blobIndexBuffer, HEADER_LENGTH);
        blobIndexBuffer.clear();
    }

    private void loadBlobIndex() throws IOException {
        blobIndexBuffer = ByteBuffer.allocate(blobCount * 4);
        blobIndexBuffer.order(ByteOrder.BIG_ENDIAN);
        fileChannel.read(blobIndexBuffer, HEADER_LENGTH);
        blobIndexBuffer.flip();
    }

    private void readUsedSegments() throws IOException {
        usedSegments.clear();
        for (int blobIndex = 0; blobIndex < blobCount; blobIndex++) {
            int firstSegmentIndex = blobIndexBuffer.getInt(blobIndex * 4);
            if (firstSegmentIndex != 0) {
                ByteBuffer blobHeader = readBlobHeader(firstSegmentIndex);
                int compressedLength = blobHeader.getInt(COMPRESSED_LENGTH_OFFSET);
                int segmentsNeeded = requiredSegments(BLOB_HEADER_LENGTH + compressedLength);
                for (int i = 0; i < segmentsNeeded; i++) {
                    usedSegments.set(firstSegmentIndex + i);
                }
            }
        }
    }

    /**
     * Write a chunk to the region file.
     *
     * @param localX Local X coordinate within the region (0-31)
     * @param localZ Local Z coordinate within the region (0-31)
     * @param chunk The chunk to write
     */
    public void writeChunk(int localX, int localZ, HytaleChunk chunk) throws IOException {
        int blobIndex = getBlobIndex(localX, localZ);

        // Serialize the chunk
        byte[] serialized = serializeChunk(chunk);

        // Compress with Zstd
        int maxCompressedLen = (int) Zstd.compressBound(serialized.length);
        byte[] compressed = new byte[maxCompressedLen];
        long compressedLen = Zstd.compress(compressed, serialized, compressionLevel);

        // Prepare blob data with header
        int totalSize = BLOB_HEADER_LENGTH + (int) compressedLen;
        ByteBuffer blobData = ByteBuffer.allocate(totalSize);
        blobData.order(ByteOrder.BIG_ENDIAN);
        blobData.putInt(serialized.length); // src length
        blobData.putInt((int) compressedLen); // compressed length
        blobData.put(compressed, 0, (int) compressedLen);
        blobData.flip();

        // Free old segments if chunk exists
        int oldFirstSegment = blobIndexBuffer.getInt(blobIndex * 4);
        if (oldFirstSegment != 0) {
            ByteBuffer oldHeader = readBlobHeader(oldFirstSegment);
            int oldCompressedLen = oldHeader.getInt(COMPRESSED_LENGTH_OFFSET);
            int oldSegments = requiredSegments(BLOB_HEADER_LENGTH + oldCompressedLen);
            for (int i = 0; i < oldSegments; i++) {
                usedSegments.clear(oldFirstSegment + i);
            }
        }

        // Find free segments
        int segmentsNeeded = requiredSegments(totalSize);
        int firstSegment = findFreeSegments(segmentsNeeded);

        // Write blob data
        long position = segmentPosition(firstSegment);
        fileChannel.write(blobData, position);

        // Update blob index
        blobIndexBuffer.putInt(blobIndex * 4, firstSegment);
        ByteBuffer indexUpdate = ByteBuffer.allocate(4);
        indexUpdate.order(ByteOrder.BIG_ENDIAN);
        indexUpdate.putInt(firstSegment);
        indexUpdate.flip();
        fileChannel.write(indexUpdate, HEADER_LENGTH + blobIndex * 4);

        // Mark segments as used
        for (int i = 0; i < segmentsNeeded; i++) {
            usedSegments.set(firstSegment + i);
        }

        logger.trace("Wrote chunk at {},{} to blob {} (segment {})", localX, localZ, blobIndex, firstSegment);
    }

    /**
     * Read a chunk from the region file.
     *
     * @param localX Local X coordinate within the region (0-31)
     * @param localZ Local Z coordinate within the region (0-31)
     * @return The chunk, or null if not present
     */
    public HytaleChunk readChunk(int localX, int localZ, int minHeight, int maxHeight) throws IOException {
        int blobIndex = getBlobIndex(localX, localZ);
        int firstSegment = blobIndexBuffer.getInt(blobIndex * 4);

        if (firstSegment == 0) {
            return null; // Chunk not present
        }

        // Read blob header
        ByteBuffer blobHeader = readBlobHeader(firstSegment);
        int srcLength = blobHeader.getInt(SRC_LENGTH_OFFSET);
        int compressedLength = blobHeader.getInt(COMPRESSED_LENGTH_OFFSET);

        // Read compressed data
        ByteBuffer compressedData = ByteBuffer.allocate(compressedLength);
        fileChannel.read(compressedData, segmentPosition(firstSegment) + BLOB_HEADER_LENGTH);
        compressedData.flip();

        // Decompress
        byte[] decompressed = new byte[srcLength];
        long decompressedLen = Zstd.decompress(decompressed, compressedData.array());
        if (decompressedLen != srcLength) {
            logger.warn("Zstd decompression size mismatch for chunk {},{}: expected {} but got {}",
                localX, localZ, srcLength, decompressedLen);
        }

        // Deserialize
        return HytaleBsonChunkDeserializer.deserializeChunk(decompressed, localX, localZ, minHeight, maxHeight);
    }

    /**
     * Check if a chunk exists at the given coordinates.
     */
    public boolean hasChunk(int localX, int localZ) {
        int blobIndex = getBlobIndex(localX, localZ);
        return blobIndexBuffer.getInt(blobIndex * 4) != 0;
    }

    private ByteBuffer readBlobHeader(int firstSegment) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(BLOB_HEADER_LENGTH);
        header.order(ByteOrder.BIG_ENDIAN);
        fileChannel.read(header, segmentPosition(firstSegment));
        header.flip();
        return header;
    }

    private int findFreeSegments(int count) {
        int consecutive = 0;
        int start = 1; // Segment 0 is reserved

        for (int i = 1; ; i++) {
            if (!usedSegments.get(i)) {
                if (consecutive == 0) {
                    start = i;
                }
                consecutive++;
                if (consecutive >= count) {
                    return start;
                }
            } else {
                consecutive = 0;
            }
        }
    }

    private int requiredSegments(int dataLength) {
        return (dataLength + segmentSize - 1) / segmentSize;
    }

    private long segmentsBase() {
        return HEADER_LENGTH + (long) blobCount * 4;
    }

    private long segmentPosition(int segmentIndex) {
        return segmentsBase() + (long) (segmentIndex - 1) * segmentSize;
    }

    private int getBlobIndex(int localX, int localZ) {
        return localZ * 32 + localX; // 32 chunks per row in Hytale region
    }

    /**
     * Serialize a chunk to bytes using Hytale's BSON format.
     */
    private byte[] serializeChunk(HytaleChunk chunk) {
        // Use the BSON serializer to produce Hytale-compatible format
        return HytaleBsonChunkSerializer.serializeChunk(chunk);
    }

    @Override
    public void close() throws IOException {
        if (fileChannel != null) {
            fileChannel.close();
            fileChannel = null;
        }
    }

    /**
     * Force all changes to disk.
     */
    public void flush() throws IOException {
        if (fileChannel != null) {
            fileChannel.force(true);
        }
    }

    public Path getPath() {
        return path;
    }

    /**
     * Get the filename for a region file at the given coordinates.
     */
    public static String getRegionFileName(int regionX, int regionZ) {
        return regionX + "." + regionZ + ".region.bin";
    }
}
