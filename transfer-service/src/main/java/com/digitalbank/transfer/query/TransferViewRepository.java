package com.digitalbank.transfer.query;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferViewRepository extends JpaRepository<TransferView, UUID> {

    @Query("""
            SELECT v FROM TransferView v
             WHERE v.fromAccountId = :accountId OR v.toAccountId = :accountId
             ORDER BY v.createdAt DESC
            """)
    Page<TransferView> findByAccount(@Param("accountId") UUID accountId, Pageable pageable);
}
