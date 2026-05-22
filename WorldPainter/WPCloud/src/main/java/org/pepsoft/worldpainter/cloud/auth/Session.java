package org.pepsoft.worldpainter.cloud.auth;

import java.util.UUID;

public record Session(UUID userId, String displayName, String color, String token) {

    public Session {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("token required");
        if (userId == null) throw new IllegalArgumentException("userId required");
    }
}
