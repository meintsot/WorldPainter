package org.pepsoft.worldpainter.cloud.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.cloud.auth.AuthClient;
import org.pepsoft.worldpainter.cloud.auth.Session;
import org.pepsoft.worldpainter.cloud.tile.CloudTileProvider;
import org.pepsoft.worldpainter.cloud.tile.LocalTile;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Disabled("Requires backend stack at http://localhost:8080 — run manually after docker compose up")
class SingleClientRoundTripTest {

    @Test
    void paint_persists_across_provider_close_and_reopen() throws Exception {
        URI httpBase = URI.create("http://localhost:8080");
        URI wsBase   = URI.create("ws://localhost:8080/ws");

        AuthClient auth = new AuthClient(httpBase);
        Session session = auth.login("E2E Single-Client Tester");

        UUID worldId = createWorld(httpBase, session.token(), "e2e-single-" + UUID.randomUUID());

        // First provider: write a cell.
        try (CloudTileProvider provider = new CloudTileProvider(wsBase, session, worldId)) {
            provider.connect();
            waitUntil(() -> provider.isOpen(), 5_000);
            LocalTile tile = provider.getTile(0, 0);
            assertThat(tile).isNotNull();
            provider.setTerrain(0, 0, 5, 5, (byte) 7);
            // Give the op queue a flush window and the server time to persist.
            Thread.sleep(300);
        }

        // Second provider (same worldId, new session): read it back.
        Session session2 = auth.login("E2E Single-Client Tester 2");
        try (CloudTileProvider provider = new CloudTileProvider(wsBase, session2, worldId)) {
            provider.connect();
            waitUntil(() -> provider.isOpen(), 5_000);
            LocalTile tile = provider.getTile(0, 0);
            assertThat(tile.getTerrain(5, 5)).isEqualTo((byte) 7);
        }
    }

    private static UUID createWorld(URI baseUri, String token, String name) throws Exception {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(baseUri.resolve("/v1/worlds"));
            post.setHeader("Authorization", "Bearer " + token);
            post.setEntity(new StringEntity(
                    "{\"name\":\"" + name + "\",\"platform\":\"hytale\"}",
                    ContentType.APPLICATION_JSON));
            return http.execute(post, response -> {
                String body = EntityUtils.toString(response.getEntity());
                JsonNode node = new ObjectMapper().readTree(body);
                return UUID.fromString(node.get("id").asText());
            });
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within " + timeoutMs + "ms");
            }
            Thread.sleep(50);
        }
    }
}
