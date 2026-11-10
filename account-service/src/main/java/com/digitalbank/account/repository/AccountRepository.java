package com.digitalbank.account.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.digitalbank.account.domain.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByDocument(String document);

    Optional<Account> findByDocument(String document);

    /**
     * Débito atômico: o próprio banco garante que o saldo nunca fica negativo
     * (o WHERE é reavaliado sob o lock de linha do UPDATE).
     *
     * @return 1 se debitou, 0 se o saldo era insuficiente (ou a conta não existe)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Account a
               SET a.balance = a.balance - :amount, a.updatedAt = :now
             WHERE a.id = :id AND a.balance >= :amount
            """)
    int debit(@Param("id") UUID id, @Param("amount") BigDecimal amount, @Param("now") Instant now);

    /**
     * Crédito atômico.
     *
     * @return 1 se creditou, 0 se a conta não existe
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Account a
               SET a.balance = a.balance + :amount, a.updatedAt = :now
             WHERE a.id = :id
            """)
    int credit(@Param("id") UUID id, @Param("amount") BigDecimal amount, @Param("now") Instant now);
}
