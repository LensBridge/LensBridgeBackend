package com.ibrasoft.lensbridge.model.board;

/**
 * State machine for an admin-issued device command.
 * <p>
 * Terminal states: SUCCEEDED, FAILED, TIMEOUT, REJECTED, EXPIRED.
 */
public enum DeviceCommandStatus {
    /** Persisted, awaiting an authenticated agent session. */
    PENDING,
    /** Frame written to the agent's WS session. */
    DELIVERED,
    /** Agent acknowledged receipt and accepted the command. */
    ACKED,
    /** Agent reported it has begun executing. */
    RUNNING,
    /** Agent finished successfully. */
    SUCCEEDED,
    /** Agent reported a runtime error. */
    FAILED,
    /** Agent (or backend) hit the deadline before completion. */
    TIMEOUT,
    /** Agent refused the command (e.g. unknown kind). */
    REJECTED,
    /** Backend gave up waiting for a session before the command could be delivered. */
    EXPIRED;

    public boolean isTerminal() {
        return switch (this) {
            case SUCCEEDED, FAILED, TIMEOUT, REJECTED, EXPIRED -> true;
            default -> false;
        };
    }
}
