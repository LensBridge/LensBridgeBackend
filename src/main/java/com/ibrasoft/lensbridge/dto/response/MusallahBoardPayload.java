package com.ibrasoft.lensbridge.dto.response;

import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
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
}
