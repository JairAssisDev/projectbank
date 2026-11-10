package com.digitalbank.account.api.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @Schema(example = "11111111111") @NotBlank String document,
            @Schema(example = "senha123") @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UUID accountId,
                                String ownerName) {
    }
}
