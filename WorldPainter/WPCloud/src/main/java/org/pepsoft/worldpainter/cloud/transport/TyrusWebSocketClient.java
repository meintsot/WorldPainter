package org.pepsoft.worldpainter.cloud.transport;

import com.talepainter.protocol.messages.Messages;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Tyrus-backed implementation of {@link WebSocketClient}. Uses Jakarta WebSocket APIs.
 *
 * <p>Max binary message buffer is set to 2 MB to accommodate {@code TILE_SNAPSHOT} frames
 * (an empty 128x128 tile encodes to ~114 KB; full tiles can reach several hundred KB).
 */
public final class TyrusWebSocketClient extends Endpoint implements WebSocketClient {

    private static final Logger LOG = LoggerFactory.getLogger(TyrusWebSocketClient.class);
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;

    private final AtomicReference<Session> sessionRef = new AtomicReference<>();
    private Consumer<Messages.ServerMessage> onMessage;

    @Override
    public void connect(URI uri, Consumer<Messages.ServerMessage> onMessage) {
        this.onMessage = onMessage;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(MAX_FRAME_BYTES);
        try {
            Session session = container.connectToServer(this,
                    ClientEndpointConfig.Builder.create().build(), uri);
            session.setMaxBinaryMessageBufferSize(MAX_FRAME_BYTES);
            sessionRef.set(session);
        } catch (Exception e) {
            throw new RuntimeException("WebSocket connect failed for " + uri, e);
        }
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        session.setMaxBinaryMessageBufferSize(MAX_FRAME_BYTES);
        session.addMessageHandler((MessageHandler.Whole<ByteBuffer>) frame -> {
            try {
                Messages.ServerMessage msg = ProtocolCodec.decodeServer(frame);
                if (onMessage != null) onMessage.accept(msg);
            } catch (Exception e) {
                LOG.error("Failed to decode inbound WS frame", e);
            }
        });
    }

    @Override
    public void send(Messages.ClientMessage msg) {
        Session s = sessionRef.get();
        if (s == null || !s.isOpen()) {
            throw new IllegalStateException("WebSocket is not open");
        }
        try {
            s.getAsyncRemote().sendBinary(ProtocolCodec.encode(msg), result -> {
                if (result.getException() != null) {
                    LOG.error("WS send failed", result.getException());
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("WebSocket send failed", e);
        }
    }

    @Override
    public boolean isOpen() {
        Session s = sessionRef.get();
        return s != null && s.isOpen();
    }

    @Override
    public void close() {
        Session s = sessionRef.getAndSet(null);
        if (s != null && s.isOpen()) {
            try { s.close(); } catch (Exception e) { LOG.warn("WS close error", e); }
        }
    }
}
