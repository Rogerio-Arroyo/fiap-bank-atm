package com.fiap.bank.atm.application.exception;

// Traducao da com.fiap.bank.atm.domain.exception.AccountBlockedException para
// fora do dominio. A presentation depende so de application, entao nunca pode
// enxergar a excecao original do pacote domain.
public class AccountBlockedException extends RuntimeException {
    public AccountBlockedException(String message) {
        super(message);
    }
}
