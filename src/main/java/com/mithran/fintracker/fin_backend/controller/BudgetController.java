package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Budget;
import com.mithran.fintracker.fin_backend.entity.Category;
import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.BudgetRepository;
import com.mithran.fintracker.fin_backend.repository.CategoryRepository;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import com.mithran.fintracker.fin_backend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public BudgetController(BudgetRepository budgetRepository,
                            CategoryRepository categoryRepository,
                            TransactionRepository transactionRepository,
                            UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // GET /budgets?month=2026-03
    @GetMapping
    public ResponseEntity<?> getBudgets(@RequestParam String month, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("User not found");

        List<Budget> budgets = budgetRepository.findByUserAndMonth(user, month);
        List<Transaction> allTxns = transactionRepository.findByUser(user);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Budget b : budgets) {
            double spent = allTxns.stream()
                    .filter(t -> t.getCategory() != null
                            && t.getCategory().getId() == b.getCategory().getId()
                            && t.getType().equals("expense")
                            && t.getDate() != null
                            && t.getDate().toString().startsWith(month))
                    .mapToDouble(Transaction::getAmount)
                    .sum();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", b.getId());
            map.put("category", b.getCategory().getName());
            map.put("limitAmount", b.getLimitAmount());
            map.put("spent", spent);
            map.put("remaining", b.getLimitAmount() - spent);
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    // POST /budgets  — set or update a budget
    @PostMapping
    public ResponseEntity<?> setBudget(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("User not found");

        String month = (String) body.get("month");
        double limitAmount = Double.parseDouble(body.get("limitAmount").toString());
        int categoryId = Integer.parseInt(body.get("categoryId").toString());

        Category category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null) return ResponseEntity.status(404).body("Category not found");

        Optional<Budget> existing = budgetRepository.findByUserAndCategoryAndMonth(user, category, month);

        Budget budget = existing.orElse(new Budget());
        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonth(month);
        budget.setLimitAmount(limitAmount);
        budgetRepository.save(budget);

        return ResponseEntity.ok("Budget saved");
    }

    // DELETE /budgets/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBudget(@PathVariable int id, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        Budget budget = budgetRepository.findById(id).orElse(null);
        if (budget == null) return ResponseEntity.status(404).body("Not found");

        if (budget.getUser().getId() != userId) return ResponseEntity.status(403).body("Forbidden");

        budgetRepository.delete(budget);
        return ResponseEntity.ok("Deleted");
    }
}
