package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, UUID> {

    /** Pending commands for a device, oldest first — used to flush the queue on session auth. */
    List<DeviceCommand> findByDeviceIdAndStatusOrderByIssuedAtAsc(UUID deviceId, DeviceCommandStatus status);

    /** Recent command history for a device (any status), newest first. */
    List<DeviceCommand> findTop50ByDeviceIdOrderByIssuedAtDesc(UUID deviceId);
}
