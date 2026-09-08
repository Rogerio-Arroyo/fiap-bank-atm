package com.fiap.bank.atm.application.service;

import com.fiap.bank.atm.application.dto.AccountInfoDTO;
import com.fiap.bank.atm.application.dto.TransactionDTO;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class AtmService {
    private final AccountRepository accountRepository;
    private Account currentAccount;

    public AtmService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountInfoDTO authenticate(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new com.fiap.bank.atm.application.exception.InvalidPinException(
                        "Conta não encontrada."));

        try {
            account.authenticate(pin);
            currentAccount = account;
            return toDTO(account);
        } catch (com.fiap.bank.atm.domain.exception.AccountBlockedException e) {
            accountRepository.save(account); // Persiste o bloqueio da conta
            throw new com.fiap.bank.atm.application.exception.AccountBlockedException(e.getMessage());
        } catch (com.fiap.bank.atm.domain.exception.InvalidPinException e) {
            accountRepository.save(account); // Persiste a tentativa falha
            throw new com.fiap.bank.atm.application.exception.InvalidPinException(e.getMessage());
        }
    }

    public void withdraw(BigDecimal amount) {
        ensureAuthenticated();
        try {
            currentAccount.withdraw(Money.of(amount));
        } catch (com.fiap.bank.atm.domain.exception.AccountBlockedException e) {
            throw new com.fiap.bank.atm.application.exception.AccountBlockedException(e.getMessage());
        } catch (com.fiap.bank.atm.domain.exception.InsufficientFundsException e) {
            throw new com.fiap.bank.atm.application.exception.InsufficientFundsException(e.getMessage());
        } catch (com.fiap.bank.atm.domain.exception.DailyLimitExceededException e) {
            throw new com.fiap.bank.atm.application.exception.DailyLimitExceededException(e.getMessage());
        }
        accountRepository.save(currentAccount);
    }

    public void deposit(BigDecimal amount) {
        ensureAuthenticated();
        try {
            currentAccount.deposit(Money.of(amount));
        } catch (com.fiap.bank.atm.domain.exception.AccountBlockedException e) {
            throw new com.fiap.bank.atm.application.exception.AccountBlockedException(e.getMessage());
        }
        accountRepository.save(currentAccount);
    }

    public void transfer(String targetAccountNumber, BigDecimal amount) {
        ensureAuthenticated();

        Account targetAccount = accountRepository.findByAccountNumber(targetAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada."));

        try {
            currentAccount.transfer(targetAccount, Money.of(amount));
        } catch (com.fiap.bank.atm.domain.exception.AccountBlockedException e) {
            throw new com.fiap.bank.atm.application.exception.AccountBlockedException(e.getMessage());
        } catch (com.fiap.bank.atm.domain.exception.InsufficientFundsException e) {
            throw new com.fiap.bank.atm.application.exception.InsufficientFundsException(e.getMessage());
        }

        accountRepository.save(currentAccount);
        accountRepository.save(targetAccount);
    }

    public BigDecimal getBalance() {
        ensureAuthenticated();
        return currentAccount.getBalance().getAmount();
    }

    public List<TransactionDTO> getStatement() {
        ensureAuthenticated();
        return currentAccount.getTransactions().stream()
                .map(this::toDTO)
                .toList();
    }

    // Streams API no lugar de um for clássico: ordena as transações da mais
    // recente para a mais antiga e recorta apenas as últimas "limit" (usado
    // no comprovante impresso do extrato).
    public List<TransactionDTO> getRecentTransactions(int limit) {
        ensureAuthenticated();
        return currentAccount.getTransactions().stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
                .limit(limit)
                .map(this::toDTO)
                .toList();
    }

    public void logout() {
        currentAccount = null;
    }

    public AccountInfoDTO getCurrentAccount() {
        return currentAccount != null ? toDTO(currentAccount) : null;
    }

    public boolean isAuthenticated() {
        return currentAccount != null;
    }

    private void ensureAuthenticated() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("Nenhum usuário está autenticado no momento.");
        }
    }

    private AccountInfoDTO toDTO(Account account) {
        return new AccountInfoDTO(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance().getAmount(),
                account.getDailyWithdrawalLimit().getAmount(),
                account.getTotalWithdrawnToday().getAmount(),
                account.isBlocked());
    }

    private TransactionDTO toDTO(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getType().getDescription(),
                transaction.getAmount().getAmount(),
                transaction.getTimestamp(),
                transaction.getDescription());
    }
}
