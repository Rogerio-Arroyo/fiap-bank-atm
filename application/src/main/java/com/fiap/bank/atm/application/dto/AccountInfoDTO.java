package com.fiap.bank.atm.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Record (Java 21): contrato imutável de leitura da conta para fora do domínio.
// Nenhuma regra de negócio aqui, só os dados que a apresentação precisa exibir.
public record AccountInfoDTO(
        UUID id,
        String accountNumber,
        BigDecimal balance,
        BigDecimal dailyWithdrawalLimit,
        BigDecimal totalWithdrawnToday,
        boolean blocked) {
}
