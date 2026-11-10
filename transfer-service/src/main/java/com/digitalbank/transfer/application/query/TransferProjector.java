package com.digitalbank.transfer.application.query;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.digitalbank.transfer.domain.model.Transfer;
import com.digitalbank.transfer.infrastructure.persistence.TransferViewRepository;

/**
 * Atualiza o read model na mesma transação do write model (CQRS lógico: modelos separados,
 * mesmo banco). Para escalar leituras, bastaria mover esta projeção para um consumidor de eventos.
 */
@Component
public class TransferProjector {

    private final TransferViewRepository views;

    public TransferProjector(TransferViewRepository views) {
        this.views = views;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void project(Transfer transfer) {
        views.save(TransferView.of(transfer));
    }
}
