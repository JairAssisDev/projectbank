package com.digitalbank.account.domain.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Documento ou senha inválidos");
    }
}
