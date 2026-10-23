package com.trustvault;

import com.trustvault.model.*;
import com.trustvault.repository.AuditLogRepository;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.DocumentAccessPolicyService;
import com.trustvault.service.DocumentService;
import com.trustvault.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class DocumentSecurityPolicyTests {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentAccessPolicyService accessPolicyService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private StorageService storageService;

    private User aliceOwner;
    private User bobViewer;
    private User charlieThirdParty;
    private Document aliceDocument;

    @BeforeEach
    void setUp() throws IOException {
        long timestamp = System.currentTimeMillis();

        aliceOwner = userRepository.save(User.builder()
                .username("alice_" + timestamp)
                .email("alice_" + timestamp + "@test.local")
                .fullName("Alice Owner")
                .password("Password123!")
                .role(Role.OWNER)
                .enabled(true)
                .build());

        bobViewer = userRepository.save(User.builder()
                .username("bob_" + timestamp)
                .email("bob_" + timestamp + "@test.local")
                .fullName("Bob Viewer")
                .password("Password123!")
                .role(Role.VIEWER)
                .enabled(true)
                .build());

        charlieThirdParty = userRepository.save(User.builder()
                .username("charlie_" + timestamp)
                .email("charlie_" + timestamp + "@test.local")
                .fullName("Charlie ThirdParty")
                .password("Password123!")
                .role(Role.OWNER)
                .enabled(true)
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "confidential_contract.pdf",
                "application/pdf",
                "Confidential Financial Agreement 2026".getBytes()
        );

        aliceDocument = documentService.uploadDocument(
                file,
                "Confidential Contract",
                "Legal",
                "Alice Private Document",
                aliceOwner,
                new MockHttpServletRequest()
        );
    }

    @Test
    @DisplayName("1. Cross-User IDOR Attack Rejection: Bob cannot download Alice's document without permission grant")
    void testCrossUserUnauthorizedDownloadBlocked() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> documentService.downloadDocument(aliceDocument.getId(), bobViewer, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");

        // Verify audit log captured the unauthorized access attempt
        boolean deniedLogged = auditLogRepository.findAll().stream()
                .anyMatch(log -> "ACCESS_DENIED".equals(log.getAction()) && bobViewer.getUsername().equals(log.getActorUsername()));
        assertThat(deniedLogged).isTrue();
    }

    @Test
    @DisplayName("2. Viewer Deletion Rejection: Bob (VIEWER) cannot delete Alice's document")
    void testViewerCannotDeleteDocument() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> documentService.deleteDocument(aliceDocument.getId(), bobViewer, request))
                .isInstanceOf(AccessDeniedException.class);

        // Document still exists in database
        assertThat(documentRepository.existsById(aliceDocument.getId())).isTrue();
    }

    @Test
    @DisplayName("3. READ vs EDIT Permission Enforcement: READ recipient can download but cannot edit metadata")
    void testReadVsEditPermissionEnforcement() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        // Alice grants READ permission to Charlie
        documentService.grantPermission(aliceDocument.getId(), charlieThirdParty.getUsername(), PermissionType.READ, aliceOwner, request);

        // Charlie can download the document
        byte[] downloaded = documentService.downloadDocument(aliceDocument.getId(), charlieThirdParty, request);
        assertThat(downloaded).isNotEmpty();

        // Charlie tries to edit document metadata -> Rejected
        assertThatThrownBy(() -> documentService.updateMetadata(aliceDocument.getId(), "Hacked Title", "General", "Desc", charlieThirdParty, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("4. Immediate Revocation Enforcement: Revoking permission blocks subsequent access immediately")
    void testAccessDeniedImmediatelyAfterRevocation() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        // Alice grants READ permission to Charlie
        documentService.grantPermission(aliceDocument.getId(), charlieThirdParty.getUsername(), PermissionType.READ, aliceOwner, request);

        // Charlie downloads successfully
        byte[] firstDownload = documentService.downloadDocument(aliceDocument.getId(), charlieThirdParty, request);
        assertThat(firstDownload).isNotEmpty();

        // Alice revokes permission
        documentService.revokePermission(aliceDocument.getId(), charlieThirdParty.getId(), aliceOwner, request);

        // Subsequent download attempt is immediately rejected
        assertThatThrownBy(() -> documentService.downloadDocument(aliceDocument.getId(), charlieThirdParty, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("5. Unauthorized Sharing Rejection: Non-owner cannot grant permissions to other users")
    void testNonOwnerCannotGrantPermissions() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> documentService.grantPermission(aliceDocument.getId(), bobViewer.getUsername(), PermissionType.READ, charlieThirdParty, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("6. VIEWER Role Upload Prevention: Users with VIEWER role cannot upload documents")
    void testViewerRoleCannotUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "Content".getBytes()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> documentService.uploadDocument(file, "Doc", "General", "", bobViewer, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("VIEWER accounts do not have upload privileges");
    }

    @Test
    @DisplayName("7. Malicious File Upload Rejection: Executable scripts (.exe, .sh) are rejected")
    void testMaliciousExtensionUploadRejected() {
        MockMultipartFile executableFile = new MockMultipartFile(
                "file", "exploit.exe", "application/octet-stream", "MZ...malicious code".getBytes()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> documentService.uploadDocument(executableFile, "Exploit", "General", "", aliceOwner, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File type '.exe' is not supported");
    }

    @Test
    @DisplayName("8. Successful Deletion Consistency: Owner deletes document and object is purged")
    void testOwnerCanDeleteDocumentSuccessfully() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Long docId = aliceDocument.getId();

        documentService.deleteDocument(docId, aliceOwner, request);

        assertThat(documentRepository.existsById(docId)).isFalse();
    }
}
