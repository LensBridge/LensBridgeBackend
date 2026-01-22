package com.ibrasoft.lensbridge.repository;

import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardConfigRepository extends MongoRepository<BoardConfig, BoardLocation> {
}
