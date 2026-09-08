package com.fiap.bank.atm.domain.repository;

import com.fiap.bank.atm.domain.model.BaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Repositorio generico padrao do dominio. O limite superior "T extends
// BaseEntity" garante que so entidades (com id, createdAt, updatedAt) possam
// ser gerenciadas por este contrato. Nenhum metodo de busca devolve null:
// tudo que pode nao existir vem envelopado em Optional.
public interface ATMRepository<T extends BaseEntity> {
    Optional<T> findById(UUID id);

    void save(T entity);

    void deleteById(UUID id);

    List<T> findAll();
}
