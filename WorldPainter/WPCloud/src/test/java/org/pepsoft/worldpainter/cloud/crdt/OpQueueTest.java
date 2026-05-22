package org.pepsoft.worldpainter.cloud.crdt;

import com.talepainter.protocol.ops.Ops;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpQueueTest {

    @Test
    void enqueued_ops_are_flushed_in_batch_after_interval() throws Exception {
        ClientHlcClock clock = new ClientHlcClock(Clock.systemUTC(), 1);
        OpGenerator gen = new OpGenerator(clock);

        AtomicReference<Ops.OpBatch> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        OpQueue queue = new OpQueue(50, batch -> {
            received.set(batch);
            latch.countDown();
        });
        queue.start();

        queue.enqueue(gen.terrainWrite(0, 0, 5, 5, (byte) 1));
        queue.enqueue(gen.terrainWrite(0, 0, 5, 6, (byte) 2));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getOpsCount()).isEqualTo(2);
        queue.stop();
    }

    @Test
    void same_cell_writes_are_coalesced_within_a_batch() throws Exception {
        ClientHlcClock clock = new ClientHlcClock(Clock.systemUTC(), 1);
        OpGenerator gen = new OpGenerator(clock);

        AtomicReference<Ops.OpBatch> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        OpQueue queue = new OpQueue(50, b -> { received.set(b); latch.countDown(); });
        queue.start();

        queue.enqueue(gen.terrainWrite(0, 0, 5, 5, (byte) 1));
        queue.enqueue(gen.terrainWrite(0, 0, 5, 5, (byte) 2));
        queue.enqueue(gen.terrainWrite(0, 0, 5, 5, (byte) 3));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getOpsCount()).isEqualTo(1);
        assertThat(received.get().getOps(0).getCell().getValue()).isEqualTo(3);
        queue.stop();
    }
}
