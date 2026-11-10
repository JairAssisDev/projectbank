package com.digitalbank.transfer.application.query;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalbank.transfer.domain.exception.TransferNotFoundException;
import com.digitalbank.transfer.infrastructure.persistence.TransferViewRepository;

/** Lado Query do CQRS: casos de uso de leitura sobre o read model {@link TransferView}. */
@Service
@Transactional(readOnly = true)
public class TransferQueryService {

    private final TransferViewRepository views;

    public TransferQueryService(TransferViewRepository views) {
        this.views = views;
    }

    /**
     * Transferência visível para a conta (como remetente ou destinatária).
     * Transferência de terceiros responde "não encontrada" (e não "proibida") para não revelar que o ID existe.
     */
    public TransferView findVisibleTo(UUID transferId, UUID accountId) {
        return views.findById(transferId)
                .filter(v -> accountId.equals(v.getFromAccountId()) || accountId.equals(v.getToAccountId()))
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }

    /** Transferências enviadas e recebidas pela conta, mais recentes primeiro. */
    public Page<TransferView> findByAccount(UUID accountId, Pageable pageable) {
        return views.findByAccount(accountId, pageable);
    }
}
