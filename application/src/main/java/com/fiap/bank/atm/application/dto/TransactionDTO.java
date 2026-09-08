package com.fiap.bank.atm.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Record (Java 21): contrato imutável de uma transação para fora do domínio.
// "type" já vem como descrição pronta (ex.: "Saque"), para a apresentação
// não precisar conhecer o enum TransactionType do pacote de domínio.
public record TransactionDTO(
        UUID id,
        String type,
        BigDecimal amount,
        LocalDateTime timestamp,
        String description) {
}
