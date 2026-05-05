package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.service.board.BoardContext;

public interface FrameTransformer<T> {
    FrameType supports();
    FrameDefinition transform(T source, BoardContext ctx);
}
