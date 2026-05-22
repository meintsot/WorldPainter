package org.pepsoft.worldpainter.cloud.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialsStoreTest {

    private final CredentialsStore store = new CredentialsStore("test-" + UUID.randomUUID());

    @AfterEach
    void cleanup() throws Exception {
        store.clear();
    }

    @Test
    void save_then_load_round_trips_session() {
        Session original = new Session(UUID.randomUUID(), "Alice", "#ff0000", "noop-12345");
        store.save(original);
        Session loaded = store.load().orElseThrow();
        assertThat(loaded.userId()).isEqualTo(original.userId());
        assertThat(loaded.displayName()).isEqualTo("Alice");
        assertThat(loaded.color()).isEqualTo("#ff0000");
        assertThat(loaded.token()).isEqualTo("noop-12345");
    }

    @Test
    void load_returns_empty_when_no_session_saved() {
        assertThat(store.load()).isEmpty();
    }

    @Test
    void clear_removes_session() {
        store.save(new Session(UUID.randomUUID(), "X", "#0", "tok"));
        store.clear();
        assertThat(store.load()).isEmpty();
    }
}
