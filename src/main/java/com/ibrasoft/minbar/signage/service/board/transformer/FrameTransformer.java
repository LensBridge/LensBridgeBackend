package com.ibrasoft.minbar.signage.service.board.transformer;

import com.ibrasoft.minbar.signage.model.frames.FrameDefinition;
import com.ibrasoft.minbar.signage.model.frames.FrameType;
import com.ibrasoft.minbar.signage.service.board.BoardContext;

public interface FrameTransformer<T> {
    FrameType supports();
    FrameDefinition transform(T source, BoardContext ctx);
}
