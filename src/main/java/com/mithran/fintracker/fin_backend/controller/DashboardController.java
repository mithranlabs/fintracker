package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Budget;
import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.BudgetRepository;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import com.mithran.fintracker.fin_backend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public DashboardController(TransactionRepository transactionRepository,
                               BudgetRepository budgetRepository,
                               UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("User not found");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyDaysAgo = cal.getTime();
        String currentMonth = new SimpleDateFormat("yyyy-MM").format(new Date());

        List<Transaction> all = transactionRepository.findByUser(user);

        double income = 0, expense = 0;
        int count = 0;

        for (Transaction t : all) {
            if (t.getDate() == null) continue;
            if (t.getDate().before(thirtyDaysAgo)) continue; // ← last 30 days only
            count++;
            if ("income".equals(t.getType())) income += t.getAmount();
            else expense += t.getAmount();
        }


        // recent 5 transactions
        List<Map<String, Object>> recent = new ArrayList<>();
        all.sort((a, b) -> {
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            return b.getDate().compareTo(a.getDate());
        });
        SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM yyyy");
        for (int i = 0; i < Math.min(5, all.size()); i++) {
            Transaction t = all.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("note", t.getNote());
            map.put("amount", t.getAmount());
            map.put("type", t.getType());
            map.put("category", t.getCategory() != null ? t.getCategory().getName() : "—");
            map.put("date", t.getDate() != null ? displayFmt.format(t.getDate()) : "—");
            recent.add(map);
        }

        // budget health
        List<Budget> budgets = budgetRepository.findByUserAndMonth(user, currentMonth);
        List<Map<String, Object>> budgetHealth = new ArrayList<>();
        for (Budget b : budgets) {
            double spent = all.stream()
                    .filter(t -> t.getCategory() != null
                            && t.getCategory().getId() == b.getCategory().getId()
                            && "expense".equals(t.getType())
                            && t.getDate() != null
                            && new SimpleDateFormat("yyyy-MM").format(t.getDate()).equals(currentMonth))
                    .mapToDouble(Transaction::getAmount)
                    .sum();
            Map<String, Object> bmap = new LinkedHashMap<>();
            bmap.put("category", b.getCategory().getName());
            bmap.put("limit", b.getLimitAmount());
            bmap.put("spent", spent);
            double pct = b.getLimitAmount() > 0 ? (spent / b.getLimitAmount()) * 100 : 0;
            bmap.put("pct", Math.round(pct));
            budgetHealth.add(bmap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", user.getUsername());
        result.put("month", "Last 30 Days");
        result.put("income", income);
        result.put("expense", expense);
        result.put("savings", income - expense);
        result.put("count", count);
        result.put("recent", recent);
        result.put("budgets", budgetHealth);

        return ResponseEntity.ok(result);
    }
}
