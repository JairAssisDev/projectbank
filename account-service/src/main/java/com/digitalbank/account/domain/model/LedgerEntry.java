package com.digitalbank.account.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lançamento no livro-razão. A constraint única (transfer_id, account_id, entry_type)
 * garante idempotência: um mesmo passo da Saga nunca é aplicado duas vezes.
 */
@Entity
@Table(name = "account_ledger")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA
    }

    public LedgerEntry(UUID accountId, UUID transferId, LedgerEntryType entryType, BigDecimal amount, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.transferId = transferId;
        this.entryType = entryType;
        this.amount = amount;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
