package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Poster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A poster's end date is what keeps it off the days after it (contract C6): each day of a
 * content package asks for the posters overlapping that day, on the real schema.
 */
@SqliteDataJpaTest
class PosterRepositoryTest {

    @Autowired
    private PosterRepository posters;

    private Poster save(String title, String start, String end, Audience audience) {
        return posters.saveAndFlush(Poster.builder().title(title).image("https://media.example.com/" + title + ".jpg")
                .duration(10).startTime(Instant.parse(start)).endTime(Instant.parse(end)).audience(audience).build());
    }

    private List<String> titlesOn(String dayStart, String nextDayStart, Audience audience) {
        return posters.findPostersForAudienceOverlapping(Instant.parse(dayStart), Instant.parse(nextDayStart), audience)
                .stream().map(Poster::getTitle).toList();
    }

    @Test
    void aPosterShowsUntilItsEndDateAndNotAfter() {
        save("iftar", "2026-11-01T05:00:00Z", "2026-11-20T05:00:00Z", Audience.BOTH);

        assertThat(titlesOn("2026-11-19T05:00:00Z", "2026-11-20T05:00:00Z", Audience.BROTHERS)).containsExactly("iftar");
        assertThat(titlesOn("2026-11-20T05:00:00Z", "2026-11-21T05:00:00Z", Audience.BROTHERS))
                .as("the day after it ends").isEmpty();
        assertThat(titlesOn("2026-10-31T04:00:00Z", "2026-11-01T04:00:00Z", Audience.BROTHERS))
                .as("the day before it starts").isEmpty();
    }

    @Test
    void anotherAudiencesPosterIsLeftOut() {
        save("sisters-halaqa", "2026-11-01T05:00:00Z", "2026-12-01T05:00:00Z", Audience.SISTERS);

        assertThat(titlesOn("2026-11-19T05:00:00Z", "2026-11-20T05:00:00Z", Audience.BROTHERS)).isEmpty();
        assertThat(titlesOn("2026-11-19T05:00:00Z", "2026-11-20T05:00:00Z", Audience.SISTERS))
                .containsExactly("sisters-halaqa");
    }
}
