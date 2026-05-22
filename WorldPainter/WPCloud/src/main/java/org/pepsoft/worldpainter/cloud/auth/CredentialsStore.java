package org.pepsoft.worldpainter.cloud.auth;

import java.util.Optional;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Persists the current {@link Session} via the Java {@link Preferences} API.
 *
 * <p><strong>Note:</strong> Java {@code Preferences} stores values in plain text on Linux and macOS.
 * Acceptable for Phase 0 NoOp tokens; real OAuth tokens (Phase 1) should use OS-native secure
 * storage (tracked as TD-031).
 */
public final class CredentialsStore {

    private static final String K_USER_ID = "userId";
    private static final String K_DISPLAY_NAME = "displayName";
    private static final String K_COLOR = "color";
    private static final String K_TOKEN = "token";

    private final Preferences prefs;

    public CredentialsStore() { this("default"); }

    public CredentialsStore(String namespace) {
        this.prefs = Preferences.userRoot()
                .node("org/pepsoft/worldpainter/cloud/auth")
                .node(namespace);
    }

    public void save(Session session) {
        prefs.put(K_USER_ID, session.userId().toString());
        prefs.put(K_DISPLAY_NAME, session.displayName());
        prefs.put(K_COLOR, session.color() == null ? "" : session.color());
        prefs.put(K_TOKEN, session.token());
        try { prefs.flush(); } catch (BackingStoreException e) {
            throw new RuntimeException("failed to flush credentials", e);
        }
    }

    public Optional<Session> load() {
        String token = prefs.get(K_TOKEN, null);
        if (token == null || token.isBlank()) return Optional.empty();
        String userIdStr = prefs.get(K_USER_ID, null);
        if (userIdStr == null) return Optional.empty();
        return Optional.of(new Session(
                UUID.fromString(userIdStr),
                prefs.get(K_DISPLAY_NAME, "Anonymous"),
                prefs.get(K_COLOR, "#888888"),
                token
        ));
    }

    public void clear() {
        prefs.remove(K_USER_ID);
        prefs.remove(K_DISPLAY_NAME);
        prefs.remove(K_COLOR);
        prefs.remove(K_TOKEN);
        try { prefs.flush(); } catch (BackingStoreException ignored) {}
    }
}
