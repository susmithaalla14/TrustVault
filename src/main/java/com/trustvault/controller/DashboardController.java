package com.trustvault.controller;

import com.trustvault.model.Document;
import com.trustvault.model.Role;
import com.trustvault.model.User;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuditLogService;
import com.trustvault.service.DocumentService;
import com.trustvault.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final UserService userService;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getRequiredUser(userDetails.getUsername());
        model.addAttribute("user", user);

        List<Document> ownedDocs = documentService.getOwnedDocuments(user);
        List<Document> sharedDocs = documentService.getSharedDocuments(user);
        long storageBytes = documentRepository.getTotalStorageUsedByOwner(user);

        model.addAttribute("ownedDocsCount", ownedDocs.size());
        model.addAttribute("sharedDocsCount", sharedDocs.size());
        model.addAttribute("storageUsedFormatted", formatBytes(storageBytes));
        model.addAttribute("recentDocuments", ownedDocs.stream().limit(5).toList());
        model.addAttribute("recentShared", sharedDocs.stream().limit(5).toList());

        // Admin-specific stats if role is ADMIN
        if (user.getRole() == Role.ADMIN) {
            model.addAttribute("totalUsersCount", userRepository.count());
            model.addAttribute("totalSystemDocsCount", documentRepository.count());
            model.addAttribute("totalSystemStorageFormatted", formatBytes(documentRepository.getTotalStorageUsedSystemWide()));
            model.addAttribute("recentAuditLogs", auditLogService.getRecentLogs().stream().limit(10).toList());
        }

        return "dashboard";
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
