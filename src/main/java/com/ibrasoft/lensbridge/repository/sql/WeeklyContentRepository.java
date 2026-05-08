package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyContentRepository extends JpaRepository<WeeklyContent, UUID> {
    Optional<WeeklyContent> findByYearAndWeekNumber(int year, int weekNumber);
    boolean existsByYearAndWeekNumber(int year, int weekNumber);
    List<WeeklyContent> findByYear(int year);
}
