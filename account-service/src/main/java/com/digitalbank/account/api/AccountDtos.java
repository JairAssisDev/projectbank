package com.digitalbank.account.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.digitalbank.account.domain.Account;
import com.digitalbank.account.domain.LedgerEntry;
import com.digitalbank.account.domain.LedgerEntryType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @Schema(example = "Maria Silva") @NotBlank @Size(max = 120) String ownerName,
            @Schema(example = "12345678901") @NotBlank @Size(max = 20) String document,
            @Schema(example = "1000.00") @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
            BigDecimal initialBalance) {
    }

    public record AccountResponse(UUID id, String ownerName, String document, BigDecimal balance,
                                  Instant createdAt, Instant updatedAt) {

        static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getOwnerName(), a.getDocument(), a.getBalance(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    public record StatementEntryResponse(UUID id, UUID transferId, LedgerEntryType type, BigDecimal amount,
                                         Instant createdAt) {

        static StatementEntryResponse from(LedgerEntry e) {
            return new StatementEntryResponse(e.getId(), e.getTransferId(), e.getEntryType(), e.getAmount(),
                    e.getCreatedAt());
        }
    }
}
