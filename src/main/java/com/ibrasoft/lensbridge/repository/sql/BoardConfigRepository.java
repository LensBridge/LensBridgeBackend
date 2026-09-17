package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BoardConfigRepository extends JpaRepository<DeviceConfig, UUID> {}
