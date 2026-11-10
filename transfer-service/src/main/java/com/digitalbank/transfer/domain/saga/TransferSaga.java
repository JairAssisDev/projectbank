package com.digitalbank.transfer.domain.saga;

import com.digitalbank.transfer.domain.model.TransferStatus;

/**
 * Máquina de estados da Saga (função pura, sem I/O — fácil de testar).
 * Dado o estado atual e a resposta de um participante, decide a transição e a próxima ação.
 * Respostas duplicadas ou fora de ordem resultam em {@link Transition.Ignore}.
 */
public final class TransferSaga {

    private TransferSaga() {
    }

    /** Próximo comando/evento que o orquestrador deve emitir após a transição. */
    public enum SagaAction {
        SEND_CREDIT,
        SEND_REFUND,
        PUBLISH_COMPLETED,
        NONE
    }

    public sealed interface Transition {

        record Advance(TransferStatus next, SagaAction action) implements Transition {
        }

        record Ignore(String reason) implements Transition {
        }
    }

    public static Transition decide(TransferStatus current, SagaStep step, boolean success) {
        return switch (current) {
            case PENDING -> expect(step, SagaStep.DEBIT, current,
                    success ? new Transition.Advance(TransferStatus.DEBITED, SagaAction.SEND_CREDIT)
                            : new Transition.Advance(TransferStatus.FAILED, SagaAction.NONE));
            case DEBITED -> expect(step, SagaStep.CREDIT, current,
                    success ? new Transition.Advance(TransferStatus.COMPLETED, SagaAction.PUBLISH_COMPLETED)
                            : new Transition.Advance(TransferStatus.COMPENSATING, SagaAction.SEND_REFUND));
            case COMPENSATING -> expect(step, SagaStep.REFUND, current,
                    success ? new Transition.Advance(TransferStatus.COMPENSATED, SagaAction.NONE)
                            : new Transition.Advance(TransferStatus.COMPENSATION_FAILED, SagaAction.NONE));
            case COMPLETED, FAILED, COMPENSATED, COMPENSATION_FAILED ->
                    new Transition.Ignore("Saga já finalizada em " + current);
        };
    }

    private static Transition expect(SagaStep received, SagaStep expected, TransferStatus current,
                                     Transition transition) {
        return received == expected
                ? transition
                : new Transition.Ignore("Resposta " + received + " inesperada no estado " + current
                        + " (duplicada ou fora de ordem)");
    }
}
