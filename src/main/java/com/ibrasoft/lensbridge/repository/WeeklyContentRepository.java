package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.WeekId;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyContentRepository extends MongoRepository<WeeklyContent, WeekId> {
    
    /**
     * Find weekly content by year.
     */
    List<WeeklyContent> findByWeekIdYear(Integer year);
    
    /**
     * Find weekly content by year and week number.
     */
    List<WeeklyContent> findByWeekIdYearAndWeekIdWeekNumber(Integer year, Integer weekNumber);
}
