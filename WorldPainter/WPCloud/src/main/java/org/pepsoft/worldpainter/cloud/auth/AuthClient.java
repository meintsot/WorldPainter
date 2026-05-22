package org.pepsoft.worldpainter.cloud.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URI;
import java.util.UUID;

/**
 * REST client for the backend's {@code /v1/auth/login} endpoint. Phase 0 only — Phase 1 swaps
 * in an OAuth-aware client.
 */
public final class AuthClient {

    private final URI baseUri;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthClient(URI baseUri) { this.baseUri = baseUri; }

    public Session login(String displayName) {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(baseUri.resolve("/v1/auth/login"));
            String body = "{\"displayName\":\"" + escape(displayName) + "\"}";
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

            return http.execute(post, response -> {
                int status = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (status != HttpStatus.SC_OK) {
                    throw new RuntimeException("login failed: HTTP " + status + " " + responseBody);
                }
                JsonNode node = mapper.readTree(responseBody);
                return new Session(
                        UUID.fromString(node.get("userId").asText()),
                        node.get("displayName").asText(),
                        node.get("color").asText(),
                        node.get("token").asText()
                );
            });
        } catch (Exception e) {
            throw new RuntimeException("AuthClient.login failed for " + baseUri, e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
