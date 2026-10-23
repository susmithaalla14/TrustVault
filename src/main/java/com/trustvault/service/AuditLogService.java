package com.trustvault.service;

import com.trustvault.model.AuditLog;
import com.trustvault.model.Document;
import com.trustvault.model.PermissionType;
import com.trustvault.model.User;
import com.trustvault.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records a tamper-resistant security audit log event.
     * Uses REQUIRES_NEW propagation to ensure audit records are committed
     * even if the calling business transaction fails or rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logEvent(String actorUsername, Long actorId, String actorRole,
                             String action, String targetType, Long targetId,
                             String targetName, String status, String details,
                             HttpServletRequest request) {
        String ipAddress = extractClientIp(request);

        AuditLog entry = AuditLog.builder()
                .actorUsername(actorUsername != null ? actorUsername : "ANONYMOUS")
                .actorId(actorId)
                .actorRole(actorRole)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetName(targetName)
                .status(status != null ? status : "SUCCESS")
                .details(details)
                .ipAddress(ipAddress)
                .timestamp(LocalDateTime.now())
                .build();

        log.info("SECURITY AUDIT: actor='{}' role='{}' action='{}' target='{}:{}' status='{}' ip='{}'",
                entry.getActorUsername(), entry.getActorRole(), entry.getAction(),
                entry.getTargetType(), entry.getTargetId(), entry.getStatus(), entry.getIpAddress());

        return auditLogRepository.save(entry);
    }

    public void logLoginSuccess(User user, HttpServletRequest request) {
        logEvent(user.getUsername(), user.getId(), user.getRole().name(),
                "LOGIN_SUCCESS", "AUTH", user.getId(), user.getUsername(),
                "SUCCESS", "User authenticated successfully", request);
    }

    public void logLoginFailure(String username, String reason, HttpServletRequest request) {
        logEvent(username, null, null,
                "LOGIN_FAILURE", "AUTH", null, username,
                "FAILURE", "Authentication failed: " + reason, request);
    }

    public void logLogout(User user, HttpServletRequest request) {
        if (user != null) {
            logEvent(user.getUsername(), user.getId(), user.getRole().name(),
                    "LOGOUT", "AUTH", user.getId(), user.getUsername(),
                    "SUCCESS", "User logged out", request);
        }
    }

    public void logUserRegistration(User user, HttpServletRequest request) {
        logEvent(user.getUsername(), user.getId(), user.getRole().name(),
                "USER_REGISTER", "USER", user.getId(), user.getUsername(),
                "SUCCESS", "New user account registered with role " + user.getRole(), request);
    }

    public void logDocumentUpload(User user, Document document, String status, HttpServletRequest request) {
        logEvent(user.getUsername(), user.getId(), user.getRole().name(),
                "DOCUMENT_UPLOAD", "DOCUMENT", document != null ? document.getId() : null,
                document != null ? document.getOriginalFilename() : "UNKNOWN",
                status, "File uploaded: " + (document != null ? document.getTitle() : "Failed"), request);
    }

    public void logDocumentDownload(User user, Document document, String status, HttpServletRequest request) {
        logEvent(user.getUsername(), user.getId(), user.getRole().name(),
                "DOCUMENT_DOWNLOAD", "DOCUMENT", document.getId(), document.getOriginalFilename(),
                status, "File downloaded: " + document.getTitle(), request);
    }

    public void logDocumentUpdate(User user, Document document, String details, HttpServletRequest request) {
        logEvent(user.getUsername(), user.getId(), user.getRole().name(),
                "DOCUMENT_UPDATE", "DOCUMENT", document.getId(), document.getOriginalFilename(),
                "SUCCESS", details, request);
    }

    public void logDocumentDelete(User user, Document document, String status, HttpServletRequest request) {
        logEvent(user.getUsername(), user.getId(), user.getRole().name(),
                "DOCUMENT_DELETE", "DOCUMENT", document.getId(), document.getOriginalFilename(),
                status, "Document deleted: " + document.getTitle(), request);
    }

    public void logPermissionGrant(User actor, Document document, User targetUser, PermissionType permissionType, HttpServletRequest request) {
        logEvent(actor.getUsername(), actor.getId(), actor.getRole().name(),
                "PERMISSION_GRANT", "PERMISSION", document.getId(), document.getOriginalFilename(),
                "SUCCESS", String.format("Granted %s permission to user '%s'", permissionType, targetUser.getUsername()), request);
    }

    public void logPermissionRevoke(User actor, Document document, User targetUser, HttpServletRequest request) {
        logEvent(actor.getUsername(), actor.getId(), actor.getRole().name(),
                "PERMISSION_REVOKE", "PERMISSION", document.getId(), document.getOriginalFilename(),
                "SUCCESS", String.format("Revoked access for user '%s'", targetUser.getUsername()), request);
    }

    public void logAccessDenied(String actorUsername, String attemptedAction, String targetType, Long targetId, String reason, HttpServletRequest request) {
        logEvent(actorUsername, null, null,
                "ACCESS_DENIED", targetType, targetId, null,
                "DENIED", String.format("Blocked %s: %s", attemptedAction, reason), request);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "0.0.0.0";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "0.0.0.0";
    }
}
