package com.ibrasoft.minbar.dto.board.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibrasoft.minbar.model.board.embedded.DeviceConfig;
import com.ibrasoft.minbar.model.board.frames.FrameDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusallahBoardPayload {
    private DeviceConfig deviceConfig;
    private List<FrameDefinition> frames;
    private JsonNode weather;
}
