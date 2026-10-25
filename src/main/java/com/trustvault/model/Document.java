package com.trustvault.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_doc_owner", columnList = "owner_id"),
        @Index(name = "idx_doc_category", columnList = "category"),
        @Index(name = "idx_doc_created", columnList = "createdAt"),
        @Index(name = "idx_doc_storage_key", columnList = "storageObjectKey", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Document title is required")
    @Column(nullable = false, length = 150)
    private String title;

    @NotBlank(message = "Original filename is required")
    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @NotNull
    @Column(nullable = false)
    private Long sizeBytes;

    /**
     * Cryptographically unique, non-guessable object path in private Supabase Storage.
     * Example: "owners/1/8f2c3b8a-9a1d-4d44-ba2a-0a2b3c4d5e6f.pdf"
     */
    @Column(nullable = false, unique = true, length = 300)
    private String storageObjectKey;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "General";

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentPermission> permissions = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public String getFormattedSize() {
        if (sizeBytes == null || sizeBytes <= 0) return "0 B";
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        return String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
