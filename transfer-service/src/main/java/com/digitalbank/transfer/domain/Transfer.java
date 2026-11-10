package com.digitalbank.transfer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Agregado da transferência (write model) — também é o estado persistido da Saga. */
@Entity
@Table(name = "transfer")
public class Transfer {

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

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transfer() {
        // JPA
    }

    private Transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey, Instant now) {
        this.id = UUID.randomUUID();
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = TransferStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Transfer request(UUID from, UUID to, BigDecimal amount, String idempotencyKey, Instant now) {
        if (from == null || to == null) {
            throw new InvalidTransferException("Contas de origem e destino são obrigatórias");
        }
        if (from.equals(to)) {
            throw new InvalidTransferException("Conta de origem e destino devem ser diferentes");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransferException("Valor da transferência deve ser positivo");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidTransferException("Valor deve ter no máximo 2 casas decimais");
        }
        return new Transfer(from, to, amount.setScale(2), idempotencyKey, now);
    }

    /** Aplica uma transição decidida por {@link TransferSaga}. */
    public void moveTo(TransferStatus next, String reason, Instant now) {
        this.status = next;
        if (reason != null) {
            this.failureReason = reason;
        }
        this.updatedAt = now;
    }

    public boolean sameRequestAs(UUID from, UUID to, BigDecimal value) {
        return fromAccountId.equals(from) && toAccountId.equals(to) && amount.compareTo(value) == 0;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
