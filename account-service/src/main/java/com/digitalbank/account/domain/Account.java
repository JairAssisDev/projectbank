package com.digitalbank.account.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Conta corrente. O saldo NUNCA é alterado via "read-modify-write" em memória:
 * débitos e créditos usam UPDATE atômicos em {@code AccountRepository}, o que elimina
 * lost updates sob alta concorrência sem precisar de locks explícitos.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Column(nullable = false, unique = true, length = 20)
    private String document;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // JPA
    }

    private Account(UUID id, String ownerName, String document, String passwordHash, BigDecimal balance,
                    Instant now) {
        this.id = id;
        this.ownerName = ownerName;
        this.document = document;
        this.passwordHash = passwordHash;
        this.balance = balance;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Account open(String ownerName, String document, String passwordHash, BigDecimal initialBalance,
                               Instant now) {
        if (initialBalance == null || initialBalance.signum() < 0) {
            throw new IllegalArgumentException("Saldo inicial não pode ser negativo");
        }
        return new Account(UUID.randomUUID(), ownerName.trim(), document.trim(), passwordHash,
                initialBalance.setScale(2, RoundingMode.UNNECESSARY), now);
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getDocument() {
        return document;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
