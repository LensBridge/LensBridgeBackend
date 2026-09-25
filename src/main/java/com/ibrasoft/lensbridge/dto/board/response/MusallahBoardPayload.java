package com.ibrasoft.lensbridge.dto.board.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One day of a board's content: the file {@code payloads/<date>.json} inside a signed
 * content package, built by {@code BoardPayloadAssembler#assembleForDay}. No endpoint serves
 * it directly; boards install packages and render from these files.
 */
@Schema(description = "Format of each payloads/<date>.json file inside a signed content package (.mbu). "
        + "Not returned by any endpoint; boards read it from installed packages. weather is always null "
        + "here: boards fetch current weather from GET /api/agent/weather.")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusallahBoardPayload {
    private DeviceConfig deviceConfig;
    private List<FrameDefinition> frames;
    private JsonNode weather;
}
