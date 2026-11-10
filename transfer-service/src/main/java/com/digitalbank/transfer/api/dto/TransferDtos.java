package com.digitalbank.transfer.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.digitalbank.transfer.application.query.TransferView;
import com.digitalbank.transfer.domain.model.TransferStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record CreateTransferRequest(
            @Schema(description = "Opcional. A origem é sempre a conta do usuário logado; se informado, "
                    + "precisa ser a própria conta (senão 403).", nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            UUID fromAccountId,
            @Schema(example = "22222222-2222-2222-2222-222222222222") @NotNull UUID toAccountId,
            @Schema(example = "150.00") @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2)
            BigDecimal amount) {
    }

    public record TransferAcceptedResponse(UUID transferId, TransferStatus status, String statusUrl) {
    }

    public record TransferViewResponse(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                                       TransferStatus status, String failureReason, Instant createdAt,
                                       Instant updatedAt) {

        public static TransferViewResponse from(TransferView v) {
            return new TransferViewResponse(v.getId(), v.getFromAccountId(), v.getToAccountId(), v.getAmount(),
                    v.getStatus(), v.getFailureReason(), v.getCreatedAt(), v.getUpdatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages());
        }
    }
}
