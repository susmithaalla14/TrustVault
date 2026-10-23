package com.trustvault.service;

import java.io.IOException;

/**
 * Storage abstraction for persisting private document binaries.
 * In production, backed by Supabase Storage private buckets.
 * In test/offline environments, backed by a local/in-memory store.
 */
public interface StorageService {

    /**
     * Upload binary data to private storage under a unique, non-guessable object path.
     * @param objectPath Relative unique path inside the bucket (e.g. "owners/1/uuid-filename.pdf")
     * @param content Binary content of the document
     * @param contentType MIME type of the document
     * @return Confirmed storage object key
     */
    String upload(String objectPath, byte[] content, String contentType) throws IOException;

    /**
     * Download binary data from private storage.
     * @param objectPath Relative unique path inside the bucket
     * @return Binary file content
     */
    byte[] download(String objectPath) throws IOException;

    /**
     * Delete binary file from private storage.
     * @param objectPath Relative unique path inside the bucket
     * @return true if deleted, false otherwise
     */
    boolean delete(String objectPath);

    /**
     * Create a short-lived signed URL for authorized temporary access.
     * @param objectPath Relative unique path inside the bucket
     * @param expiresInSeconds Duration in seconds before URL expires
     * @return Signed download URL
     */
    String createSignedUrl(String objectPath, int expiresInSeconds) throws IOException;

    /**
     * Verify or provision the private bucket in Supabase Storage.
     */
    boolean ensureBucketExists();
}
