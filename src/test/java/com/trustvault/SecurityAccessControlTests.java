package com.trustvault;

import com.trustvault.model.Role;
import com.trustvault.model.User;
import com.trustvault.repository.UserRepository;
import com.trustvault.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class SecurityAccessControlTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("1. Unauthenticated access to dashboard redirects to login page")
    void testUnauthenticatedAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("2. Unauthenticated access to documents list redirects to login page")
    void testUnauthenticatedDocumentsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/documents"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("3. Unauthenticated access to admin audit logs redirects to login page")
    void testUnauthenticatedAdminLogsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "regular_owner", roles = {"OWNER"})
    @DisplayName("4. Non-admin user (OWNER) accessing /admin is rejected with HTTP 403 Forbidden")
    void testNonAdminAccessToAdminPanelForbidden() throws Exception {
        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "restricted_viewer", roles = {"VIEWER"})
    @DisplayName("5. Restricted user (VIEWER) accessing /admin/users is rejected with HTTP 403 Forbidden")
    void testViewerAccessToAdminUsersForbidden() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("6. Authorized administrator can access /admin/audit-logs successfully")
    void testAdminAccessToAuditLogsAllowed() throws Exception {
        mockMvc.perform(get("/admin/audit-logs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("7. Registration strictly prevents self-assigned ADMIN privileges")
    void testRegistrationPreventsAdminEscalation() throws Exception {
        String testUser = "candidate_" + System.currentTimeMillis();
        String testEmail = testUser + "@domain.local";

        // Perform registration via post
        mockMvc.perform(post("/register")
                .param("username", testUser)
                .param("email", testEmail)
                .param("fullName", "Test Candidate")
                .param("password", "CandidateSecure2026!")
                .param("confirmPassword", "CandidateSecure2026!")
                .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Verify the created user has OWNER role, not ADMIN
        User registered = userRepository.findByUsername(testUser).orElseThrow();
        assertThat(registered.getRole()).isEqualTo(Role.OWNER);
        assertThat(registered.getRole()).isNotEqualTo(Role.ADMIN);
    }
}
