package org.pepsoft.worldpainter.cloud.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.cloud.auth.AuthClient;
import org.pepsoft.worldpainter.cloud.auth.Session;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Disabled("Requires backend stack at http://localhost:8080 — run manually after docker compose up")
class CloudWorldsClientIntegrationTest {

    @Test
    void list_and_create_round_trip() {
        URI base = URI.create("http://localhost:8080");
        Session session = new AuthClient(base).login("CloudWorldsClient Tester");
        CloudWorldsClient client = new CloudWorldsClient(base, session.token());

        // Initial list might be empty for a freshly-minted user
        List<CloudWorldsClient.WorldSummary> initial = client.listWorlds();
        int initialSize = initial.size();

        // Create a new world
        String name = "test-world-" + UUID.randomUUID();
        UUID newId = client.createWorld(name, "hytale");
        assertThat(newId).isNotNull();

        // It appears in the list
        List<CloudWorldsClient.WorldSummary> afterCreate = client.listWorlds();
        assertThat(afterCreate).hasSize(initialSize + 1);
        assertThat(afterCreate).anyMatch(w -> w.id().equals(newId) && name.equals(w.name()));
    }
}
