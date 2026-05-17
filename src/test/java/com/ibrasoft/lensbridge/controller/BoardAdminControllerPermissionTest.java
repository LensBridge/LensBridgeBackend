// package com.ibrasoft.lensbridge.controller;

// import com.ibrasoft.lensbridge.handler.SignboardHandler;
// import com.ibrasoft.lensbridge.service.AdminAuditService;
// import com.ibrasoft.lensbridge.service.BoardService;
// import com.ibrasoft.lensbridge.service.PosterService;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.CsvSource;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.context.annotation.Import;
// import org.springframework.http.MediaType;
// import org.springframework.security.test.context.support.WithAnonymousUser;
// import org.springframework.security.test.context.support.WithMockUser;
// import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.ResultActions;
// import org.springframework.test.web.servlet.request.RequestPostProcessor;

// import java.util.UUID;

// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /**
//  * Security-only integration tests for {@link BoardAdminController}. Every endpoint is class-level
//  * {@code @PreAuthorize("hasRole('ROOT')")}; this verifies anonymous -> 401, USER/ADMIN -> 403,
//  * and ROOT -> not 401/403. CSRF is disabled, so no csrf() post-processor is needed.
//  */
// @WebMvcTest(controllers = BoardAdminController.class)
// @Import(MethodSecurityTestConfig.class)
// class BoardAdminControllerPermissionTest {

//     private static final String BASE = "/api/admin/board";

//     @Autowired
//     private MockMvc mockMvc;

//     @MockitoBean
//     private PosterService posterService;
//     @MockitoBean
//     private BoardService boardService;
//     @MockitoBean
//     private AdminAuditService auditService;
//     @MockitoBean
//     private SignboardHandler signboardHandler;

//     private static String id() {
//         return UUID.randomUUID().toString();
//     }

//     // ==================== Anonymous -> 401 ====================

//     @Test
//     @WithAnonymousUser
//     void anonymousIsUnauthorizedForAllEndpoints() throws Exception {
//         mockMvc.perform(get(BASE + "/configs")).andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/configs/" + id())).andExpect(status().isUnauthorized());
//         mockMvc.perform(put(BASE + "/configs/" + id()).contentType(MediaType.APPLICATION_JSON).content("{}"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(patch(BASE + "/configs/" + id()).contentType(MediaType.APPLICATION_JSON).content("{}"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/weekly-content")).andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/weekly-content/year/2026")).andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/weekly-content/2026/1")).andExpect(status().isUnauthorized());
//         mockMvc.perform(put(BASE + "/weekly-content/2026/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(delete(BASE + "/weekly-content/2026/1")).andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/posters")).andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/posters/by-audience").param("audience", "BOTH"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/posters/" + id())).andExpect(status().isUnauthorized());
//         mockMvc.perform(post(BASE + "/posters").contentType(MediaType.MULTIPART_FORM_DATA))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(patch(BASE + "/posters/" + id()).contentType(MediaType.APPLICATION_JSON).content("{}"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(put(BASE + "/posters/" + id() + "/image").contentType(MediaType.MULTIPART_FORM_DATA))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(delete(BASE + "/posters/" + id())).andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/events")).andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/events/by-audience").param("audience", "BOTH"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(get(BASE + "/events/" + id())).andExpect(status().isUnauthorized());
//         mockMvc.perform(post(BASE + "/events").contentType(MediaType.APPLICATION_JSON).content("{}"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(patch(BASE + "/events/" + id()).contentType(MediaType.APPLICATION_JSON).content("{}"))
//                 .andExpect(status().isUnauthorized());
//         mockMvc.perform(delete(BASE + "/events/" + id())).andExpect(status().isUnauthorized());
//         mockMvc.perform(post(BASE + "/refresh")).andExpect(status().isUnauthorized());
//     }

//     // ==================== USER / ADMIN -> 403 ====================

//     @ParameterizedTest
//     @CsvSource({"USER", "ADMIN"})
//     @WithMockUser
//     void nonRootRolesAreForbidden(String role) throws Exception {
//         runAllEndpoints(role, true);
//     }

//     // ==================== ROOT -> not 401/403 ====================

//     @Test
//     @WithMockUser(roles = "ROOT")
//     void rootIsAuthorized() throws Exception {
//         runAllEndpoints("ROOT", false);
//     }

//     private void runAllEndpoints(String role, boolean expectForbidden) throws Exception {
//         RequestPostProcessor user = SecurityMockMvcRequestPostProcessors.user("u").roles(role);

//         check(expectForbidden, mockMvc.perform(get(BASE + "/configs").with(user)));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/configs/" + id()).with(user)));
//         check(expectForbidden, mockMvc.perform(put(BASE + "/configs/" + id()).with(user)
//                 .contentType(MediaType.APPLICATION_JSON).content("{}")));
//         check(expectForbidden, mockMvc.perform(patch(BASE + "/configs/" + id()).with(user)
//                 .contentType(MediaType.APPLICATION_JSON).content("{}")));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/weekly-content").with(user)));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/weekly-content/year/2026").with(user)));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/weekly-content/2026/1").with(user)));
//         check(expectForbidden, mockMvc.perform(put(BASE + "/weekly-content/2026/1").with(user)
//                 .contentType(MediaType.APPLICATION_JSON).content("{}")));
//         check(expectForbidden, mockMvc.perform(delete(BASE + "/weekly-content/2026/1").with(user)));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/posters").with(user)));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/posters/by-audience").with(user)
//                 .param("audience", "BOTH")));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/posters/" + id()).with(user)));
//         check(expectForbidden, mockMvc.perform(post(BASE + "/posters").with(user)
//                 .contentType(MediaType.MULTIPART_FORM_DATA)));
//         check(expectForbidden, mockMvc.perform(patch(BASE + "/posters/" + id()).with(user)
//                 .contentType(MediaType.APPLICATION_JSON).content("{}")));
//         check(expectForbidden, mockMvc.perform(put(BASE + "/posters/" + id() + "/image").with(user)
//                 .contentType(MediaType.MULTIPART_FORM_DATA)));
//         check(expectForbidden, mockMvc.perform(delete(BASE + "/posters/" + id()).with(user)));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/events").with(user)));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/events/by-audience").with(user)
//                 .param("audience", "BOTH")));
//         check(expectForbidden, mockMvc.perform(get(BASE + "/events/" + id()).with(user)));
//         check(expectForbidden, mockMvc.perform(post(BASE + "/events").with(user)
//                 .contentType(MediaType.APPLICATION_JSON).content("{}")));
//         check(expectForbidden, mockMvc.perform(patch(BASE + "/events/" + id()).with(user)
//                 .contentType(MediaType.APPLICATION_JSON).content("{}")));
//         check(expectForbidden, mockMvc.perform(delete(BASE + "/events/" + id()).with(user)));
//         check(expectForbidden, mockMvc.perform(post(BASE + "/refresh").with(user)));
//     }

//     private void check(boolean expectForbidden, ResultActions actions) throws Exception {
//         if (expectForbidden) {
//             assertForbiddenForRole(actions);
//         } else {
//             assertNotUnauthorizedOrForbidden(actions);
//         }
//     }
// }
