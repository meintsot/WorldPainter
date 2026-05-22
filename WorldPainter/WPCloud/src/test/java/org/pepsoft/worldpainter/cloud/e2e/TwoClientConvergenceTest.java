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
class TwoClientConvergenceTest {

    @Test
    void two_clients_writing_same_cell_converge_with_lww() throws Exception {
        URI httpBase = URI.create("http://localhost:8080");
        URI wsBase   = URI.create("ws://localhost:8080/ws");

        AuthClient auth = new AuthClient(httpBase);
        Session sessionA = auth.login("E2E Client A");
        Session sessionB = auth.login("E2E Client B");

        UUID worldId = createWorld(httpBase, sessionA.token(), "e2e-convergence-" + UUID.randomUUID());

        try (CloudTileProvider a = new CloudTileProvider(wsBase, sessionA, worldId);
             CloudTileProvider b = new CloudTileProvider(wsBase, sessionB, worldId)) {

            a.connect(); b.connect();
            waitUntil(() -> a.isOpen() && b.isOpen(), 5_000);

            // Both subscribe to tile (0, 0).
            LocalTile tileA = a.getTile(0, 0);
            LocalTile tileB = b.getTile(0, 0);
            assertThat(tileA).isNotNull();
            assertThat(tileB).isNotNull();

            // Both write the same cell. Whichever provider has the higher node_id wins
            // because they generate ops with nearly identical wall times.
            a.setTerrain(0, 0, 5, 5, (byte) 1);
            b.setTerrain(0, 0, 5, 5, (byte) 2);

            // Wait for batches to flush and broadcasts to propagate.
            Thread.sleep(500);

            // Open a third provider to observe the converged state directly from the backend.
            Session sessionC = auth.login("E2E Client C (observer)");
            try (CloudTileProvider c = new CloudTileProvider(wsBase, sessionC, worldId)) {
                c.connect();
                waitUntil(c::isOpen, 5_000);
                LocalTile observed = c.getTile(0, 0);
                byte serverValue = observed.getTerrain(5, 5);
                assertThat(serverValue).isIn((byte) 1, (byte) 2);

                Thread.sleep(200);  // let any pending A/B broadcasts arrive
                LocalTile aTile = a.getCachedTile(0, 0);
                LocalTile bTile = b.getCachedTile(0, 0);
                assertThat(aTile.getTerrain(5, 5)).isEqualTo(serverValue);
                assertThat(bTile.getTerrain(5, 5)).isEqualTo(serverValue);
            }
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
