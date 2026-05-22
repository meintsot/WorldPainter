package org.pepsoft.worldpainter.cloud.auth;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide holder for the currently-signed-in {@link Session}. Backed by
 * {@link CredentialsStore} for persistence across app restarts.
 *
 * <p>WPGUI typically obtains the singleton via {@link #getInstance()}; tests pass a
 * specific {@link CredentialsStore} to the {@link #CloudSession(CredentialsStore)} constructor
 * for isolation.
 */
public final class CloudSession {

    private static final CloudSession INSTANCE = new CloudSession(new CredentialsStore());

    public static CloudSession getInstance() { return INSTANCE; }

    private final CredentialsStore store;
    private final AtomicReference<Session> current = new AtomicReference<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public CloudSession(CredentialsStore store) {
        this.store = store;
    }

    public Optional<Session> current() {
        return Optional.ofNullable(current.get());
    }

    public boolean isSignedIn() {
        return current.get() != null;
    }

    public void signIn(Session session) {
        current.set(session);
        store.save(session);
        for (Listener l : listeners) {
            try { l.onSignIn(session); } catch (Exception ignored) {}
        }
    }

    public void signOut() {
        current.set(null);
        store.clear();
        for (Listener l : listeners) {
            try { l.onSignOut(); } catch (Exception ignored) {}
        }
    }

    /**
     * Attempt to load a previously-saved session from the {@link CredentialsStore}. Returns
     * {@code true} if a session was restored. Does NOT fire listeners (caller decides whether
     * restoration counts as a UX-visible "sign in" event).
     */
    public boolean tryRestore() {
        Optional<Session> saved = store.load();
        if (saved.isEmpty()) return false;
        current.set(saved.get());
        return true;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public interface Listener {
        default void onSignIn(Session session) {}
        default void onSignOut() {}
    }
}
