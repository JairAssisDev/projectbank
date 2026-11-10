package com.digitalbank.transfer.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.digitalbank.transfer.domain.model.Transfer;

import jakarta.persistence.LockModeType;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    /** Serializa o processamento de respostas de uma mesma Saga (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transfer t WHERE t.id = :id")
    Optional<Transfer> findByIdForUpdate(@Param("id") UUID id);
}
