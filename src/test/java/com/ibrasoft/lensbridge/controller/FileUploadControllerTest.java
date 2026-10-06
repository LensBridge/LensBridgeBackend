package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.upload.response.UploadCompletionResponse;
import com.ibrasoft.lensbridge.dto.upload.response.UploadDto;
import com.ibrasoft.lensbridge.exception.DailyLimitExceededException;
import com.ibrasoft.lensbridge.exception.GlobalExceptionHandler;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.security.CurrentUser;
import com.ibrasoft.lensbridge.service.UploadLimitsService;
import com.ibrasoft.lensbridge.service.UploadService;
import com.ibrasoft.lensbridge.service.UploadWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request validation on the upload endpoints (violations must be a 400 in the shared
 * {@code MessageResponse} shape, and must stop the request before any service runs) and the
 * who-may-read-what rules on the two lookup endpoints.
 */
class FileUploadControllerTest {

    private static final String SHA = "0123456789abcdef".repeat(4);

    private final UploadService uploadService = mock(UploadService.class);
    private final UploadWorkflowService workflow = mock(UploadWorkflowService.class);
    private final UploadLimitsService limits = mock(UploadLimitsService.class);

    private final User caller = new User();
    private final UUID eventId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        caller.setId(UUID.randomUUID());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        HandlerMethodArgumentResolver currentUser = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return caller;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new FileUploadController(uploadService, workflow, limits))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(currentUser, new PageableHandlerMethodArgumentResolver())
                .build();

        when(limits.getHighestRole(any())).thenReturn(Role.USER);
    }

    private static Authentication holding(String... authorities) {
        return new UsernamePasswordAuthenticationToken("u", "p",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    /** Request parameters from defaults, with {@code overrides} (name, value pairs) replacing them. */
    private static Map<String, String> params(Map<String, String> defaults, String... overrides) {
        Map<String, String> params = new LinkedHashMap<>(defaults);
        for (int i = 0; i < overrides.length; i += 2) {
            params.put(overrides[i], overrides[i + 1]);
        }
        return params;
    }

    private MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request, Map<String, String> params) {
        request.principal(holding("ROLE_USER"));
        params.forEach(request::param);
        return request;
    }

    private MockHttpServletRequestBuilder presign(String... overrides) {
        return withParams(post("/api/upload/{eventId}/direct/presign", eventId), params(Map.of(
                "filename", "a.jpg",
                "contentType", "image/jpeg",
                "fileSize", "1024",
                "expectedSha256", SHA), overrides));
    }

    private MockHttpServletRequestBuilder complete(String... overrides) {
        return withParams(post("/api/upload/{eventId}/direct/complete", eventId), params(Map.of(
                "objectKey", "images/" + UUID.randomUUID(),
                "filename", "a.jpg",
                "contentType", "image/jpeg",
                "fileSize", "1024",
                "expectedSha256", SHA), overrides));
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    void aValidPresignReachesTheWorkflow() throws Exception {
        mockMvc.perform(presign()).andExpect(status().isOk());

        verify(workflow).initiateUpload(eq(eventId), eq("a.jpg"), eq("image/jpeg"), eq(1024L), eq(SHA),
                eq(caller.getId()), eq(Role.USER));
    }

    @Test
    void aNonPositiveFileSizeIsA400InTheMessageShape() throws Exception {
        for (String size : new String[] { "0", "-5" }) {
            mockMvc.perform(presign("fileSize", size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("fileSize")))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }
        verifyNoInteractions(workflow);
    }

    @Test
    void aMalformedSha256IsA400() throws Exception {
        for (String sha : new String[] { "abc", "z".repeat(64), SHA + "0" }) {
            mockMvc.perform(presign("expectedSha256", sha))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("expectedSha256")));
        }
        verifyNoInteractions(workflow);
    }

    @Test
    void anUppercaseSha256IsAccepted() throws Exception {
        mockMvc.perform(presign("expectedSha256", SHA.toUpperCase())).andExpect(status().isOk());
    }

    @Test
    void aFileNameLongerThanTheColumnIsA400NotAFailedInsert() throws Exception {
        mockMvc.perform(presign("filename", "x".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("filename")));
        mockMvc.perform(complete("filename", "x".repeat(256)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workflow);
    }

    @Test
    void theOptionalCompleteFieldsAreBoundedToo() throws Exception {
        mockMvc.perform(complete("description", "d".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("description")));
        mockMvc.perform(complete("instagramHandle", "h".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("instagramHandle")));
        verifyNoInteractions(workflow);
    }

    @Test
    void fieldsAtTheLimitAreAccepted() throws Exception {
        when(workflow.completeUpload(any(), anyString(), anyString(), anyString(), anyLong(), any(), any(),
                anyBoolean(), anyString(), any(), any())).thenReturn(UploadCompletionResponse.builder().build());

        mockMvc.perform(complete("filename", "x".repeat(255), "description", "d".repeat(255), "instagramHandle", "h".repeat(255)))
                .andExpect(status().isOk());
    }

    @Test
    void completeUsesTheCallersRoleForTheLimitsCheck() throws Exception {
        when(limits.getHighestRole(any())).thenReturn(Role.ADMIN);
        when(workflow.completeUpload(any(), anyString(), anyString(), anyString(), anyLong(), any(), any(),
                anyBoolean(), anyString(), any(), any())).thenReturn(UploadCompletionResponse.builder().build());

        mockMvc.perform(complete()).andExpect(status().isOk());

        verify(workflow).completeUpload(eq(eventId), anyString(), eq("a.jpg"), eq("image/jpeg"), eq(1024L),
                eq(null), eq(null), eq(false), eq(SHA), eq(caller.getId()), eq(Role.ADMIN));
    }

    @Test
    void theDailyLimitResponseNamesTheRealRole() throws Exception {
        when(workflow.initiateUpload(any(), any(), any(), anyLong(), any(), any(), any()))
                .thenThrow(new DailyLimitExceededException(10, 10, "admin"));

        mockMvc.perform(presign())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.role").value("admin"));
    }

    // ── who may read an upload ────────────────────────────────────────────────

    @Test
    void aPlainUserIsNotAModeratorWhenReadingAnUpload() throws Exception {
        UUID uploadId = UUID.randomUUID();
        when(uploadService.getUploadByIdAsDto(uploadId, caller.getId(), false)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/upload/{id}", uploadId).principal(holding("ROLE_USER", "media:upload:self")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theAdminRoleOrTheModeratePermissionMakesAModerator() throws Exception {
        UUID uploadId = UUID.randomUUID();
        when(uploadService.getUploadByIdAsDto(uploadId, caller.getId(), true))
                .thenReturn(Optional.of(new UploadDto()));

        mockMvc.perform(get("/api/upload/{id}", uploadId).principal(holding("ROLE_USER", Role.ADMIN.getAuthority())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/upload/{id}", uploadId)
                        .principal(holding("ROLE_USER", Permission.Authority.MEDIA_UPLOAD_MODERATE)))
                .andExpect(status().isOk());
    }

    @Test
    void boardRolesDoNotGrantModeration() throws Exception {
        UUID uploadId = UUID.randomUUID();
        when(uploadService.getUploadByIdAsDto(uploadId, caller.getId(), false)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/upload/{id}", uploadId).principal(holding("ROLE_USER", "ROLE_BOARD_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theEventListingIsScopedByTheCallersModeration() throws Exception {
        when(uploadService.getUploadsByEventAsDto(eq(eventId), eq(caller.getId()), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/upload/event/{id}", eventId).principal(holding("ROLE_USER")))
                .andExpect(status().isOk());

        verify(uploadService).getUploadsByEventAsDto(eq(eventId), eq(caller.getId()), eq(false), any(Pageable.class));
    }
}
