package com.digitalbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.digitalbank.account.domain.Account;
import com.digitalbank.account.domain.DuplicateDocumentException;
import com.digitalbank.account.domain.LedgerEntry;
import com.digitalbank.account.domain.LedgerEntryType;
import com.digitalbank.account.domain.OperationResult;
import com.digitalbank.account.domain.OperationResult.FailureReason;
import com.digitalbank.account.repository.AccountRepository;
import com.digitalbank.account.repository.LedgerEntryRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID TRANSFER = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");

    @Mock
    AccountRepository accounts;
    @Mock
    LedgerEntryRepository ledger;

    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, ledger, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void debitSuccessWritesLedgerEntry() {
        when(accounts.existsById(ACCOUNT)).thenReturn(true);
        when(accounts.debit(ACCOUNT, AMOUNT, NOW)).thenReturn(1);

        var result = service.debit(TRANSFER, ACCOUNT, AMOUNT);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(captor.getValue().getTransferId()).isEqualTo(TRANSFER);
    }

    @Test
    void debitWithInsufficientFundsFailsWithoutLedgerEntry() {
        when(accounts.existsById(ACCOUNT)).thenReturn(true);
        when(accounts.debit(ACCOUNT, AMOUNT, NOW)).thenReturn(0);

        var result = service.debit(TRANSFER, ACCOUNT, AMOUNT);

        assertThat(result).isEqualTo(OperationResult.failure(FailureReason.INSUFFICIENT_FUNDS));
        verify(ledger, never()).save(any());
    }

    @Test
    void debitOnUnknownAccountFails() {
        when(accounts.existsById(ACCOUNT)).thenReturn(false);

        var result = service.debit(TRANSFER, ACCOUNT, AMOUNT);

        assertThat(result).isEqualTo(OperationResult.failure(FailureReason.ACCOUNT_NOT_FOUND));
        verify(accounts, never()).debit(any(), any(), any());
    }

    @Test
    void duplicatedDebitIsIdempotent() {
        when(ledger.existsByTransferIdAndAccountIdAndEntryType(TRANSFER, ACCOUNT, LedgerEntryType.DEBIT))
                .thenReturn(true);

        var result = service.debit(TRANSFER, ACCOUNT, AMOUNT);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(accounts, never()).debit(any(), any(), any());
        verify(ledger, never()).save(any());
    }

    @Test
    void creditOnUnknownAccountFails() {
        when(accounts.credit(ACCOUNT, AMOUNT, NOW)).thenReturn(0);

        var result = service.credit(TRANSFER, ACCOUNT, AMOUNT);

        assertThat(result).isEqualTo(OperationResult.failure(FailureReason.ACCOUNT_NOT_FOUND));
        verify(ledger, never()).save(any());
    }

    @Test
    void refundCreditsSourceAccountWithRefundEntry() {
        when(accounts.credit(ACCOUNT, AMOUNT, NOW)).thenReturn(1);

        var result = service.refund(TRANSFER, ACCOUNT, AMOUNT);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.REFUND);
    }

    @Test
    void duplicatedRefundIsIdempotent() {
        when(ledger.existsByTransferIdAndAccountIdAndEntryType(TRANSFER, ACCOUNT, LedgerEntryType.REFUND))
                .thenReturn(true);

        assertThat(service.refund(TRANSFER, ACCOUNT, AMOUNT)).isInstanceOf(OperationResult.Success.class);
        verify(accounts, never()).credit(any(), any(), any());
    }

    @Test
    void openRejectsDuplicatedDocument() {
        when(accounts.existsByDocument("123")).thenReturn(true);

        assertThatThrownBy(() -> service.open("Fulano", "123", "segredo1", BigDecimal.TEN))
                .isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void openPersistsAccountWithScaledBalance() {
        when(accounts.existsByDocument("123")).thenReturn(false);
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        var account = service.open(" Fulano ", "123", "segredo1", new BigDecimal("10"));

        assertThat(account.getOwnerName()).isEqualTo("Fulano");
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("10.00"));
        // a senha nunca é guardada em claro
        assertThat(account.getPasswordHash()).isNotEqualTo("segredo1");
        assertThat(passwordEncoder.matches("segredo1", account.getPasswordHash())).isTrue();
        verify(accounts).save(eq(account));
    }
}
