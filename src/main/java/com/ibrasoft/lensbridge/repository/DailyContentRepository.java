package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.DailyContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DailyContentRepository extends MongoRepository<DailyContent, LocalDate> {
}
