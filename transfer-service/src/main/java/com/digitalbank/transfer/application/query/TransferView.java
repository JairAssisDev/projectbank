package com.digitalbank.transfer.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.digitalbank.transfer.domain.model.Transfer;
import com.digitalbank.transfer.domain.model.TransferStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read model (lado Query do CQRS): projeção desnormalizada e otimizada para consulta. */
@Entity
@Table(name = "transfer_view")
public class TransferView {

    @Id
    private UUID id;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransferView() {
        // JPA
    }

    public static TransferView of(Transfer t) {
        var view = new TransferView();
        view.id = t.getId();
        view.fromAccountId = t.getFromAccountId();
        view.toAccountId = t.getToAccountId();
        view.amount = t.getAmount();
        view.status = t.getStatus();
        view.failureReason = t.getFailureReason();
        view.createdAt = t.getCreatedAt();
        view.updatedAt = t.getUpdatedAt();
        return view;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
