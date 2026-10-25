package com.trustvault.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_permissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_doc_user_perm", columnNames = {"document_id", "granted_user_id"})
}, indexes = {
        @Index(name = "idx_perm_granted_user", columnList = "granted_user_id"),
        @Index(name = "idx_perm_document", columnList = "document_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "granted_user_id", nullable = false)
    private User grantedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionType permissionType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "granted_by_id", nullable = false)
    private User grantedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
