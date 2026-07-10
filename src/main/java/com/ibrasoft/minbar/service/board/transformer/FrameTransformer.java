package com.ibrasoft.minbar.service.board.transformer;

import com.ibrasoft.minbar.model.board.frames.FrameDefinition;
import com.ibrasoft.minbar.model.board.frames.FrameType;
import com.ibrasoft.minbar.service.board.BoardContext;

public interface FrameTransformer<T> {
    FrameType supports();
    FrameDefinition transform(T source, BoardContext ctx);
}
