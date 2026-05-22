package org.pepsoft.worldpainter.cloud.transport;

import com.talepainter.protocol.messages.Messages;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolCodecTest {

    @Test
    void encode_then_decode_round_trips_a_heartbeat() {
        Messages.ClientMessage msg = Messages.ClientMessage.newBuilder()
                .setHeartbeat(Messages.Heartbeat.newBuilder()).build();
        ByteBuffer encoded = ProtocolCodec.encode(msg);
        Messages.ClientMessage decoded = ProtocolCodec.decodeClient(encoded);
        assertThat(decoded.hasHeartbeat()).isTrue();
    }

    @Test
    void decode_server_message_round_trips_an_auth_ok() {
        Messages.ServerMessage msg = Messages.ServerMessage.newBuilder()
                .setAuthOk(Messages.AuthOk.newBuilder().setSessionId("sid-1")).build();
        ByteBuffer encoded = ByteBuffer.wrap(msg.toByteArray());
        Messages.ServerMessage decoded = ProtocolCodec.decodeServer(encoded);
        assertThat(decoded.hasAuthOk()).isTrue();
        assertThat(decoded.getAuthOk().getSessionId()).isEqualTo("sid-1");
    }
}
