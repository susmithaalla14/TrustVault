package com.trustvault.service;

import com.trustvault.config.SupabaseStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side Supabase Storage client using Java 21's native HttpClient.
 * Securely interacts with the private Supabase Storage REST API using the
 * server-side service role key.
 *
 * All sensitive document objects remain strictly private. Direct public reads
 * are never enabled.
 */
@Service
@Slf4j
public class SupabaseStorageService implements StorageService {

    private final SupabaseStorageProperties properties;
    private final HttpClient httpClient;

    // In-memory fallback for local development or automated testing when credentials are not supplied
    private final Map<String, byte[]> localFallbackStore = new ConcurrentHashMap<>();

    public SupabaseStorageService(SupabaseStorageProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String upload(String objectPath, byte[] content, String contentType) throws IOException {
        if (!properties.isConfigured()) {
            log.warn("Supabase credentials not configured. Storing document '{}' in local in-memory fallback.", objectPath);
            localFallbackStore.put(objectPath, content);
            return objectPath;
        }

        String sanitizedPath = sanitizePath(objectPath);
        String endpoint = String.format("%s/storage/v1/object/%s/%s",
                cleanBaseUrl(properties.getUrl()),
                properties.getBucket(),
                sanitizedPath);

        log.info("Uploading document to Supabase Storage: bucket='{}', path='{}'", properties.getBucket(), sanitizedPath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                .header("apikey", properties.getServiceRoleKey())
                .header("Content-Type", (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream")
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully uploaded object to Supabase Storage: path='{}'", sanitizedPath);
                return sanitizedPath;
            } else if (response.statusCode() == 400 || response.statusCode() == 404) {
                log.warn("Supabase Storage upload failed with status {}: {}. Attempting to ensure bucket exists and retry...", response.statusCode(), response.body());
                if (ensureBucketExists()) {
                    HttpResponse<String> retryResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (retryResponse.statusCode() >= 200 && retryResponse.statusCode() < 300) {
                        log.info("Successfully uploaded object to Supabase Storage after bucket creation: path='{}'", sanitizedPath);
                        return sanitizedPath;
                    }
                }
                throw new IOException("Failed to upload document to Supabase Storage: " + response.body() + " (Status: " + response.statusCode() + ")");
            } else {
                log.error("Supabase Storage upload failed with status {}: {}", response.statusCode(), response.body());
                throw new IOException("Failed to upload document to Supabase Storage: " + response.body() + " (Status: " + response.statusCode() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase Storage upload interrupted", e);
        }
    }

    @Override
    public byte[] download(String objectPath) throws IOException {
        if (!properties.isConfigured()) {
            byte[] data = localFallbackStore.get(objectPath);
            if (data == null) {
                throw new IOException("Document not found in local fallback store: " + objectPath);
            }
            return data;
        }

        String sanitizedPath = sanitizePath(objectPath);
        String endpoint = String.format("%s/storage/v1/object/authenticated/%s/%s",
                cleanBaseUrl(properties.getUrl()),
                properties.getBucket(),
                sanitizedPath);

        log.info("Downloading document from Supabase Storage: bucket='{}', path='{}'", properties.getBucket(), sanitizedPath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                .header("apikey", properties.getServiceRoleKey())
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.error("Supabase Storage download failed with status {}: body length {}", response.statusCode(), response.body().length);
                throw new IOException("Failed to download document from Supabase Storage. Status: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase Storage download interrupted", e);
        }
    }

    @Override
    public boolean delete(String objectPath) {
        if (!properties.isConfigured()) {
            localFallbackStore.remove(objectPath);
            return true;
        }

        String sanitizedPath = sanitizePath(objectPath);
        String endpoint = String.format("%s/storage/v1/object/%s/%s",
                cleanBaseUrl(properties.getUrl()),
                properties.getBucket(),
                sanitizedPath);

        log.info("Deleting document from Supabase Storage: bucket='{}', path='{}'", properties.getBucket(), sanitizedPath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                .header("apikey", properties.getServiceRoleKey())
                .DELETE()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed to delete object '{}' from Supabase Storage: {}", sanitizedPath, e.getMessage());
            return false;
        }
    }

    @Override
    public String createSignedUrl(String objectPath, int expiresInSeconds) throws IOException {
        if (!properties.isConfigured()) {
            return "/api/documents/stream?path=" + URLEncoder.encode(objectPath, StandardCharsets.UTF_8);
        }

        String sanitizedPath = sanitizePath(objectPath);
        String endpoint = String.format("%s/storage/v1/object/sign/%s/%s",
                cleanBaseUrl(properties.getUrl()),
                properties.getBucket(),
                sanitizedPath);

        String jsonBody = String.format("{\"expiresIn\": %d}", expiresInSeconds > 0 ? expiresInSeconds : 60);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                .header("apikey", properties.getServiceRoleKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Pattern pattern = Pattern.compile("\"signedURL\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(response.body());
                if (matcher.find()) {
                    String relativeUrl = matcher.group(1);
                    return cleanBaseUrl(properties.getUrl()) + "/storage/v1" + relativeUrl;
                }
            }
            throw new IOException("Failed to generate signed URL. Status: " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase signed URL generation interrupted", e);
        }
    }

    @Override
    public boolean ensureBucketExists() {
        if (!properties.isConfigured()) {
            return true;
        }

        String bucket = properties.getBucket();
        String getEndpoint = cleanBaseUrl(properties.getUrl()) + "/storage/v1/bucket/" + bucket;

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(getEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                .header("apikey", properties.getServiceRoleKey())
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Verified private Supabase bucket exists: '{}'", bucket);
                return true;
            }

            // Supabase returns 400 or 404 if the bucket doesn't exist
            if (response.statusCode() == 404 || response.statusCode() == 400 || (response.body() != null && response.body().contains("not found"))) {
                log.info("Bucket '{}' not found in Supabase (status {}). Creating dedicated private bucket...", bucket, response.statusCode());
                String postEndpoint = cleanBaseUrl(properties.getUrl()) + "/storage/v1/bucket";
                String createJson = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"public\":false}", bucket, bucket);

                HttpRequest createRequest = HttpRequest.newBuilder()
                        .uri(URI.create(postEndpoint))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                        .header("apikey", properties.getServiceRoleKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(createJson))
                        .build();

                HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
                boolean created = createResponse.statusCode() >= 200 && createResponse.statusCode() < 300;
                log.info("Private Supabase bucket '{}' creation result: {} ({})", bucket, created ? "SUCCESS" : "FAILED (" + createResponse.statusCode() + ")", createResponse.body());
                return created;
            }
        } catch (Exception e) {
            log.warn("Could not verify/create Supabase bucket '{}': {}", bucket, e.getMessage());
        }
        return false;
    }

    private String cleanBaseUrl(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String sanitizePath(String path) {
        if (path == null) return "";
        // Disallow path traversal
        String cleaned = path.replace("..", "").replace("\\", "/");
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }
}
