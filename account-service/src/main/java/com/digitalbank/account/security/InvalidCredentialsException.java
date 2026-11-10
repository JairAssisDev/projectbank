package com.digitalbank.account.security;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Documento ou senha inválidos");
    }
}
