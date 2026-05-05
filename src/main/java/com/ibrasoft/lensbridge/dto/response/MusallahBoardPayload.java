package com.ibrasoft.lensbridge.dto.response;

import com.ibrasoft.lensbridge.model.board.BoardConfig;
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
    private BoardConfig boardConfig;
    private List<FrameDefinition> frames;
}
