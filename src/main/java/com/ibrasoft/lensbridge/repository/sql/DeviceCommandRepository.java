package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.minbar.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.minbar.board.DeviceCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, UUID> {

    /** Pending commands for a device, oldest first — used to flush the queue on session auth. */
    List<DeviceCommand> findByDeviceIdAndStatusOrderByIssuedAtAsc(UUID deviceId, DeviceCommandStatus status);

    /** Recent command history for a device (any status), newest first. */
    List<DeviceCommand> findTop50ByDeviceIdOrderByIssuedAtDesc(UUID deviceId);

    /** Commands that outlived their delivery window — reaper input. */
    List<DeviceCommand> findByStatusAndExpiresAtBefore(DeviceCommandStatus status, Instant cutoff);

    /**
     * In-flight commands delivered before {@code cutoff}. A coarse filter: the reaper still
     * checks each row against its own {@code deadlineMs} before declaring it timed out.
     */
    List<DeviceCommand> findByStatusInAndDeliveredAtBefore(
            Collection<DeviceCommandStatus> statuses, Instant cutoff);
}
