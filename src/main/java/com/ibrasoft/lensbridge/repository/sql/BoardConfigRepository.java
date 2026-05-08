package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardConfigRepository extends JpaRepository<BoardConfig, BoardLocation> {}
