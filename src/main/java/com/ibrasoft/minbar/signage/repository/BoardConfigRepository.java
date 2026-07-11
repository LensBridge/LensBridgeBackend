package com.ibrasoft.minbar.signage.repository;

import com.ibrasoft.minbar.signage.model.embedded.DeviceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BoardConfigRepository extends JpaRepository<DeviceConfig, UUID> {}
