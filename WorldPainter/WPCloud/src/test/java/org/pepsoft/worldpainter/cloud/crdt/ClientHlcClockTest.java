package org.pepsoft.worldpainter.cloud.crdt;

import com.talepainter.protocol.common.Common;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClientHlcClockTest {

    private Clock fixedAt(long epochMs) {
        return Clock.fixed(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
    }

    @Test
    void next_advances_counter_within_same_wall_ms() {
        ClientHlcClock clock = new ClientHlcClock(fixedAt(1000), 1);
        Common.Hlc a = clock.next();
        Common.Hlc b = clock.next();
        assertThat(a.getWallTimeMs()).isEqualTo(1000);
        assertThat(b.getWallTimeMs()).isEqualTo(1000);
        assertThat(b.getCounter()).isGreaterThan(a.getCounter());
    }

    @Test
    void observe_advances_to_received_hlc_when_higher() {
        ClientHlcClock clock = new ClientHlcClock(fixedAt(1000), 1);
        clock.observe(Common.Hlc.newBuilder().setWallTimeMs(5000).setCounter(0).setNodeId(99).build());
        Common.Hlc next = clock.next();
        assertThat(next.getWallTimeMs()).isGreaterThanOrEqualTo(5000);
    }

    @Test
    void nodeId_is_preserved_in_emitted_hlcs() {
        ClientHlcClock clock = new ClientHlcClock(fixedAt(1000), 42);
        assertThat(clock.next().getNodeId()).isEqualTo(42);
    }
}
