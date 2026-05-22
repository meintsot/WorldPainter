package org.pepsoft.worldpainter.cloud.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

/**
 * REST helper for cloud heavy-operation jobs: submit / status / result-url / cancel.
 * Used by Cloud → Export / Import / Merge menu actions.
 */
public final class CloudJobsClient {

    private final URI baseUri;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();

    public CloudJobsClient(URI baseUri, String token) {
        this.baseUri = baseUri;
        this.token = token;
    }

    /** Returned by {@link #submit}. */
    public record SubmitResult(UUID jobId, String status) {}

    /** Returned by {@link #getStatus}. */
    public record JobStatus(UUID id, String kind, String status, int progressPct,
                            String errorMessage, String inputBlobKey, String resultBlobKey) {}

    public SubmitResult submit(UUID worldId, String kind, String paramsJson, String inputBlobKey) {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(baseUri.resolve("/v1/worlds/" + worldId + "/jobs"));
            post.setHeader("Authorization", "Bearer " + token);
            StringBuilder body = new StringBuilder();
            body.append("{\"kind\":\"").append(escape(kind)).append("\"");
            body.append(",\"paramsJson\":").append(mapper.writeValueAsString(
                    paramsJson == null ? "{}" : paramsJson));
            if (inputBlobKey != null) {
                body.append(",\"inputBlobKey\":\"").append(escape(inputBlobKey)).append("\"");
            }
            body.append("}");
            post.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
            return http.execute(post, response -> {
                String text = EntityUtils.toString(response.getEntity());
                int code = response.getCode();
                if (code < 200 || code >= 300) {
                    throw new RuntimeException("submit failed: HTTP " + code + " — " + text);
                }
                JsonNode json = mapper.readTree(text);
                return new SubmitResult(
                        UUID.fromString(json.get("id").asText()),
                        json.get("status").asText());
            });
        } catch (Exception e) {
            throw new RuntimeException("submit failed", e);
        }
    }

    public JobStatus getStatus(UUID jobId) {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(baseUri.resolve("/v1/jobs/" + jobId));
            get.setHeader("Authorization", "Bearer " + token);
            return http.execute(get, response -> {
                String text = EntityUtils.toString(response.getEntity());
                JsonNode j = mapper.readTree(text);
                return new JobStatus(
                        UUID.fromString(j.get("id").asText()),
                        j.get("kind").asText(),
                        j.get("status").asText(),
                        j.get("progressPct").asInt(),
                        j.hasNonNull("errorMessage") ? j.get("errorMessage").asText() : null,
                        j.hasNonNull("inputBlobKey") ? j.get("inputBlobKey").asText() : null,
                        j.hasNonNull("resultBlobKey") ? j.get("resultBlobKey").asText() : null);
            });
        } catch (Exception e) {
            throw new RuntimeException("getStatus failed", e);
        }
    }

    public URL getResultUrl(UUID jobId) {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(baseUri.resolve("/v1/jobs/" + jobId + "/result-url"));
            get.setHeader("Authorization", "Bearer " + token);
            return http.execute(get, response -> {
                JsonNode j = mapper.readTree(EntityUtils.toString(response.getEntity()));
                return new URL(j.get("url").asText());
            });
        } catch (Exception e) {
            throw new RuntimeException("getResultUrl failed", e);
        }
    }

    public void cancel(UUID jobId) {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpDelete del = new HttpDelete(baseUri.resolve("/v1/jobs/" + jobId));
            del.setHeader("Authorization", "Bearer " + token);
            http.execute(del, response -> null);
        } catch (Exception e) {
            throw new RuntimeException("cancel failed", e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
