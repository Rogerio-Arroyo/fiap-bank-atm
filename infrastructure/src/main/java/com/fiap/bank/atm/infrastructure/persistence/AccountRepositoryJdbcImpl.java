package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import com.fiap.bank.atm.domain.repository.AccountRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Implementacao concreta de AccountRepository via JDBC puro (sem ORM), usando
// exclusivamente PreparedStatement + ResultSet para evitar SQL Injection,
// conforme exigido pela Fase 4.
public class AccountRepositoryJdbcImpl implements AccountRepository {

    private static final String SELECT_ACCOUNT_COLUMNS =
            "id, number, pin, balance, daily_withdrawal_limit, status, failed_attempts ";

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT " + SELECT_ACCOUNT_COLUMNS + "FROM tb_account WHERE number = ?";
        try (Connection connection = ConnectionFactory.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapAccount(resultSet, connection));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar conta pelo número.", e);
        }
    }

    @Override
    public Optional<Account> findById(UUID id) {
        String sql = "SELECT " + SELECT_ACCOUNT_COLUMNS + "FROM tb_account WHERE id = ?";
        try (Connection connection = ConnectionFactory.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapAccount(resultSet, connection));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar conta pelo id.", e);
        }
    }

    @Override
    public List<Account> findAll() {
        String sql = "SELECT " + SELECT_ACCOUNT_COLUMNS + "FROM tb_account";
        List<Account> accounts = new ArrayList<>();
        try (Connection connection = ConnectionFactory.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                accounts.add(mapAccount(resultSet, connection));
            }
            return accounts;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao listar contas.", e);
        }
    }

    // Persiste, em uma unica transacao (setAutoCommit/commit/rollback), tanto
    // o saldo/status/tentativas atuais da conta quanto as transacoes novas do
    // seu historico (as ja existentes sao ignoradas via ON CONFLICT).
    @Override
    public void save(Account account) {
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                updateAccountRow(connection, account);
                insertNewTransactions(connection, account);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("Erro ao salvar conta e suas transações.", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao conectar ao banco de dados.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM tb_account WHERE id = ?";
        try (Connection connection = ConnectionFactory.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao remover conta.", e);
        }
    }

    private void updateAccountRow(Connection connection, Account account) throws SQLException {
        String sql = "UPDATE tb_account SET balance = ?, status = ?, failed_attempts = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, account.getBalance().getAmount());
            statement.setString(2, account.isBlocked() ? "BLOCKED" : "ACTIVE");
            statement.setInt(3, account.getFailedAttempts());
            statement.setString(4, account.getId().toString());
            statement.executeUpdate();
        }
    }

    private void insertNewTransactions(Connection connection, Account account) throws SQLException {
        String sql = """
                INSERT INTO tb_transaction (id, account_id, type, amount, created_at, description)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Transaction transaction : account.getTransactions()) {
                statement.setString(1, transaction.getId().toString());
                statement.setString(2, account.getId().toString());
                statement.setString(3, transaction.getType().name());
                statement.setBigDecimal(4, transaction.getAmount().getAmount());
                statement.setTimestamp(5, Timestamp.valueOf(transaction.getTimestamp()));
                statement.setString(6, transaction.getDescription());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Account mapAccount(ResultSet resultSet, Connection connection) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        String number = resultSet.getString("number");
        String pin = resultSet.getString("pin");
        Money balance = Money.of(resultSet.getBigDecimal("balance"));
        Money dailyWithdrawalLimit = Money.of(resultSet.getBigDecimal("daily_withdrawal_limit"));
        boolean blocked = "BLOCKED".equals(resultSet.getString("status"));
        int failedAttempts = resultSet.getInt("failed_attempts");

        List<Transaction> transactions = findTransactionsByAccountId(id, connection);

        return Account.reconstruct(id, number, pin, balance, dailyWithdrawalLimit, blocked, failedAttempts,
                transactions);
    }

    private List<Transaction> findTransactionsByAccountId(UUID accountId, Connection connection)
            throws SQLException {
        String sql = "SELECT id, type, amount, created_at, description FROM tb_transaction "
                + "WHERE account_id = ? ORDER BY created_at";
        List<Transaction> transactions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    transactions.add(new Transaction(
                            UUID.fromString(resultSet.getString("id")),
                            resultSet.getTimestamp("created_at").toLocalDateTime(),
                            TransactionType.valueOf(resultSet.getString("type")),
                            Money.of(resultSet.getBigDecimal("amount")),
                            resultSet.getString("description")));
                }
            }
        }
        return transactions;
    }
}
