package com.digitalbank.notification.infrastructure.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/** Conta do usuário logado, extraída do "sub" do JWT. */
public final class CurrentAccount {

    private CurrentAccount() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Garante que o recurso pedido pertence à conta logada; caso contrário responde 403. */
    public static UUID requireOwner(Jwt jwt, UUID accountId) {
        UUID me = id(jwt);
        if (accountId != null && !me.equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A conta informada não pertence ao usuário logado");
        }
        return me;
    }
}
