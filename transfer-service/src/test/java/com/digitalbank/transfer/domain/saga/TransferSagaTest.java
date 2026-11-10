package com.digitalbank.transfer.domain.saga;

import static com.digitalbank.transfer.domain.saga.TransferSaga.decide;
import static com.digitalbank.transfer.domain.saga.SagaStep.CREDIT;
import static com.digitalbank.transfer.domain.saga.SagaStep.DEBIT;
import static com.digitalbank.transfer.domain.saga.SagaStep.REFUND;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.digitalbank.transfer.domain.model.TransferStatus;
import com.digitalbank.transfer.domain.saga.TransferSaga.SagaAction;
import com.digitalbank.transfer.domain.saga.TransferSaga.Transition;

class TransferSagaTest {

    @Test
    void happyPath() {
        assertThat(decide(TransferStatus.PENDING, DEBIT, true))
                .isEqualTo(new Transition.Advance(TransferStatus.DEBITED, SagaAction.SEND_CREDIT));
        assertThat(decide(TransferStatus.DEBITED, CREDIT, true))
                .isEqualTo(new Transition.Advance(TransferStatus.COMPLETED, SagaAction.PUBLISH_COMPLETED));
    }

    @Test
    void debitFailureEndsSagaWithoutCompensation() {
        assertThat(decide(TransferStatus.PENDING, DEBIT, false))
                .isEqualTo(new Transition.Advance(TransferStatus.FAILED, SagaAction.NONE));
    }

    @Test
    void creditFailureTriggersCompensation() {
        assertThat(decide(TransferStatus.DEBITED, CREDIT, false))
                .isEqualTo(new Transition.Advance(TransferStatus.COMPENSATING, SagaAction.SEND_REFUND));
        assertThat(decide(TransferStatus.COMPENSATING, REFUND, true))
                .isEqualTo(new Transition.Advance(TransferStatus.COMPENSATED, SagaAction.NONE));
    }

    @Test
    void refundFailureNeedsManualIntervention() {
        assertThat(decide(TransferStatus.COMPENSATING, REFUND, false))
                .isEqualTo(new Transition.Advance(TransferStatus.COMPENSATION_FAILED, SagaAction.NONE));
    }

    @Test
    void duplicatedOrOutOfOrderRepliesAreIgnored() {
        assertThat(decide(TransferStatus.DEBITED, DEBIT, true)).isInstanceOf(Transition.Ignore.class);
        assertThat(decide(TransferStatus.PENDING, CREDIT, true)).isInstanceOf(Transition.Ignore.class);
        assertThat(decide(TransferStatus.COMPENSATING, CREDIT, false)).isInstanceOf(Transition.Ignore.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"COMPLETED", "FAILED", "COMPENSATED", "COMPENSATION_FAILED"})
    void terminalStatesIgnoreEverything(TransferStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (SagaStep op : SagaStep.values()) {
            assertThat(decide(terminal, op, true)).isInstanceOf(Transition.Ignore.class);
            assertThat(decide(terminal, op, false)).isInstanceOf(Transition.Ignore.class);
        }
    }
}
