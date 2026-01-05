package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.Poster;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PosterRepository extends MongoRepository<Poster, UUID> {

    @Query("{ 'startDate': { $lte: ?0 }, 'endDate': { $gt: ?0 } }")
    List<Poster> findActivePostersAt(LocalDate date);

}
