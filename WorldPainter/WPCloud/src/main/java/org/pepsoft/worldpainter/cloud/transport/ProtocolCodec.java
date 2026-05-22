package org.pepsoft.worldpainter.cloud.transport;

import com.google.protobuf.InvalidProtocolBufferException;
import com.talepainter.protocol.messages.Messages;

import java.nio.ByteBuffer;

/**
 * Encodes {@link Messages.ClientMessage} to a {@link ByteBuffer} for the WebSocket transport and
 * decodes inbound {@link Messages.ServerMessage} frames.
 */
public final class ProtocolCodec {

    private ProtocolCodec() {}

    public static ByteBuffer encode(Messages.ClientMessage msg) {
        return ByteBuffer.wrap(msg.toByteArray());
    }

    public static Messages.ServerMessage decodeServer(ByteBuffer frame) {
        try {
            byte[] bytes = new byte[frame.remaining()];
            frame.get(bytes);
            return Messages.ServerMessage.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("malformed server message", e);
        }
    }

    public static Messages.ClientMessage decodeClient(ByteBuffer frame) {
        try {
            byte[] bytes = new byte[frame.remaining()];
            frame.get(bytes);
            return Messages.ClientMessage.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("malformed client message", e);
        }
    }
}
