package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.DeviceTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, UUID> {

    List<DeviceTelemetry> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId);

    @Modifying
    @Query("DELETE FROM DeviceTelemetry t WHERE t.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
