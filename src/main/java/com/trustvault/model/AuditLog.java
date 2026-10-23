package com.trustvault.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Append-only security audit log entity.
 * Records all authentication attempts, permission modifications, document access,
 * and security-sensitive authorization denials.
 * Modification or deletion of audit logs is forbidden by system design.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor", columnList = "actorUsername"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_status", columnList = "status"),
        @Index(name = "idx_audit_created", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String actorUsername;

    private Long actorId;

    @Column(length = 20)
    private String actorRole;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 50)
    private String targetType;

    private Long targetId;

    @Column(length = 100)
    private String targetName;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, length = 20)
    private String status; // SUCCESS, DENIED, FAILURE

    @Column(length = 1000)
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
