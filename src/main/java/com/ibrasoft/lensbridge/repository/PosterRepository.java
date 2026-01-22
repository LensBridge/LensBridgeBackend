package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Poster;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PosterRepository extends MongoRepository<Poster, UUID> {

    /**
     * Find all posters active at a given date.
     */
    @Query("{ 'startDate': { $lte: ?0 }, 'endDate': { $gt: ?0 } }")
    List<Poster> findActivePostersAt(LocalDate date);

    /**
     * Find all posters for a specific audience or BOTH, active at a given date.
     * Sorted by startDate descending (newest first).
     */
    @Query("{ 'startDate': { $lte: ?0 }, 'endDate': { $gt: ?0 }, 'audience': { $in: [?1, 'BOTH'] } }")
    List<Poster> findActivePostersForAudienceAt(LocalDate date, Audience audience, Sort sort);

    /**
     * Find all posters for a specific audience or BOTH.
     */
    @Query("{ 'audience': { $in: [?0, 'BOTH'] } }")
    List<Poster> findByAudienceOrBoth(Audience audience, Sort sort);

    /**
     * Find all posters sorted by startDate descending (newest first).
     */
    List<Poster> findAllByOrderByStartDateDesc();

    /**
     * Find all posters for a specific audience.
     */
    List<Poster> findByAudience(Audience audience, Sort sort);

}
