package org.pepsoft.worldpainter.cloud.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CloudSessionTest {

    private CloudSession session;
    private CredentialsStore store;

    @BeforeEach
    void setUp() {
        store = new CredentialsStore("test-" + UUID.randomUUID());
        session = new CloudSession(store);
    }

    @AfterEach
    void cleanUp() {
        store.clear();
    }

    @Test
    void signed_out_by_default() {
        assertThat(session.isSignedIn()).isFalse();
        assertThat(session.current()).isEmpty();
    }

    @Test
    void signing_in_makes_session_available_and_persists_via_store() {
        Session s = new Session(UUID.randomUUID(), "Alice", "#ff0000", "noop-12345");
        session.signIn(s);
        assertThat(session.isSignedIn()).isTrue();
        assertThat(session.current()).contains(s);
        assertThat(store.load()).contains(s);   // persisted
    }

    @Test
    void signing_out_clears_session_and_store() {
        Session s = new Session(UUID.randomUUID(), "Bob", "#00ff00", "noop-67890");
        session.signIn(s);
        session.signOut();
        assertThat(session.isSignedIn()).isFalse();
        assertThat(session.current()).isEmpty();
        assertThat(store.load()).isEmpty();
    }

    @Test
    void restore_loads_saved_session_from_store() {
        Session saved = new Session(UUID.randomUUID(), "Persisted", "#0000ff", "noop-persisted");
        store.save(saved);
        // Create a NEW CloudSession over the same store
        CloudSession restored = new CloudSession(store);
        assertThat(restored.tryRestore()).isTrue();
        assertThat(restored.current()).contains(saved);
    }

    @Test
    void tryRestore_returns_false_when_store_empty() {
        // store is empty by default in setUp
        assertThat(session.tryRestore()).isFalse();
        assertThat(session.isSignedIn()).isFalse();
    }

    @Test
    void listener_receives_signIn_signOut_events() {
        AtomicReference<Session> lastSignIn = new AtomicReference<>();
        AtomicReference<Boolean> signedOutEvent = new AtomicReference<>(false);
        session.addListener(new CloudSession.Listener() {
            @Override public void onSignIn(Session s) { lastSignIn.set(s); }
            @Override public void onSignOut() { signedOutEvent.set(true); }
        });
        Session s = new Session(UUID.randomUUID(), "Alice", "#ff0000", "noop-1");
        session.signIn(s);
        assertThat(lastSignIn.get()).isEqualTo(s);
        session.signOut();
        assertThat(signedOutEvent.get()).isTrue();
    }
}
