package com.ibrasoft.lensbridge.model.board;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Document("board_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardConfig {
    /**
     * The location of the board (e.g., brothers' musallah, sisters' musallah).
     * Since there will only be one of each, we use the location as the document ID.
     * Two-for-one design!
     */
    @Id
    private BoardLocation boardLocation;

    /**
     * The prayer location details (city, country, coordinates, timezone, calculation method).
     */
    private Location location;

    /**
     * Default poster cycle duration in milliseconds
     */
    private int posterCycleInterval;

    /**
     * How long to wait before refreshing data after Ishaa
     */
    private int refreshAfterIshaaMinutes;

    /**
     * Enable dark mode after Ishaa
     */
    private boolean darkModeAfterIsha;

    /**
     * How long to wait after isha (in minutes) before enabling dark mode
     */
    private int darkModeMinutesAfterIsha;

    /**
     * Enable scrolling message at the bottom of the screen
     */
    private boolean enableScrollingMessage;

    /**
     * The scrolling message text
     */
    private String scrollingMessage;
}
