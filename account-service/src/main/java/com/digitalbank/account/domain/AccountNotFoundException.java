package com.digitalbank.account.domain;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID id) {
        super("Conta não encontrada: " + id);
    }
}
