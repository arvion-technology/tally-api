package com.arvion.tally.repo;

import com.arvion.tally.domain.Expense;
import java.util.List;
import java.util.Optional;

/**
 * Storage boundary. The API only talks to this interface, so a JDBC
 * implementation could replace the in-memory one without touching handlers.
 */
public interface ExpenseRepository {
    Expense save(Expense expense);

    Optional<Expense> findById(String id);

    List<Expense> findAll();
}
