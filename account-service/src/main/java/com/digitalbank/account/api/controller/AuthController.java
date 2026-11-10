package com.digitalbank.account.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.account.api.dto.AuthDtos.LoginRequest;
import com.digitalbank.account.api.dto.AuthDtos.LoginResponse;
import com.digitalbank.account.application.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Login do cliente")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
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
