package com.digitalbank.account.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalbank.account.domain.Account;
import com.digitalbank.account.domain.AccountNotFoundException;
import com.digitalbank.account.domain.DuplicateDocumentException;
import com.digitalbank.account.domain.LedgerEntry;
import com.digitalbank.account.domain.LedgerEntryType;
import com.digitalbank.account.domain.OperationResult;
import com.digitalbank.account.domain.OperationResult.FailureReason;
import com.digitalbank.account.repository.AccountRepository;
import com.digitalbank.account.repository.LedgerEntryRepository;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ cadastro / consulta

    @Transactional
    public Account open(String ownerName, String document, BigDecimal initialBalance) {
        if (accounts.existsByDocument(document.trim())) {
            throw new DuplicateDocumentException(document);
        }
        return accounts.save(Account.open(ownerName, document, initialBalance, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Account> list() {
        return accounts.findAll();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> statement(UUID accountId, Pageable pageable) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return ledger.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    // ------------------------------------------------------------------ passos da Saga

    /** Passo 1 da Saga: retira o valor da conta de origem. */
    @Transactional
    public OperationResult debit(UUID transferId, UUID accountId, BigDecimal amount) {
        if (alreadyApplied(transferId, accountId, LedgerEntryType.DEBIT)) {
            return OperationResult.success();
        }
        if (!accounts.existsById(accountId)) {
            return OperationResult.failure(FailureReason.ACCOUNT_NOT_FOUND);
        }
        if (accounts.debit(accountId, amount, clock.instant()) == 0) {
            return OperationResult.failure(FailureReason.INSUFFICIENT_FUNDS);
        }
        ledger.save(new LedgerEntry(accountId, transferId, LedgerEntryType.DEBIT, amount, clock.instant()));
        return OperationResult.success();
    }

    /** Passo 2 da Saga: deposita o valor na conta de destino. */
    @Transactional
    public OperationResult credit(UUID transferId, UUID accountId, BigDecimal amount) {
        return addFunds(transferId, accountId, amount, LedgerEntryType.CREDIT);
    }

    /** Compensação: devolve o valor debitado à conta de origem. */
    @Transactional
    public OperationResult refund(UUID transferId, UUID accountId, BigDecimal amount) {
        return addFunds(transferId, accountId, amount, LedgerEntryType.REFUND);
    }

    private OperationResult addFunds(UUID transferId, UUID accountId, BigDecimal amount, LedgerEntryType type) {
        if (alreadyApplied(transferId, accountId, type)) {
            return OperationResult.success();
        }
        if (accounts.credit(accountId, amount, clock.instant()) == 0) {
            return OperationResult.failure(FailureReason.ACCOUNT_NOT_FOUND);
        }
        ledger.save(new LedgerEntry(accountId, transferId, type, amount, clock.instant()));
        return OperationResult.success();
    }

    private boolean alreadyApplied(UUID transferId, UUID accountId, LedgerEntryType type) {
        boolean applied = ledger.existsByTransferIdAndAccountIdAndEntryType(transferId, accountId, type);
        if (applied) {
            log.info("{} da transferência {} já aplicado na conta {} (mensagem duplicada ignorada)",
                    type, transferId, accountId);
        }
        return applied;
    }
}
