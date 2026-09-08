package com.fiap.bank.atm.application.exception;

// Traducao da com.fiap.bank.atm.domain.exception.InsufficientFundsException
// para fora do dominio (ver AccountBlockedException nesta mesma pasta).
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
