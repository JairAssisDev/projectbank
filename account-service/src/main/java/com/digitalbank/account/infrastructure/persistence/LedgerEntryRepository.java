package com.digitalbank.account.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.digitalbank.account.domain.model.LedgerEntry;
import com.digitalbank.account.domain.model.LedgerEntryType;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByTransferIdAndAccountIdAndEntryType(UUID transferId, UUID accountId, LedgerEntryType entryType);

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
