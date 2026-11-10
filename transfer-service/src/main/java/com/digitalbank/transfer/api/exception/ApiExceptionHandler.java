package com.digitalbank.transfer.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.digitalbank.transfer.domain.exception.IdempotencyConflictException;
import com.digitalbank.transfer.domain.exception.InvalidTransferException;
import com.digitalbank.transfer.domain.exception.TransferNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidTransferException.class)
    ProblemDetail invalid(InvalidTransferException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail conflict(IdempotencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ProblemDetail notFound(TransferNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
