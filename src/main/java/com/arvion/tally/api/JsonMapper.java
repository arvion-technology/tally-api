package com.arvion.tally.api;

import com.arvion.tally.domain.CategoryTotal;
import com.arvion.tally.domain.Expense;
import com.arvion.tally.domain.MonthlyTotal;
import com.arvion.tally.domain.Summary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps domain records to the JSON shapes the HTTP handlers send. */
public final class JsonMapper {
    private JsonMapper() {
    }

    public static Map<String, Object> expense(Expense expense) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", expense.id());
        json.put("employee", expense.employee());
        json.put("description", expense.description());
        json.put("category", expense.category().wire());
        json.put("amount", expense.amount());
        json.put("date", expense.date().toString());
        json.put("status", expense.status().wire());
        json.put("submittedAt", expense.submittedAt().toString());
        json.put("decidedAt", expense.decidedAt() == null ? null : expense.decidedAt().toString());
        json.put("decisionNote", expense.decisionNote());
        return json;
    }

    public static Map<String, Object> expenseList(List<Expense> expenses) {
        List<Object> items = new ArrayList<>();
        for (Expense expense : expenses) {
            items.add(expense(expense));
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("items", items);
        json.put("count", items.size());
        return json;
    }

    public static Map<String, Object> monthly(List<MonthlyTotal> months) {
        List<Object> items = new ArrayList<>();
        long total = 0;
        for (MonthlyTotal month : months) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("month", month.month());
            json.put("count", month.count());
            json.put("total", month.total());
            items.add(json);
            total += month.total();
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("months", items);
        response.put("count", items.size());
        response.put("total", total);
        return response;
    }

    public static Map<String, Object> categories(List<CategoryTotal> categories) {
        List<Object> items = new ArrayList<>();
        long total = 0;
        for (CategoryTotal category : categories) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("category", category.category().wire());
            json.put("count", category.count());
            json.put("total", category.total());
            json.put("percent", category.percent());
            items.add(json);
            total += category.total();
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("categories", items);
        response.put("count", items.size());
        response.put("total", total);
        return response;
    }

    public static Map<String, Object> summary(Summary summary) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("submitted", summary.submitted());
        counts.put("approved", summary.approved());
        counts.put("rejected", summary.rejected());
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("total", summary.total());
        json.put("counts", counts);
        json.put("approvedTotal", summary.approvedTotal());
        json.put("pendingTotal", summary.pendingTotal());
        return json;
    }
}
