package com.fiap.bank.atm.domain.model;

import com.fiap.bank.atm.domain.exception.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Account extends BaseEntity {
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final String accountNumber;
    private final String pin;
    private Money balance;
    private final Money dailyWithdrawalLimit;
    private boolean blocked;
    private int failedAttempts;
    private final List<Transaction> transactions;

    public Account(UUID id, String accountNumber, String pin, Money initialBalance, Money dailyWithdrawalLimit) {
        super(id);
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        this.pin = Objects.requireNonNull(pin, "PIN cannot be null");
        this.balance = Objects.requireNonNull(initialBalance, "Initial balance cannot be null");
        this.dailyWithdrawalLimit = Objects.requireNonNull(dailyWithdrawalLimit, "Daily limit cannot be null");
        this.blocked = false;
        this.failedAttempts = 0;
        this.transactions = new ArrayList<>();
    }

    // Reconstrói uma conta já existente a partir de dados persistidos (usada
    // pela infraestrutura ao carregar do banco). Diferente do construtor
    // público - que sempre começa uma conta nova, sem bloqueio e sem
    // tentativas falhas - este método restaura o estado exatamente como
    // estava salvo, sem abrir mão do encapsulamento (nada disso vira setter
    // público).
    public static Account reconstruct(UUID id, String accountNumber, String pin, Money balance,
            Money dailyWithdrawalLimit, boolean blocked, int failedAttempts, List<Transaction> transactions) {
        Account account = new Account(id, accountNumber, pin, balance, dailyWithdrawalLimit);
        account.blocked = blocked;
        account.failedAttempts = failedAttempts;
        transactions.forEach(account::seedTransaction);
        return account;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Money getBalance() {
        return balance;
    }

    public Money getDailyWithdrawalLimit() {
        return dailyWithdrawalLimit;
    }

    // Modernização de iteração (Fase 3): em vez de manter um campo mutável
    // incrementado a cada saque, o total sacado hoje é derivado por Streams a
    // partir do próprio histórico de transações - uma única fonte de verdade.
    public Money getTotalWithdrawnToday() {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.WITHDRAWAL)
                .filter(transaction -> transaction.getTimestamp().toLocalDate().equals(LocalDate.now()))
                .map(Transaction::getAmount)
                .reduce(Money.ZERO, Money::plus);
    }

    public boolean isBlocked() {
        return blocked;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public void authenticate(String pinAttempt) {
        if (blocked) {
            throw new AccountBlockedException("Esta conta está bloqueada por excesso de tentativas de senha.");
        }

        if (!this.pin.equals(pinAttempt)) {
            failedAttempts++;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                blocked = true;
                throw new AccountBlockedException(
                        "Conta bloqueada após " + MAX_FAILED_ATTEMPTS + " tentativas incorretas.");
            }
            throw new InvalidPinException(
                    "Senha incorreta. Tentativa " + failedAttempts + " de " + MAX_FAILED_ATTEMPTS + ".");
        }

        failedAttempts = 0; // Reset attempts on successful login
    }

    public void withdraw(Money amount) {
        if (blocked) {
            throw new AccountBlockedException("Operação não permitida: conta bloqueada.");
        }

        if (amount.isLessThan(Money.of(0.01))) {
            throw new IllegalArgumentException("O valor do saque deve ser maior que zero.");
        }

        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException(
                    "Saldo insuficiente para realizar o saque. Saldo disponível: " + balance);
        }

        Money alreadyWithdrawnToday = getTotalWithdrawnToday();
        Money projectedWithdrawal = alreadyWithdrawnToday.plus(amount);
        if (projectedWithdrawal.isGreaterThan(dailyWithdrawalLimit)) {
            throw new DailyLimitExceededException("Limite diário de saque excedido. Limite restante hoje: "
                    + dailyWithdrawalLimit.minus(alreadyWithdrawnToday));
        }

        balance = balance.minus(amount);

        transactions.add(new Transaction(UUID.randomUUID(), TransactionType.WITHDRAWAL, amount, "Saque eletrônico"));
    }

    public void deposit(Money amount) {
        if (blocked) {
            throw new AccountBlockedException("Operação não permitida: conta bloqueada.");
        }

        if (amount.isLessThan(Money.of(0.01))) {
            throw new IllegalArgumentException("O valor do depósito deve ser maior que zero.");
        }

        balance = balance.plus(amount);
        transactions.add(new Transaction(UUID.randomUUID(), TransactionType.DEPOSIT, amount, "Depósito em dinheiro"));
    }

    public void transfer(Account targetAccount, Money amount) {
        if (blocked) {
            throw new AccountBlockedException("Operação não permitida: conta de origem bloqueada.");
        }

        if (targetAccount.isBlocked()) {
            throw new AccountBlockedException("Operação não permitida: conta de destino está bloqueada.");
        }

        if (amount.isLessThan(Money.of(0.01))) {
            throw new IllegalArgumentException("O valor da transferência deve ser maior que zero.");
        }

        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException("Saldo insuficiente para transferência. Saldo disponível: " + balance);
        }

        if (this.accountNumber.equals(targetAccount.getAccountNumber())) {
            throw new IllegalArgumentException("Não é possível realizar transferência para a mesma conta.");
        }

        // Debita a conta de origem
        this.balance = this.balance.minus(amount);
        this.transactions.add(new Transaction(
                UUID.randomUUID(),
                TransactionType.TRANSFER_OUT,
                amount,
                "Transf. para Conta " + targetAccount.getAccountNumber()));

        // Credita a conta de destino
        targetAccount.receiveTransfer(this, amount);
    }

    private void receiveTransfer(Account sourceAccount, Money amount) {
        this.balance = this.balance.plus(amount);
        this.transactions.add(new Transaction(
                UUID.randomUUID(),
                TransactionType.TRANSFER_IN,
                amount,
                "Transf. de Conta " + sourceAccount.getAccountNumber()));
    }

    // Adiciona uma transação já existente ao histórico, sem passar pelas
    // regras de negócio de withdraw/deposit/transfer (usado por reconstruct()
    // e por cargas de dados de teste).
    public void seedTransaction(Transaction transaction) {
        this.transactions.add(transaction);
    }
}
