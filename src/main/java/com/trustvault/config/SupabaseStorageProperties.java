package com.trustvault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "supabase")
@Getter
@Setter
public class SupabaseStorageProperties {

    /**
     * Supabase project base URL (e.g. https://tkksvfmtaoxlexcmngmk.supabase.co)
     */
    private String url = "https://tkksvfmtaoxlexcmngmk.supabase.co";

    /**
     * Supabase service role key for privileged, server-side Storage management.
     * Stored in environment variable SUPABASE_SERVICE_ROLE_KEY.
     * NEVER exposed in client HTML, JavaScript, logs, or Git commits.
     */
    private String serviceRoleKey = "";

    /**
     * Dedicated private bucket name for sensitive documents.
     */
    private String bucket = "TrustVault-documents";

    /**
     * Storage mode: "supabase" (live REST API) or "local" (in-memory/filesystem fallback for offline dev/tests).
     */
    private String mode = "supabase";

    public boolean isConfigured() {
        return url != null && !url.isBlank() && serviceRoleKey != null && !serviceRoleKey.isBlank();
    }
}
