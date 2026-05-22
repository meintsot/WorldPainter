package org.pepsoft.worldpainter.cloud.crdt;

import com.talepainter.protocol.common.Common;

import java.time.Clock;

/**
 * Hybrid Logical Clock generator. Thread-safe. Mirror of the backend {@code HlcClock} but
 * emits {@link Common.Hlc} protobuf values directly.
 */
public final class ClientHlcClock {

    private final Clock systemClock;
    private final int nodeId;
    private long lastWall;
    private int lastCounter;

    public ClientHlcClock(Clock systemClock, int nodeId) {
        this.systemClock = systemClock;
        this.nodeId = nodeId;
        this.lastWall = 0L;
        this.lastCounter = 0;
    }

    public synchronized Common.Hlc next() {
        long now = Math.max(systemClock.millis(), lastWall);
        if (now == lastWall) lastCounter += 1;
        else { lastWall = now; lastCounter = 0; }
        return Common.Hlc.newBuilder()
                .setWallTimeMs(lastWall).setCounter(lastCounter).setNodeId(nodeId).build();
    }

    public synchronized void observe(Common.Hlc remote) {
        long now = systemClock.millis();
        long wall = Math.max(Math.max(now, lastWall), remote.getWallTimeMs());
        int counter;
        if (wall == lastWall && wall == remote.getWallTimeMs()) {
            counter = Math.max(lastCounter, remote.getCounter()) + 1;
        } else if (wall == lastWall) {
            counter = lastCounter + 1;
        } else if (wall == remote.getWallTimeMs()) {
            counter = remote.getCounter() + 1;
        } else {
            counter = 0;
        }
        lastWall = wall;
        lastCounter = counter;
    }

    public int nodeId() { return nodeId; }
}
