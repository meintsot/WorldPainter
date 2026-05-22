package org.pepsoft.worldpainter.cloud.tile;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.messages.Messages;
import com.talepainter.protocol.ops.Ops;
import com.talepainter.protocol.tile.TileSnapshotProto;
import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.cloud.auth.Session;
import org.pepsoft.worldpainter.cloud.transport.WebSocketClient;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTileProviderTest {

    /** In-process fake. Records sent messages; lets tests simulate inbound messages. */
    static class FakeWebSocketClient implements WebSocketClient {
        final LinkedBlockingQueue<Messages.ClientMessage> sent = new LinkedBlockingQueue<>();
        final AtomicReference<Consumer<Messages.ServerMessage>> handler = new AtomicReference<>();
        volatile boolean open = false;

        @Override
        public void connect(URI uri, Consumer<Messages.ServerMessage> onMessage) {
            handler.set(onMessage);
            open = true;
        }
        @Override
        public void send(Messages.ClientMessage msg) { sent.offer(msg); }
        @Override
        public boolean isOpen() { return open; }
        @Override
        public void close() { open = false; }

        /** Simulate an inbound message from the server. */
        void receive(Messages.ServerMessage msg) {
            Consumer<Messages.ServerMessage> h = handler.get();
            if (h != null) h.accept(msg);
        }
    }

    @Test
    void connect_sends_auth_and_open_world_messages() throws Exception {
        Session session = new Session(UUID.randomUUID(), "Alice", "#ff0000", "noop-12345");
        UUID worldId = UUID.randomUUID();
        FakeWebSocketClient fake = new FakeWebSocketClient();

        CloudTileProvider provider = new CloudTileProvider(
                URI.create("ws://localhost:8080/ws"), session, worldId, fake);
        try {
            provider.connect();
            // First send: AUTH
            Messages.ClientMessage auth = fake.sent.poll(2, TimeUnit.SECONDS);
            assertThat(auth).isNotNull();
            assertThat(auth.hasAuth()).isTrue();
            assertThat(auth.getAuth().getJwt()).isEqualTo("noop-12345");

            // Server replies AUTH_OK
            fake.receive(Messages.ServerMessage.newBuilder()
                    .setAuthOk(Messages.AuthOk.newBuilder()
                            .setUserId(Common.UserId.newBuilder().setUuid(uuidToBs(session.userId())))
                            .setSessionId("ses-1")
                            .setServerHlc(Common.Hlc.newBuilder().setWallTimeMs(1000)))
                    .build());

            // Second send: OPEN_WORLD
            Messages.ClientMessage openWorld = fake.sent.poll(2, TimeUnit.SECONDS);
            assertThat(openWorld).isNotNull();
            assertThat(openWorld.hasOpenWorld()).isTrue();
            assertThat(openWorld.getOpenWorld().getWorldId().getUuid().size()).isEqualTo(16);

            // Server replies WORLD_OPENED
            fake.receive(Messages.ServerMessage.newBuilder()
                    .setWorldOpened(Messages.WorldOpened.newBuilder()
                            .setRootHlc(Common.Hlc.newBuilder().setWallTimeMs(1000)))
                    .build());

            assertThat(provider.isOpen()).isTrue();
        } finally {
            provider.close();
        }
    }

    @Test
    void getTile_sends_subscribe_and_returns_decoded_snapshot() throws Exception {
        Session session = new Session(UUID.randomUUID(), "Alice", "#ff0000", "noop-12345");
        UUID worldId = UUID.randomUUID();
        FakeWebSocketClient fake = new FakeWebSocketClient();

        CloudTileProvider provider = new CloudTileProvider(
                URI.create("ws://localhost:8080/ws"), session, worldId, fake);
        try {
            provider.connect();
            fake.sent.poll(2, TimeUnit.SECONDS);  // AUTH
            fake.receive(Messages.ServerMessage.newBuilder()
                    .setAuthOk(Messages.AuthOk.newBuilder()
                            .setUserId(Common.UserId.newBuilder().setUuid(uuidToBs(session.userId())))
                            .setSessionId("s").setServerHlc(Common.Hlc.newBuilder().setWallTimeMs(1)))
                    .build());
            fake.sent.poll(2, TimeUnit.SECONDS);  // OPEN_WORLD
            fake.receive(Messages.ServerMessage.newBuilder()
                    .setWorldOpened(Messages.WorldOpened.newBuilder()
                            .setRootHlc(Common.Hlc.newBuilder().setWallTimeMs(1)))
                    .build());

            // Caller asks for tile (0, 0) — this blocks until snapshot arrives.
            // Simulate the snapshot before the call returns via a separate thread.
            Thread snapshotResponder = new Thread(() -> {
                try {
                    Messages.ClientMessage subscribe = fake.sent.poll(2, TimeUnit.SECONDS);
                    assertThat(subscribe).isNotNull();
                    assertThat(subscribe.hasSubscribe()).isTrue();

                    TileSnapshotProto.TileSnapshot snap = TileSnapshotProto.TileSnapshot.newBuilder()
                            .setSnapshotHlc(Common.Hlc.newBuilder().setWallTimeMs(2000))
                            .build();
                    fake.receive(Messages.ServerMessage.newBuilder()
                            .setTileSnapshot(Messages.TileSnapshot.newBuilder()
                                    .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                                    .setBlob(ByteString.copyFrom(snap.toByteArray()))
                                    .setSnapshotHlc(snap.getSnapshotHlc()))
                            .build());
                } catch (Exception e) { /* ignore in test */ }
            });
            snapshotResponder.setDaemon(true);
            snapshotResponder.start();

            LocalTile tile = provider.getTile(0, 0);
            assertThat(tile).isNotNull();
            assertThat(tile.tileX()).isEqualTo(0);
            assertThat(tile.tileY()).isEqualTo(0);
        } finally {
            provider.close();
        }
    }

    @Test
    void setTerrain_enqueues_op_and_applies_locally() throws Exception {
        Session session = new Session(UUID.randomUUID(), "Alice", "#ff0000", "noop-12345");
        UUID worldId = UUID.randomUUID();
        FakeWebSocketClient fake = new FakeWebSocketClient();

        CloudTileProvider provider = new CloudTileProvider(
                URI.create("ws://localhost:8080/ws"), session, worldId, fake);
        try {
            provider.connect();
            fake.sent.poll(2, TimeUnit.SECONDS);
            fake.receive(Messages.ServerMessage.newBuilder()
                    .setAuthOk(Messages.AuthOk.newBuilder()
                            .setUserId(Common.UserId.newBuilder().setUuid(uuidToBs(session.userId())))
                            .setSessionId("s").setServerHlc(Common.Hlc.newBuilder().setWallTimeMs(1)))
                    .build());
            fake.sent.poll(2, TimeUnit.SECONDS);
            fake.receive(Messages.ServerMessage.newBuilder()
                    .setWorldOpened(Messages.WorldOpened.newBuilder()
                            .setRootHlc(Common.Hlc.newBuilder().setWallTimeMs(1)))
                    .build());

            // Pre-populate cache with tile (0, 0) so setTerrain has somewhere to apply.
            provider.injectTileForTest(new LocalTile(0, 0));

            provider.setTerrain(0, 0, 5, 5, (byte) 7);

            // Cell is updated locally immediately.
            assertThat(provider.getCachedTile(0, 0).getTerrain(5, 5)).isEqualTo((byte) 7);

            // Op queue flushes within ~100 ms → SUBMIT_OPS should arrive on the wire.
            Messages.ClientMessage submit = null;
            for (int i = 0; i < 20; i++) {  // up to ~2 s polling
                Messages.ClientMessage m = fake.sent.poll(100, TimeUnit.MILLISECONDS);
                if (m == null) continue;
                if (m.hasSubmitOps()) { submit = m; break; }
            }
            assertThat(submit).isNotNull();
            assertThat(submit.getSubmitOps().getBatch().getOpsCount()).isEqualTo(1);
            Ops.Op op = submit.getSubmitOps().getBatch().getOps(0);
            assertThat(op.getType()).isEqualTo(Ops.OpType.OP_TYPE_TERRAIN);
            assertThat(op.getCell().getX()).isEqualTo(5);
            assertThat(op.getCell().getValue()).isEqualTo(7);
        } finally {
            provider.close();
        }
    }

    private static ByteString uuidToBs(UUID u) {
        byte[] b = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.putLong(u.getMostSignificantBits()); bb.putLong(u.getLeastSignificantBits());
        return ByteString.copyFrom(b);
    }
}
