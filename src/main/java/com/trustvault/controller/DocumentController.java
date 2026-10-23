package com.trustvault.controller;

import com.trustvault.model.Document;
import com.trustvault.model.PermissionType;
import com.trustvault.model.User;
import com.trustvault.service.DocumentAccessPolicyService;
import com.trustvault.service.DocumentService;
import com.trustvault.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final UserService userService;
    private final DocumentAccessPolicyService accessPolicyService;

    @GetMapping
    public String listMyDocuments(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getRequiredUser(userDetails.getUsername());
        List<Document> documents = documentService.getOwnedDocuments(user);
        model.addAttribute("user", user);
        model.addAttribute("documents", documents);
        model.addAttribute("pageTitle", "My Documents");
        model.addAttribute("activeNav", "my-documents");
        return "documents/list";
    }

    @GetMapping("/shared")
    public String listSharedDocuments(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getRequiredUser(userDetails.getUsername());
        List<Document> sharedDocs = documentService.getSharedDocuments(user);
        model.addAttribute("user", user);
        model.addAttribute("documents", sharedDocs);
        model.addAttribute("pageTitle", "Documents Shared With Me");
        model.addAttribute("activeNav", "shared-documents");
        return "documents/shared";
    }

    @GetMapping("/upload")
    public String uploadPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.getRequiredUser(userDetails.getUsername());
        if (user.getRole() == com.trustvault.model.Role.VIEWER) {
            throw new AccessDeniedException("VIEWER role accounts are restricted from uploading files.");
        }
        model.addAttribute("user", user);
        model.addAttribute("activeNav", "upload");
        return "documents/upload";
    }

    @PostMapping("/upload")
    public String handleUpload(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam(value = "title", required = false) String title,
                               @RequestParam(value = "category", required = false) String category,
                               @RequestParam(value = "description", required = false) String description,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getRequiredUser(userDetails.getUsername());
            Document doc = documentService.uploadDocument(file, title, category, description, user, request);
            redirectAttributes.addFlashAttribute("successMessage", "Document '" + doc.getTitle() + "' successfully uploaded to private vault.");
            return "redirect:/documents/" + doc.getId();
        } catch (IllegalArgumentException | AccessDeniedException | IOException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/documents/upload";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Upload failed: " + ex.getMessage());
            return "redirect:/documents/upload";
        }
    }

    @GetMapping("/{id}")
    public String viewDocument(@PathVariable("id") Long id,
                               @AuthenticationPrincipal UserDetails userDetails,
                               HttpServletRequest request,
                               Model model) {
        User user = userService.getRequiredUser(userDetails.getUsername());
        Document document = documentService.getDocument(id, user, request);

        boolean canEdit = accessPolicyService.canEdit(document, user);
        boolean canDelete = accessPolicyService.canDelete(document, user);
        boolean canShare = accessPolicyService.canShare(document, user);

        model.addAttribute("user", user);
        model.addAttribute("document", document);
        model.addAttribute("canEdit", canEdit);
        model.addAttribute("canDelete", canDelete);
        model.addAttribute("canShare", canShare);
        return "documents/view";
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable("id") Long id,
                                                     @AuthenticationPrincipal UserDetails userDetails,
                                                     HttpServletRequest request) throws IOException {
        User user = userService.getRequiredUser(userDetails.getUsername());
        Document document = documentService.getDocument(id, user, request);
        byte[] fileBytes = documentService.downloadDocument(id, user, request);

        ByteArrayResource resource = new ByteArrayResource(fileBytes);
        String encodedFilename = URLEncoder.encode(document.getOriginalFilename(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(fileBytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(resource);
    }

    @GetMapping("/{id}/signed-url")
    public String getSignedUrl(@PathVariable("id") Long id,
                              @RequestParam(value = "expires", defaultValue = "60") int expires,
                              @AuthenticationPrincipal UserDetails userDetails,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getRequiredUser(userDetails.getUsername());
            String signedUrl = documentService.getSignedDownloadUrl(id, user, expires, request);
            redirectAttributes.addFlashAttribute("signedUrl", signedUrl);
            redirectAttributes.addFlashAttribute("successMessage", "Signed URL generated successfully (valid for " + expires + " seconds).");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not generate signed URL: " + ex.getMessage());
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/share")
    public String shareDocument(@PathVariable("id") Long id,
                                @RequestParam("username") String targetUsername,
                                @RequestParam("permissionType") PermissionType permissionType,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getRequiredUser(userDetails.getUsername());
            documentService.grantPermission(id, targetUsername, permissionType, user, request);
            redirectAttributes.addFlashAttribute("successMessage", "Access granted to user '" + targetUsername + "' with " + permissionType + " permission.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/revoke")
    public String revokePermission(@PathVariable("id") Long id,
                                   @RequestParam("targetUserId") Long targetUserId,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getRequiredUser(userDetails.getUsername());
            documentService.revokePermission(id, targetUserId, user, request);
            redirectAttributes.addFlashAttribute("successMessage", "Access grant revoked immediately.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/edit")
    public String updateDocument(@PathVariable("id") Long id,
                                 @RequestParam("title") String title,
                                 @RequestParam("category") String category,
                                 @RequestParam("description") String description,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getRequiredUser(userDetails.getUsername());
            documentService.updateMetadata(id, title, category, description, user, request);
            redirectAttributes.addFlashAttribute("successMessage", "Document metadata updated successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteDocument(@PathVariable("id") Long id,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getRequiredUser(userDetails.getUsername());
            documentService.deleteDocument(id, user, request);
            redirectAttributes.addFlashAttribute("successMessage", "Document deleted successfully.");
            return "redirect:/documents";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/documents/" + id;
        }
    }

    @GetMapping("/search")
    public String searchDocuments(@RequestParam(value = "q", required = false) String query,
                                  @RequestParam(value = "category", required = false) String category,
                                  @AuthenticationPrincipal UserDetails userDetails,
                                  Model model) {
        User user = userService.getRequiredUser(userDetails.getUsername());
        List<Document> results = documentService.searchDocuments(user, query, category);
        model.addAttribute("user", user);
        model.addAttribute("documents", results);
        model.addAttribute("query", query != null ? query : "");
        model.addAttribute("category", category != null ? category : "");
        model.addAttribute("activeNav", "search");
        return "documents/search";
    }
}
