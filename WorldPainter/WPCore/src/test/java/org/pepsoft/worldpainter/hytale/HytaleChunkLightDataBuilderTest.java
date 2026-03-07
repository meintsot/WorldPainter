package org.pepsoft.worldpainter.hytale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HytaleChunkLightDataBuilderTest {

    @Test
    public void serializesLightDataInHytaleDiskFormat() {
        HytaleChunkLightDataBuilder builder = new HytaleChunkLightDataBuilder((short) 7);
        short primaryLight = HytaleChunkLightDataBuilder.combineLightValues(4, 5, 6, 7);
        short secondaryLight = HytaleChunkLightDataBuilder.combineLightValues(1, 2, 3, 4);

        builder.setLight(1, 2, 3, 4, 5, 6, 7);
        builder.setLight(31, 31, 31, 1, 2, 3, 4);

        ByteBuf serialized = Unpooled.buffer();
        ByteBuf flatTree = null;
        try {
            builder.serialize(serialized);

            assertEquals(7, serialized.readShort());
            assertTrue(serialized.readBoolean());

            flatTree = deserializeUsingHytaleFormat(serialized);
            assertEquals(primaryLight, getLightRaw(flatTree, HytaleChunkLightDataBuilder.indexBlock(1, 2, 3)));
            assertEquals(secondaryLight, getLightRaw(flatTree, HytaleChunkLightDataBuilder.indexBlock(31, 31, 31)));
            assertEquals(0, getLightRaw(flatTree, HytaleChunkLightDataBuilder.indexBlock(0, 0, 0)));
        } finally {
            serialized.release();
            if (flatTree != null) {
                flatTree.release();
            }
        }
    }

    private static ByteBuf deserializeUsingHytaleFormat(ByteBuf serialized) {
        int length = serialized.readInt();
        ByteBuf from = serialized.readSlice(length);
        ByteBuf to = Unpooled.buffer(Math.max(17, length * 2));
        to.writerIndex(17);
        deserializeOctree(from, to, 0, 0);
        return to;
    }

    private static int deserializeOctree(ByteBuf from, ByteBuf to, int position, int segmentIndex) {
        byte mask = from.readByte();
        to.setByte(position * 17, mask);
        for (int i = 0; i < 8; i++) {
            int value;
            if ((mask & (1 << i)) != 0) {
                int nextSegmentIndex = ++segmentIndex;
                to.writerIndex((nextSegmentIndex + 1) * 17);
                value = nextSegmentIndex;
                segmentIndex = deserializeOctree(from, to, value, nextSegmentIndex);
            } else {
                value = from.readShort() & 0xFFFF;
            }
            to.setShort(position * 17 + 1 + i * 2, value);
        }
        return segmentIndex;
    }

    private static short getLightRaw(ByteBuf flatTree, int index) {
        return getTraverse(flatTree, index, 0, 0);
    }

    private static short getTraverse(ByteBuf flatTree, int index, int segmentIndex, int depth) {
        int position = segmentIndex * 17;
        byte mask = flatTree.getByte(position);
        int childIndex = (index >> (12 - depth)) & 7;
        int childOffset = position + 1 + childIndex * 2;
        int value = flatTree.getUnsignedShort(childOffset);
        if ((mask & (1 << childIndex)) != 0) {
            return getTraverse(flatTree, index, value, depth + 3);
        }
        return (short) value;
    }
}