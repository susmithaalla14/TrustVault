package com.trustvault.controller;

import com.trustvault.model.AuditLog;
import com.trustvault.model.Role;
import com.trustvault.model.User;
import com.trustvault.repository.DocumentRepository;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.AuditLogService;
import com.trustvault.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    public String adminOverview(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User admin = userService.getRequiredUser(userDetails.getUsername());
        long totalUsers = userRepository.count();
        long totalDocs = documentRepository.count();
        long totalStorage = documentRepository.getTotalStorageUsedSystemWide();
        List<AuditLog> recentLogs = auditLogService.getRecentLogs();

        model.addAttribute("user", admin);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalDocs", totalDocs);
        model.addAttribute("totalStorageFormatted", formatBytes(totalStorage));
        model.addAttribute("recentLogs", recentLogs.stream().limit(15).toList());
        model.addAttribute("activeNav", "admin-overview");
        return "admin/overview";
    }

    @GetMapping("/users")
    public String listUsers(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User admin = userService.getRequiredUser(userDetails.getUsername());
        List<User> users = userService.getAllUsers(admin);

        model.addAttribute("user", admin);
        model.addAttribute("users", users);
        model.addAttribute("roles", Role.values());
        model.addAttribute("activeNav", "admin-users");
        return "admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String updateUserRole(@PathVariable("id") Long id,
                                 @RequestParam("role") Role role,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.getRequiredUser(userDetails.getUsername());
            userService.updateUserRole(id, role, admin, request);
            redirectAttributes.addFlashAttribute("successMessage", "User role successfully updated to " + role);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/status")
    public String toggleUserStatus(@PathVariable("id") Long id,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.getRequiredUser(userDetails.getUsername());
            userService.toggleUserStatus(id, admin, request);
            redirectAttributes.addFlashAttribute("successMessage", "User account status updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/audit-logs")
    public String viewAuditLogs(@AuthenticationPrincipal UserDetails userDetails,
                                @RequestParam(value = "filter", required = false) String filter,
                                Model model) {
        User admin = userService.getRequiredUser(userDetails.getUsername());
        List<AuditLog> logs = auditLogService.getRecentLogs();

        if ("denied".equalsIgnoreCase(filter)) {
            logs = logs.stream().filter(l -> "DENIED".equalsIgnoreCase(l.getStatus())).toList();
        } else if ("auth".equalsIgnoreCase(filter)) {
            logs = logs.stream().filter(l -> l.getAction().startsWith("LOGIN") || l.getAction().startsWith("LOGOUT") || l.getAction().startsWith("USER")).toList();
        } else if ("docs".equalsIgnoreCase(filter)) {
            logs = logs.stream().filter(l -> l.getAction().startsWith("DOCUMENT") || l.getAction().startsWith("PERMISSION")).toList();
        }

        model.addAttribute("user", admin);
        model.addAttribute("logs", logs);
        model.addAttribute("currentFilter", filter != null ? filter : "all");
        model.addAttribute("activeNav", "admin-audit");
        return "admin/audit-logs";
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
