package com.ibrasoft.lensbridge.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects (a WebSocket write, a STOMP publish) only once the database change they
 * announce is durable.
 * <p>
 * Doing them inside the transaction is wrong in both directions. A fast agent ack can reach
 * the backend before the commit and find no command row, and an event can be published for a
 * transaction that then rolls back. When a transaction is active the action waits for its
 * commit (and is dropped on rollback); when none is, the data was written by auto-committed
 * repository calls already, so the action runs at once.
 * <p>
 * An action deferred this way runs after the surrounding transaction has finished, so any
 * database write it makes must open its own transaction ({@code REQUIRES_NEW}); a plain one
 * would join the finished transaction and never commit.
 */
@Slf4j
final class AfterCommit {

    private AfterCommit() {}

    static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    // The data is already committed; a failing side effect must not look like a failed write.
                    log.error("Post-commit action failed", e);
                }
            }
        });
    }
}
