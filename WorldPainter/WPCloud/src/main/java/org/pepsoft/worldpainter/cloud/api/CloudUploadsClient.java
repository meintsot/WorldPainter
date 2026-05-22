package org.pepsoft.worldpainter.cloud.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Uploads input blobs (e.g., Hytale world zips for Import/Merge) via the presigned-URL flow:
 * 1. POST /v1/uploads to obtain a presigned PUT URL + blob key
 * 2. PUT the bytes directly to the URL (typically goes straight to MinIO/R2)
 *
 * <p>Returns the blob key for use as {@code SubmitJobRequest.inputBlobKey}.
 */
public final class CloudUploadsClient {

    private final URI baseUri;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();

    public CloudUploadsClient(URI baseUri, String token) {
        this.baseUri = baseUri;
        this.token = token;
    }

    public String uploadZip(File zipFile, String purpose) {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            String encodedPurpose = URLEncoder.encode(purpose, StandardCharsets.UTF_8);
            HttpPost req = new HttpPost(baseUri.resolve("/v1/uploads?purpose=" + encodedPurpose));
            req.setHeader("Authorization", "Bearer " + token);
            String[] keyAndUrl = http.execute(req, response -> {
                JsonNode j = mapper.readTree(EntityUtils.toString(response.getEntity()));
                return new String[] {
                        j.get("blobKey").asText(),
                        j.get("uploadUrl").asText() };
            });
            String blobKey = keyAndUrl[0];
            String putUrl = keyAndUrl[1];

            HttpPut put = new HttpPut(URI.create(putUrl));
            put.setEntity(new FileEntity(zipFile, ContentType.APPLICATION_OCTET_STREAM));
            http.execute(put, response -> {
                int code = response.getCode();
                if (code < 200 || code >= 300) {
                    throw new RuntimeException("PUT upload failed: HTTP " + code);
                }
                return null;
            });
            return blobKey;
        } catch (Exception e) {
            throw new RuntimeException("uploadZip failed", e);
        }
    }
}
