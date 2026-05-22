package org.pepsoft.worldpainter.cloud.transport;

import com.talepainter.protocol.messages.Messages;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Disabled("Requires backend stack at ws://localhost:8080/ws — run manually after starting docker compose")
class TyrusWebSocketClientTest {

    @Test
    void heartbeat_round_trips_against_running_backend() throws Exception {
        LinkedBlockingQueue<Messages.ServerMessage> inbox = new LinkedBlockingQueue<>();
        try (WebSocketClient client = new TyrusWebSocketClient()) {
            client.connect(URI.create("ws://localhost:8080/ws"), inbox::offer);

            client.send(Messages.ClientMessage.newBuilder()
                    .setHeartbeat(Messages.Heartbeat.newBuilder()).build());

            Messages.ServerMessage resp = inbox.poll(2, TimeUnit.SECONDS);
            assertThat(resp).isNotNull();
            assertThat(resp.hasHeartbeatAck()).isTrue();
        }
    }
}
