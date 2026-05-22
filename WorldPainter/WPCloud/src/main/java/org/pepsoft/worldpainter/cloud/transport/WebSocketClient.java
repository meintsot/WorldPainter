package org.pepsoft.worldpainter.cloud.transport;

import com.talepainter.protocol.messages.Messages;

import java.net.URI;
import java.util.function.Consumer;

/**
 * Minimal client interface for the TalePainter backend WebSocket.
 *
 * <p>Single-connection. The instance is single-use: {@link #connect} once, then {@link #send}
 * and receive via the listener until {@link #close}. Reconnection is a higher-layer concern
 * (deferred to TD-030).
 */
public interface WebSocketClient extends AutoCloseable {

    void connect(URI uri, Consumer<Messages.ServerMessage> onMessage);

    /** Send a message. Thread-safe. */
    void send(Messages.ClientMessage msg);

    boolean isOpen();

    @Override
    void close();
}
