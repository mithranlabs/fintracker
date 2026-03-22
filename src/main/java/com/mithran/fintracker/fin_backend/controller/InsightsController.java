package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import com.mithran.fintracker.fin_backend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@RestController
@RequestMapping("/insights")
public class InsightsController {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public InsightsController(TransactionRepository transactionRepository,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getInsights(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("User not found");

        List<Transaction> all = transactionRepository.findByUser(user);

        if (all.isEmpty()) return ResponseEntity.ok(Map.of("insight", "No transactions found. Upload a statement to get insights."));

        // Build spending summary per category
        Map<String, Double> categorySpend = new LinkedHashMap<>();
        double totalIncome = 0, totalExpense = 0;
        Map<String, Double> merchantSpend = new HashMap<>();

        // last 30 days
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyDaysAgo = cal.getTime();

        for (Transaction t : all) {
            if (t.getDate() == null || t.getDate().before(thirtyDaysAgo)) continue;

            if ("income".equals(t.getType())) {
                totalIncome += t.getAmount();
            } else {
                totalExpense += t.getAmount();
                String cat = t.getCategory() != null ? t.getCategory().getName() : "Uncategorized";
                categorySpend.merge(cat, t.getAmount(), Double::sum);

                String merchant = t.getNote() != null ? t.getNote() : "Unknown";
                if (merchant.startsWith("Paid to ")) merchant = merchant.substring(8);
                merchantSpend.merge(merchant, t.getAmount(), Double::sum);
            }
        }

        // top merchant
        String topMerchant = merchantSpend.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        // build prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a personal finance advisor. Analyze this user's spending and give 3-4 short, friendly, actionable insights.\n\n");
        prompt.append("Last 30 days summary:\n");
        prompt.append("- Total Income: ₹").append(String.format("%.2f", totalIncome)).append("\n");
        prompt.append("- Total Expense: ₹").append(String.format("%.2f", totalExpense)).append("\n");
        prompt.append("- Net Savings: ₹").append(String.format("%.2f", totalIncome - totalExpense)).append("\n");
        prompt.append("- Top merchant: ").append(topMerchant).append("\n");
        prompt.append("- Spending by category:\n");
        categorySpend.forEach((cat, amt) ->
                prompt.append("  * ").append(cat).append(": ₹").append(String.format("%.2f", amt)).append("\n")
        );
        prompt.append("\nKeep response concise, use bullet points, and mention specific numbers.");

        // call Gemini
        try {
            WebClient client = WebClient.create();

            String requestBody = """
                {
                    "contents": [{
                        "parts": [{
                            "text": "%s"
                        }]
                    }]
                }
                """.formatted(prompt.toString().replace("\"", "'").replace("\n", "\\n"));

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=" + geminiApiKey;


            Map<?, ?> response = client.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            // extract text from response
            List<?> candidates = (List<?>) response.get("candidates");
            Map<?, ?> first = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) first.get("content");
            List<?> parts = (List<?>) content.get("parts");
            Map<?, ?> part = (Map<?, ?>) parts.get(0);
            String text = (String) part.get("text");

            return ResponseEntity.ok(Map.of("insight", text));

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("429")) {
                return ResponseEntity.status(429).body(Map.of("insight", "⚠️ Too many requests. Please wait a moment and try again."));
            }
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("insight", "Failed to get insights. Try again."));
        }

    }
}
