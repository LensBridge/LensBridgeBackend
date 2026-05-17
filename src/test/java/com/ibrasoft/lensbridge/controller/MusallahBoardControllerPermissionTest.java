package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.security.WebSecurityConfig;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.board.BoardPayloadAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that every endpoint on {@link MusallahBoardController} is genuinely public
 * (matched by the {@code /api/musallah/**} permitAll rule in {@link WebSecurityConfig}).
 * A response that is NOT 401 and NOT 403 when called anonymously is a PASS.
 */
@WebMvcTest(MusallahBoardController.class)
@Import(WebSecurityConfig.class)
@TestPropertySource(properties = {
        "frontend.baseurl=http://localhost:3000",
        "musallahboard.baseurl=http://localhost:4000"
})
class MusallahBoardControllerPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PosterService posterService;

    @MockitoBean
    private BoardService boardService;

    @MockitoBean
    private BoardPayloadAssembler payloadAssembler;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    void getBoardConfig_isPublic() throws Exception {
        when(boardService.getBoardConfig(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/musallah/config").param("deviceId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCurrentWeeklyContent_isPublic() throws Exception {
        when(boardService.getCurrentWeeklyContent()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/musallah/weekly-content"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWeeklyContent_isPublic() throws Exception {
        when(boardService.getWeeklyContent(anyInt(), anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/musallah/weekly-content/{year}/{weekNumber}", 2026, 12))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActivePosterFrames_isPublic() throws Exception {
        when(posterService.getActivePosterFrameDefinitions(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/musallah/posters").param("audience", "BOTH"))
                .andExpect(status().isOk());
    }

    @Test
    void getUpcomingEvents_isPublic() throws Exception {
        when(boardService.getUpcomingEventsForAudience(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/musallah/events").param("audience", "BOTH"))
                .andExpect(status().isOk());
    }

    @Test
    void getEventsInRange_isPublic() throws Exception {
        when(boardService.getEventsForAudienceInRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/musallah/events/range")
                        .param("audience", "BOTH")
                        .param("start", "2026-01-01T00:00:00Z")
                        .param("end", "2026-12-31T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    void getBoardPayload_isPublic() throws Exception {
        when(payloadAssembler.assemble(any(UUID.class))).thenReturn(new MusallahBoardPayload());

        mockMvc.perform(get("/api/musallah/payload").param("deviceId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
