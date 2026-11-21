package com.ibrasoft.lensbridge.service.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AfterCommitTest {

    @AfterEach
    void clean() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runsAtOnceWhenNoTransactionIsActive() {
        AtomicInteger runs = new AtomicInteger();

        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    void waitsForTheCommitWhenATransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        AfterCommit.run(runs::incrementAndGet);
        assertThat(runs).hasValue(0);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(runs).hasValue(1);
    }

    @Test
    void neverRunsWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        AfterCommit.run(runs::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(runs).hasValue(0);
    }

    /** The write has committed; a broken side effect must not surface as a failed request. */
    @Test
    void aFailingActionAfterCommitDoesNotPropagate() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> {
            throw new IllegalStateException("broker down");
        });

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
