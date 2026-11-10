package com.digitalbank.account.domain.exception;

public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(String document) {
        super("Já existe uma conta para o documento " + document);
    }
}
