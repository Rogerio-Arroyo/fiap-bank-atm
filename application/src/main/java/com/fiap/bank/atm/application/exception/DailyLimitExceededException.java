package com.fiap.bank.atm.application.exception;

// Traducao da com.fiap.bank.atm.domain.exception.DailyLimitExceededException
// para fora do dominio (ver AccountBlockedException nesta mesma pasta).
public class DailyLimitExceededException extends RuntimeException {
    public DailyLimitExceededException(String message) {
        super(message);
    }
}
