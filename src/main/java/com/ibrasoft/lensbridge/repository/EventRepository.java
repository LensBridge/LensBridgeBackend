package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Event;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends MongoRepository<Event, UUID> {

    /**
     * Find all events for a specific audience or BOTH.
     */
    @Query("{ 'audience': { $in: [?0, 'BOTH'] } }")
    List<Event> findByAudienceOrBoth(Audience audience, Sort sort);

    /**
     * Find events for a specific audience or BOTH within a time range.
     * Events overlap with range if: startTimestamp <= rangeEnd AND endTimestamp >= rangeStart
     */
    @Query("{ 'audience': { $in: [?0, 'BOTH'] }, 'startTimestamp': { $lte: ?2 }, 'endTimestamp': { $gte: ?1 } }")
    List<Event> findByAudienceOrBothInTimeRange(Audience audience, long rangeStart, long rangeEnd, Sort sort);

    /**
     * Find all upcoming events for a specific audience or BOTH (startTimestamp >= now).
     */
    @Query("{ 'audience': { $in: [?0, 'BOTH'] }, 'startTimestamp': { $gte: ?1 } }")
    List<Event> findUpcomingByAudienceOrBoth(Audience audience, long nowTimestamp, Sort sort);

    /**
     * Find all events sorted by startTimestamp ascending.
     */
    List<Event> findAllByOrderByStartTimestampAsc();
}
