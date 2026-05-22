package org.pepsoft.worldpainter.cloud.auth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Disabled("Requires backend stack at http://localhost:8080 — run manually after docker compose up")
class AuthClientIntegrationTest {

    @Test
    void login_returns_session_with_noop_token() {
        AuthClient client = new AuthClient(URI.create("http://localhost:8080"));
        Session session = client.login("Integration Tester");
        assertThat(session.token()).startsWith("noop-");
        assertThat(session.displayName()).isEqualTo("Integration Tester");
        assertThat(session.userId()).isNotNull();
    }
}
