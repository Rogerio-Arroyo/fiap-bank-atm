package com.fiap.bank.atm.application.exception;

// Traducao da com.fiap.bank.atm.domain.exception.InvalidPinException para
// fora do dominio (ver AccountBlockedException nesta mesma pasta).
public class InvalidPinException extends RuntimeException {
    public InvalidPinException(String message) {
        super(message);
    }
}
