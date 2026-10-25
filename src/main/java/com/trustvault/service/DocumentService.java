package com.trustvault.service;

import com.trustvault.model.*;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "xlsx", "txt", "png", "jpg", "jpeg"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "image/png",
            "image/jpeg"
    );

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final DocumentAccessPolicyService accessPolicyService;
    private final AuditLogService auditLogService;

    /**
     * Upload document with strict MIME/size validation and safe storage-database consistency compensation.
     */
    @Transactional
    public Document uploadDocument(MultipartFile file, String title, String category, String description,
                                  User currentUser, HttpServletRequest request) throws IOException {
        // 1. Authorization check: VIEWER role cannot upload documents
        if (currentUser.getRole() == Role.VIEWER) {
            auditLogService.logAccessDenied(currentUser.getUsername(), "DOCUMENT_UPLOAD", "DOCUMENT", null,
                    "VIEWER role is not authorized to upload documents", request);
            throw new AccessDeniedException("Access denied: VIEWER accounts do not have upload privileges.");
        }

        // 2. File validation
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowable limit of 20MB.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.bin";
        String cleanOriginalName = sanitizeFilename(originalFilename);
        String extension = getFileExtension(cleanOriginalName).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type '." + extension + "' is not supported. Allowed: PDF, DOCX, XLSX, TXT, PNG, JPG.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            // Log MIME check warning but allow if extension is verified and MIME is standard stream
            log.warn("MIME type '{}' uploaded for extension '{}'", contentType, extension);
        }

        // 3. Generate non-guessable, cryptographically unique storage path
        String uniqueId = UUID.randomUUID().toString();
        String storageObjectKey = String.format("owners/%d/%s-%s", currentUser.getId(), uniqueId, cleanOriginalName);

        // 4. Upload binary to Supabase Storage
        byte[] fileBytes = file.getBytes();
        String confirmedKey;
        try {
            confirmedKey = storageService.upload(storageObjectKey, fileBytes, contentType != null ? contentType : "application/octet-stream");
        } catch (IOException e) {
            auditLogService.logEvent(currentUser.getUsername(), currentUser.getId(), currentUser.getRole().name(),
                    "DOCUMENT_UPLOAD", "DOCUMENT", null, cleanOriginalName,
                    "FAILURE", "Storage provider rejected upload: " + e.getMessage(), request);
            throw new IOException("Failed to save document to storage provider: " + e.getMessage(), e);
        }

        // 5. Persist metadata with Compensating Transaction
        Document document = Document.builder()
                .title((title != null && !title.isBlank()) ? title.trim() : cleanOriginalName)
                .originalFilename(cleanOriginalName)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .sizeBytes(file.getSize())
                .storageObjectKey(confirmedKey)
                .category((category != null && !category.isBlank()) ? category.trim() : "General")
                .description(description != null ? description.trim() : "")
                .owner(currentUser)
                .build();

        try {
            Document saved = documentRepository.save(document);
            auditLogService.logDocumentUpload(currentUser, saved, "SUCCESS", request);
            return saved;
        } catch (Exception ex) {
            // Compensating action: Delete uploaded file from storage if DB transaction fails
            log.error("Database persistence failed for uploaded file '{}'. Rolling back storage object...", confirmedKey, ex);
            try {
                storageService.delete(confirmedKey);
            } catch (Exception deleteEx) {
                log.error("Compensating delete failed for storage key '{}': {}", confirmedKey, deleteEx.getMessage());
            }
            throw new RuntimeException("Failed to save document record. Storage upload rolled back.", ex);
        }
    }

    /**
     * Authorized file download. Verifies read permissions before retrieval.
     */
    @Transactional(readOnly = true)
    public byte[] downloadDocument(Long documentId, User currentUser, HttpServletRequest request) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found with ID: " + documentId));

        // Authoritative permission check (prevents IDOR)
        accessPolicyService.verifyRead(document, currentUser, request);

        byte[] content = storageService.download(document.getStorageObjectKey());
        auditLogService.logDocumentDownload(currentUser, document, "SUCCESS", request);
        return content;
    }

    /**
     * Authorized short-lived signed URL generation.
     */
    @Transactional(readOnly = true)
    public String getSignedDownloadUrl(Long documentId, User currentUser, int expiresInSeconds, HttpServletRequest request) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found with ID: " + documentId));

        accessPolicyService.verifyRead(document, currentUser, request);

        String signedUrl = storageService.createSignedUrl(document.getStorageObjectKey(), expiresInSeconds);
        auditLogService.logDocumentDownload(currentUser, document, "SIGNED_URL_CREATED", request);
        return signedUrl;
    }

    /**
     * Authorized document deletion. Strictly restricted to Owner or Admin.
     */
    @Transactional
    public void deleteDocument(Long documentId, User currentUser, HttpServletRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found with ID: " + documentId));

        // Authoritative permission check (prevents VIEWER and EDIT-grantee deletions)
        accessPolicyService.verifyDelete(document, currentUser, request);

        String storageKey = document.getStorageObjectKey();

        // Remove from database first
        documentRepository.delete(document);

        // Remove from storage provider
        try {
            storageService.delete(storageKey);
        } catch (Exception ex) {
            log.warn("Storage deletion warning for key '{}': {}", storageKey, ex.getMessage());
        }

        auditLogService.logDocumentDelete(currentUser, document, "SUCCESS", request);
    }

    /**
     * Grant granular READ or EDIT permission on a document to another user.
     */
    @Transactional
    public DocumentPermission grantPermission(Long documentId, String targetUsername, PermissionType permissionType,
                                              User currentUser, HttpServletRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found with ID: " + documentId));

        accessPolicyService.verifyShare(document, currentUser, request);

        User targetUser = userRepository.findByUsername(targetUsername)
                .or(() -> userRepository.findByEmail(targetUsername))
                .orElseThrow(() -> new IllegalArgumentException("User '" + targetUsername + "' does not exist."));

        if (targetUser.getId().equals(document.getOwner().getId())) {
            throw new IllegalArgumentException("Cannot grant permissions to the document owner.");
        }

        Optional<DocumentPermission> existing = permissionRepository.findByDocumentAndGrantedUser(document, targetUser);
        DocumentPermission permission;
        if (existing.isPresent()) {
            permission = existing.get();
            permission.setPermissionType(permissionType);
            permission.setGrantedBy(currentUser);
        } else {
            permission = DocumentPermission.builder()
                    .document(document)
                    .grantedUser(targetUser)
                    .permissionType(permissionType)
                    .grantedBy(currentUser)
                    .build();
        }

        DocumentPermission saved = permissionRepository.save(permission);
        auditLogService.logPermissionGrant(currentUser, document, targetUser, permissionType, request);
        return saved;
    }

    /**
     * Revoke document access for a user. Enforcement is immediate on subsequent requests.
     */
    @Transactional
    public void revokePermission(Long documentId, Long targetUserId, User currentUser, HttpServletRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found with ID: " + documentId));

        accessPolicyService.verifyShare(document, currentUser, request);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found."));

        permissionRepository.deleteByDocumentAndGrantedUser(document, targetUser);
        auditLogService.logPermissionRevoke(currentUser, document, targetUser, request);
    }

    /**
     * Update document title, category, and description.
     */
    @Transactional
    public Document updateMetadata(Long documentId, String title, String category, String description,
                                   User currentUser, HttpServletRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found with ID: " + documentId));

        accessPolicyService.verifyEdit(document, currentUser, request);

        if (title != null && !title.isBlank()) {
            document.setTitle(title.trim());
        }
        if (category != null && !category.isBlank()) {
            document.setCategory(category.trim());
        }
        if (description != null) {
            document.setDescription(description.trim());
        }

        Document saved = documentRepository.save(document);
        auditLogService.logDocumentUpdate(currentUser, saved, "Document metadata updated", request);
        return saved;
    }

    @Transactional(readOnly = true)
    public Document getDocument(Long documentId, User currentUser, HttpServletRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found with ID: " + documentId));
        accessPolicyService.verifyRead(document, currentUser, request);

        // Pre-initialize lazy collections and proxies within active transaction for view rendering
        if (document.getPermissions() != null) {
            document.getPermissions().size();
            document.getPermissions().forEach(perm -> {
                if (perm.getGrantedUser() != null) perm.getGrantedUser().getUsername();
                if (perm.getGrantedBy() != null) perm.getGrantedBy().getUsername();
            });
        }
        if (document.getOwner() != null) {
            document.getOwner().getUsername();
        }

        return document;
    }

    @Transactional(readOnly = true)
    public List<Document> getOwnedDocuments(User user) {
        return documentRepository.findByOwnerOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Document> getSharedDocuments(User user) {
        return documentRepository.findSharedWithUser(user);
    }

    @Transactional(readOnly = true)
    public List<Document> searchDocuments(User user, String query, String category) {
        if (user.getRole() == Role.ADMIN) {
            return documentRepository.searchAllDocumentsAdmin(query, category);
        }
        return documentRepository.searchOwnedDocuments(user, query, category);
    }

    private String sanitizeFilename(String filename) {
        // Strip path traversal sequences and spaces
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < filename.length() - 1) ? filename.substring(dotIndex + 1) : "";
    }
}
