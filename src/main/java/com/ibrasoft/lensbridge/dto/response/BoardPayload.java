package com.ibrasoft.lensbridge.dto.response;

import java.util.List;

import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.Poster;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BoardPayload {
    private BoardConfig boardConfig;

    private List<Event> events;

    private List<Poster> posters;

    // private List<JumuahPrayer> jumuahPrayers;

    
}
