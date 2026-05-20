package org.pepsoft.worldpainter.hytale.chunk;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Palette / bit-field / UTF / half-byte encoding helpers used by
 * {@link HytaleBsonChunkSerializer} and {@link HytaleSectionBodyEncoder}.
 *
 * <p>Pure utility class — no state, no instances.</p>
 */
final class HytaleBsonPaletteCodec {

    // Palette type ordinals from PaletteType enum
    static final int PALETTE_TYPE_EMPTY = 0;
    static final int PALETTE_TYPE_HALF_BYTE = 1;
    static final int PALETTE_TYPE_BYTE = 2;
    static final int PALETTE_TYPE_SHORT = 3;

    private HytaleBsonPaletteCodec() {
        // Utility class
    }

    /**
     * Write a ShortBytePalette (used for heightmap).
     * Format: palette optimization based on unique values
     */
    static void writeShortBytePalette(ByteBuf buf, short[] values) {
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
    static void writeIntBytePalette(ByteBuf buf, int[] values) {
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
    static byte[] buildBitFieldArray(int bits, int length, int[] values) {
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
     * Write block indices as half-byte (nibble) packed data.
     * Each byte contains two 4-bit palette indices.
     * Total: 32768 blocks / 2 = 16384 bytes
     *
     * Hytale's BitUtil.setNibble uses XOR-based shifting:
     *   shift = ((idx & 1) ^ 1) << 2
     * which yields shift=4 for even indices and shift=0 for odd,
     * meaning:
     * - Even indices → HIGH nibble (bits 4-7)
     * - Odd indices  → LOW nibble  (bits 0-3)
     */
    static void writeHalfByteBlockData(ByteBuf buf, int[] blockIndices) {
        for (int i = 0; i < blockIndices.length; i += 2) {
            int even = blockIndices[i] & 0x0F;      // Even index → HIGH nibble
            int odd = (i + 1 < blockIndices.length) ? (blockIndices[i + 1] & 0x0F) : 0;  // Odd index → LOW nibble
            buf.writeByte((even << 4) | odd);
        }
    }

    /**
     * Write UTF-8 string in Hytale format.
     */
    static void writeUtf(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}
