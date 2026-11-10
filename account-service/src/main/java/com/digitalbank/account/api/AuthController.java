package com.digitalbank.account.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.account.security.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Login do cliente")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(
            @Schema(example = "11111111111") @NotBlank String document,
            @Schema(example = "senha123") @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UUID accountId,
                                String ownerName) {
    }

    @PostMapping("/login")
    @SecurityRequirements // endpoint público
    @Operation(summary = "Login com documento e senha",
            description = "Devolve um JWT. Envie-o nos três serviços no header Authorization: Bearer TOKEN "
                    + "(no Swagger: botão Authorize).")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var token = auth.login(request.document(), request.password());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), token.account().getId(),
                token.account().getOwnerName());
    }
}
