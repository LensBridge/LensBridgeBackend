package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Besides the finders, this holds the only writers of a command's status after it is created.
 * <p>
 * The status changes come from three independent places that run concurrently: the agent's
 * frames, the delivery path and the reaper. Each was a read, modify, save of the whole row, so
 * the last writer won: a reaper TIMEOUT could overwrite a result that arrived a moment
 * earlier, or the reverse. Every method below is instead one conditional UPDATE that names the
 * states it may leave, and the row count says whether it won. Callers act on (and publish
 * events for) a transition only when the count is 1.
 * <p>
 * All of them clear the persistence context afterwards, so a copy loaded before the update
 * (still showing the old status) cannot be flushed back over it.
 */
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

    /**
     * PENDING to DELIVERED, once the frame is on the wire. Runs in its own transaction because
     * it may be called from a post-commit callback, where joining the (finished) outer
     * transaction would silently lose the write.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true)
    @Query("update DeviceCommand c set c.status = com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.DELIVERED, "
            + "c.deliveredAt = :now "
            + "where c.id = :id and c.status = com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.PENDING")
    int markDelivered(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * The agent has accepted the command. Only from PENDING or DELIVERED: a late or repeated ack
     * must not pull a command that already reports progress (RUNNING) or a result back to ACKED.
     * An ack that outruns the DELIVERED write (the agent answers faster than we record the send)
     * also stamps the delivery time, which the reaper's deadline arithmetic depends on.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update DeviceCommand c set c.status = com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.ACKED, "
            + "c.ackedAt = :now, c.deliveredAt = coalesce(c.deliveredAt, :now) "
            + "where c.id = :id and c.status in ("
            + "com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.PENDING, "
            + "com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.DELIVERED)")
    int markAcked(@Param("id") UUID id, @Param("now") Instant now);

    /** The agent reports it is executing. From any state before RUNNING; a no-op once there or beyond. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update DeviceCommand c set c.status = com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.RUNNING, "
            + "c.startedAt = coalesce(c.startedAt, :now), c.deliveredAt = coalesce(c.deliveredAt, :now) "
            + "where c.id = :id and c.status in ("
            + "com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.PENDING, "
            + "com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.DELIVERED, "
            + "com.ibrasoft.lensbridge.model.board.DeviceCommandStatus.ACKED)")
    int markRunning(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Terminal transition (result, timeout or expiry), allowed only from the states in
     * {@code from}. Output and error are written in the same statement as the status so a
     * reader never sees a SUCCEEDED command without its output. They are plain assignments:
     * a command that has not finished cannot have either yet.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update DeviceCommand c set c.status = :to, c.finishedAt = :now, "
            + "c.outputJson = :outputJson, c.errorMessage = :errorMessage "
            + "where c.id = :id and c.status in :from")
    int finish(@Param("id") UUID id,
               @Param("to") DeviceCommandStatus to,
               @Param("now") Instant now,
               @Param("outputJson") String outputJson,
               @Param("errorMessage") String errorMessage,
               @Param("from") Collection<DeviceCommandStatus> from);
}
