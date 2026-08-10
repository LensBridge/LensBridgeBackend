package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.service.board.BoardContext;

import java.util.List;

/**
 * Fetches its own source data for one board and turns it into zero or more frames.
 * <p>
 * {@link com.ibrasoft.lensbridge.service.board.BoardPayloadAssembler} only knows this
 * interface — it never references a concrete frame type. Adding a new frame type means
 * adding one new {@code @Component} that implements this; the assembler does not change.
 */
public interface FrameProducer {
    List<FrameDefinition> produce(BoardContext ctx);
}
