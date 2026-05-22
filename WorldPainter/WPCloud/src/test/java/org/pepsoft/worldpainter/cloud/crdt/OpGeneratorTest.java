package org.pepsoft.worldpainter.cloud.crdt;

import com.talepainter.protocol.ops.Ops;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class OpGeneratorTest {

    @Test
    void cell_op_carries_all_fields() {
        ClientHlcClock clock = new ClientHlcClock(Clock.systemUTC(), 1);
        OpGenerator gen = new OpGenerator(clock);
        Ops.Op op = gen.terrainWrite(5, 7, 15, 22, (byte) 3);
        assertThat(op.getType()).isEqualTo(Ops.OpType.OP_TYPE_TERRAIN);
        assertThat(op.getTileId().getTileX()).isEqualTo(5);
        assertThat(op.getTileId().getTileY()).isEqualTo(7);
        assertThat(op.getCell().getX()).isEqualTo(15);
        assertThat(op.getCell().getY()).isEqualTo(22);
        assertThat(op.getCell().getValue()).isEqualTo(3);
        assertThat(op.getOpId().getUuid().size()).isEqualTo(16);
        assertThat(op.getHlc().getNodeId()).isEqualTo(1);
    }
}
