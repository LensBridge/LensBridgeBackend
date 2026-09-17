package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    /** Devices whose last heartbeat is older than the cutoff (or never reported). Used by the offline-detection cron. */
    List<Device> findByRevokedAtIsNullAndLastHeartbeatBefore(Instant cutoff);

    /** Devices that have been enrolled but never reported a heartbeat. */
    List<Device> findByRevokedAtIsNullAndLastHeartbeatIsNull();
}
