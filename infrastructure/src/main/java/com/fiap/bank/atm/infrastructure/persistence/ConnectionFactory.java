package com.fiap.bank.atm.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// Fabrica responsavel por abrir conexoes com o arquivo SQLite e por garantir
// que o schema (tabelas + carga inicial) exista antes do primeiro uso. Quem
// chama getConnection() e responsavel por fechar a conexao (try-with-resources).
public final class ConnectionFactory {

    private static final String DATABASE_URL = "jdbc:sqlite:fiapbank.db";
    private static volatile boolean schemaInitialized = false;

    private ConnectionFactory() {
    }

    public static Connection getConnection() {
        try {
            Connection connection = DriverManager.getConnection(DATABASE_URL);
            ensureSchema(connection);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível conectar ao banco de dados SQLite.", e);
        }
    }

    private static synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaInitialized) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tb_account (
                        id VARCHAR(36) PRIMARY KEY,
                        agency VARCHAR(10) NOT NULL,
                        number VARCHAR(20) NOT NULL UNIQUE,
                        balance DECIMAL(15, 2) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        pin VARCHAR(4) NOT NULL,
                        daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
                        failed_attempts INTEGER NOT NULL DEFAULT 0
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tb_transaction (
                        id VARCHAR(36) PRIMARY KEY,
                        account_id VARCHAR(36) NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        amount DECIMAL(15, 2) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        description VARCHAR(255),
                        FOREIGN KEY (account_id) REFERENCES tb_account(id)
                    )
                    """);

            seedInitialAccounts(statement);
        }

        schemaInitialized = true;
    }

    // Mesmas 3 contas de teste que o antigo InMemoryAccountRepository criava
    // em RAM (mesmo número, PIN, saldo e limite diário), agora persistidas de
    // verdade em disco. As colunas agency/pin/daily_withdrawal_limit/
    // failed_attempts vão além do dicionário de dados mínimo do Anexo 7.2,
    // pois o domínio da conta do ATM (PIN, limite diário, tentativas falhas)
    // precisa delas para funcionar como já funcionava antes da Fase 4.
    private static void seedInitialAccounts(Statement statement) throws SQLException {
        statement.execute("""
                INSERT INTO tb_account (id, agency, number, balance, status, pin, daily_withdrawal_limit, failed_attempts)
                VALUES ('550e8400-e29b-41d4-a716-446655440000', '0001', '12345', 5000.00, 'ACTIVE', '1234', 1500.00, 0)
                ON CONFLICT (number) DO NOTHING
                """);
        statement.execute("""
                INSERT INTO tb_account (id, agency, number, balance, status, pin, daily_withdrawal_limit, failed_attempts)
                VALUES ('550e8400-e29b-41d4-a716-446655440001', '0001', '67890', 1200.00, 'ACTIVE', '5678', 1000.00, 0)
                ON CONFLICT (number) DO NOTHING
                """);
        statement.execute("""
                INSERT INTO tb_account (id, agency, number, balance, status, pin, daily_withdrawal_limit, failed_attempts)
                VALUES ('550e8400-e29b-41d4-a716-446655440002', '0002', '99999', 50.00, 'ACTIVE', '9999', 500.00, 0)
                ON CONFLICT (number) DO NOTHING
                """);
    }
}
