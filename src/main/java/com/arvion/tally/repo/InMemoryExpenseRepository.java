package com.arvion.tally.repo;

import com.arvion.tally.domain.Expense;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory repository. Methods are synchronized because the HTTP server is threaded. */
public class InMemoryExpenseRepository implements ExpenseRepository {
    private final Map<String, Expense> expenses = new LinkedHashMap<>();

    @Override
    public synchronized Expense save(Expense expense) {
        expenses.put(expense.id(), expense);
        return expense;
    }

    @Override
    public synchronized Optional<Expense> findById(String id) {
        return Optional.ofNullable(expenses.get(id));
    }

    @Override
    public synchronized List<Expense> findAll() {
        return new ArrayList<>(expenses.values());
    }

    public synchronized int count() {
        return expenses.size();
    }

    public synchronized void clear() {
        expenses.clear();
    }
}
