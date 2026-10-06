package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.service.AdminAuditService;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.PromotableSocialMediaService;
import com.ibrasoft.lensbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bean validation on the content request DTOs, and the shape of the 400 a service-level
 * rejection produces. The columns behind these fields are varchar(255), widened to 1000 or
 * 4000 by V4; without {@code @Size} an over-long value used to reach the database and come back
 * as a 500, and a missing epoch-ms bound was silently read as 1970.
 */
@WebMvcTest(controllers = BoardAdminController.class)
@Import(MethodSecurityTestConfig.class)
class BoardAdminControllerContentValidationTest {

    private static final String BASE = "/api/admin/board";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;
    @MockitoBean
    private PosterService posterService;
    @MockitoBean
    private PromotableSocialMediaService socialMediaService;
    @MockitoBean
    private AdminAuditService auditService;
    @MockitoBean
    private BoardStreamHandler boardStreamHandler;
    @MockitoBean
    private UserService userService;

    /** The 200 paths read the saved entity back for the audit log, so the mocks must return one. */
    private void stubSaves() {
        when(boardService.createEvent(any())).thenReturn(BoardEvent.builder().build());
        when(boardService.saveWeeklyContent(anyInt(), anyInt(), any()))
                .thenReturn(WeeklyContent.builder().build());
        when(socialMediaService.create(any())).thenReturn(PromotableSocialMedia.builder().build());
    }

    private void send(MockHttpServletRequestBuilder request, String json, int expectedStatus) throws Exception {
        mockMvc.perform(request
                        .with(TestAuthorities.as(Role.BOARD_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus));
    }

    private static String event(String times) {
        return "{\"name\":\"Halaqa\",\"description\":\"d\",\"location\":\"Room\"," + times
                + "\"audience\":\"BOTH\"}";
    }

    // ==================== calendar events ====================

    @Test
    void createEventRejectsAMissingStartTime() throws Exception {
        mockMvc.perform(post(BASE + "/events")
                        .with(TestAuthorities.as(Role.BOARD_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("\"endEpochMs\":1700000000000,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("startEpochMs")));

        verify(boardService, never()).createEvent(any());
    }

    @Test
    void createEventRejectsAMissingEndTime() throws Exception {
        send(post(BASE + "/events"), event("\"startEpochMs\":1700000000000,"), 400);

        verify(boardService, never()).createEvent(any());
    }

    @Test
    void createEventAcceptsAnOmittedAllDay() throws Exception {
        stubSaves();
        send(post(BASE + "/events"),
                event("\"startEpochMs\":1700000000000,\"endEpochMs\":1700003600000,"), 201);

        verify(boardService).createEvent(any());
    }

    @Test
    void createEventRejectsANameLongerThanTheColumn() throws Exception {
        send(post(BASE + "/events"),
                "{\"name\":\"" + "x".repeat(256) + "\",\"description\":\"d\",\"location\":\"Room\","
                        + "\"startEpochMs\":1,\"endEpochMs\":2,\"audience\":\"BOTH\"}", 400);

        verify(boardService, never()).createEvent(any());
    }

    @Test
    void aServiceRejectionOfEndBeforeStartComesBackAsA400WithTheMessageBody() throws Exception {
        when(boardService.createEvent(any())).thenThrow(new ApiResponseException(
                HttpStatus.BAD_REQUEST, ErrorResponse.of("End time must not be before start time")));

        mockMvc.perform(post(BASE + "/events")
                        .with(TestAuthorities.as(Role.BOARD_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("\"startEpochMs\":2000,\"endEpochMs\":1000,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("End time must not be before start time"));
    }

    @Test
    void updateEventRejectsALocationLongerThanTheColumn() throws Exception {
        send(patch(BASE + "/events/" + java.util.UUID.randomUUID()),
                "{\"location\":\"" + "x".repeat(256) + "\"}", 400);
    }

    // ==================== weekly content ====================

    private static String quote(String field, int length) {
        return "{\"quotes\":[{\"kind\":\"HADITH\",\"" + field + "\":\"" + "x".repeat(length) + "\"}]}";
    }

    @Test
    void weeklyContentAcceptsAFullLengthHadithTranslation() throws Exception {
        stubSaves();
        send(put(BASE + "/weekly-content/2026/20"), quote("translation", 4000), 200);

        verify(boardService).saveWeeklyContent(anyInt(), anyInt(), any());
    }

    @Test
    void weeklyContentRejectsAQuoteTextOverTheColumn() throws Exception {
        for (String field : new String[] {"arabic", "transliteration", "translation"}) {
            send(put(BASE + "/weekly-content/2026/20"), quote(field, 4001), 400);
        }
        send(put(BASE + "/weekly-content/2026/20"), quote("reference", 1001), 400);

        verify(boardService, never()).saveWeeklyContent(anyInt(), anyInt(), any());
    }

    @Test
    void weeklyContentRejectsAKhatibOrRoomOverTheColumn() throws Exception {
        send(put(BASE + "/weekly-content/2026/20"),
                "{\"jummahPrayers\":[{\"prayerTime\":\"13:30\",\"khatib\":\"" + "x".repeat(256) + "\"}]}", 400);
        send(put(BASE + "/weekly-content/2026/20"),
                "{\"jummahPrayers\":[{\"prayerTime\":\"13:30\",\"room\":\"" + "x".repeat(256) + "\"}]}", 400);
    }

    @Test
    void aMalformedJummahTimeComesBackAsA400WithTheMessageBody() throws Exception {
        when(boardService.saveWeeklyContent(anyInt(), anyInt(), any())).thenThrow(new ApiResponseException(
                HttpStatus.BAD_REQUEST, ErrorResponse.of("prayerTime must be a 24-hour HH:mm time, got: 1:30pm")));

        mockMvc.perform(put(BASE + "/weekly-content/2026/20")
                        .with(TestAuthorities.as(Role.BOARD_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jummahPrayers\":[{\"prayerTime\":\"1:30pm\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("prayerTime")));
    }

    // ==================== ticker and config ====================

    @Test
    void tickerRejectsAMessageOverTheColumn() throws Exception {
        send(patch(BASE + "/configs/" + java.util.UUID.randomUUID() + "/ticker"),
                "{\"scrollingMessages\":[\"ok\",\"" + "x".repeat(4001) + "\"]}", 400);
    }

    @Test
    void configPatchRejectsAMessageOverTheColumn() throws Exception {
        send(patch(BASE + "/configs/" + java.util.UUID.randomUUID()),
                "{\"scrollingMessages\":[\"" + "x".repeat(4001) + "\"]}", 400);
    }

    /** A timezone-only location patch has to bind, with the coordinates left null for the merge. */
    @Test
    void configPatchAcceptsALocationWithOnlyATimezone() throws Exception {
        send(patch(BASE + "/configs/" + java.util.UUID.randomUUID()),
                "{\"location\":{\"timezone\":\"America/Toronto\"}}", 200);
    }

    // ==================== socials and posters ====================

    @Test
    void socialCreateRejectsTextOverTheColumn() throws Exception {
        stubSaves();
        String base = "{\"name\":\"n\",\"duration\":10,\"audience\":\"BOTH\",\"type\":\"INSTAGRAM\","
                + "\"url\":\"https://instagram.com/x\",\"headerText\":\"h\",\"heroText\":\"h\","
                + "\"footerText\":\"f\"}";
        send(post(BASE + "/socials"), base, 201);

        send(post(BASE + "/socials"), base.replace("\"footerText\":\"f\"",
                "\"footerText\":\"" + "x".repeat(1001) + "\""), 400);
        send(post(BASE + "/socials"), base.replace("\"heroText\":\"h\"",
                "\"heroText\":\"" + "x".repeat(1001) + "\""), 400);
    }

    @Test
    void socialUpdateRejectsTextOverTheColumn() throws Exception {
        send(patch(BASE + "/socials/" + java.util.UUID.randomUUID()),
                "{\"headerText\":\"" + "x".repeat(1001) + "\"}", 400);
    }

    @Test
    void posterUpdateRejectsATitleOverTheColumn() throws Exception {
        send(patch(BASE + "/posters/" + java.util.UUID.randomUUID()),
                "{\"title\":\"" + "x".repeat(256) + "\"}", 400);
    }
}
