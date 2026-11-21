package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.dto.board.agent.OutgoingAgentFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tells connected boards that their content may have changed, with a {@code content_changed}
 * frame on the agent WebSocket ({@code /api/agent/ws}). The agent answers by syncing its
 * content package a few seconds later, so an edit in the console reaches an online board
 * without waiting for its 30-minute sync.
 * <p>
 * The frame carries no content and no reason: the board always fetches its whole package
 * through {@code POST /api/agent/content-bundle}. The reason is only logged here.
 * <p>
 * Best effort. A board that is offline, or misses the frame, still picks the change up on its
 * next scheduled sync, and an offline board gets it on its next USB stick. Frames are sent
 * after the surrounding transaction commits, so a board never fetches before the change is
 * visible.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentChangeNotifier {

    static final String FRAME_TYPE = "content_changed";

    private final AgentSessionRegistry registry;

    /** Content every board may show (posters, events, socials, weekly content): every board. */
    public void contentChanged(String what) {
        AfterCommit.run(() -> {
            int sent = 0;
            for (AgentSession session : registry.authenticated()) {
                if (send(session)) sent++;
            }
            log.info("content_changed ({}) sent to {} connected board(s)", what, sent);
        });
    }

    /** Content of one board (its config, name or audience). */
    public void deviceChanged(UUID deviceId, String what) {
        AfterCommit.run(() -> registry.get(deviceId).ifPresentOrElse(
                session -> {
                    if (send(session)) log.info("content_changed ({}) sent to board {}", what, deviceId);
                },
                () -> log.debug("content_changed ({}) not sent: board {} is not connected", what, deviceId)));
    }

    private static boolean send(AgentSession session) {
        return session.send(OutgoingAgentFrame.builder().type(FRAME_TYPE));
    }
}
