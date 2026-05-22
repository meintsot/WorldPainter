package org.pepsoft.worldpainter.cloud.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST helper for cloud world listing and creation. Used by the WPGUI cloud world picker dialog.
 */
public final class CloudWorldsClient {

    private final URI baseUri;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();

    public CloudWorldsClient(URI baseUri, String token) {
        this.baseUri = baseUri;
        this.token = token;
    }

    public List<WorldSummary> listWorlds() {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(baseUri.resolve("/v1/worlds"));
            get.setHeader("Authorization", "Bearer " + token);
            return http.execute(get, response -> {
                JsonNode body = mapper.readTree(EntityUtils.toString(response.getEntity()));
                List<WorldSummary> out = new ArrayList<>();
                for (JsonNode w : body.get("worlds")) {
                    out.add(new WorldSummary(
                            UUID.fromString(w.get("id").asText()),
                            w.get("name").asText(),
                            w.get("platform").asText(),
                            w.get("storageMode").asText()));
                }
                return out;
            });
        } catch (Exception e) {
            throw new RuntimeException("listWorlds failed", e);
        }
    }

    public UUID createWorld(String name, String platform) {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(baseUri.resolve("/v1/worlds"));
            post.setHeader("Authorization", "Bearer " + token);
            String body = "{\"name\":\"" + escape(name) + "\",\"platform\":\"" + escape(platform) + "\"}";
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            return http.execute(post, response -> {
                JsonNode json = mapper.readTree(EntityUtils.toString(response.getEntity()));
                return UUID.fromString(json.get("id").asText());
            });
        } catch (Exception e) {
            throw new RuntimeException("createWorld failed", e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record WorldSummary(UUID id, String name, String platform, String storageMode) {}
}
