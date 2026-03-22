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
    private String cachedInsight = null;
    private long cacheTimestamp = 0;
    private static final long CACHE_DURATION_MS = 2 * 60 * 1000;

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
            String urlStr = "https://api.groq.com/openai/v1/chat/completions";
            System.out.println(">>> CALLING GROQ API NOW <<<");

            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + geminiApiKey);
            conn.setDoOutput(true);

            String safePrompt = prompt.toString().replace("\"", "'").replace("\n", "\\n").replace("\r", "");
            String requestBody = "{\"model\":\"llama-3.1-8b-instant\",\"messages\":[{\"role\":\"user\",\"content\":\"" + safePrompt + "\"}]}";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes("UTF-8"));
            }

            int status = conn.getResponseCode();
            System.out.println(">>> GROQ RESPONSE STATUS: " + status);

            if (status != 200) {
                java.io.InputStream err = conn.getErrorStream();
                String errBody = err != null ? new String(err.readAllBytes(), "UTF-8") : "no error body";
                System.out.println("GROQ ERROR " + status + ": " + errBody);
                return ResponseEntity.status(500).body(Map.of("insight", "API error " + status + ". Check console."));
            }

            java.io.InputStream is = conn.getInputStream();
            String response = new String(is.readAllBytes(), "UTF-8");
            is.close();
            System.out.println(">>> GROQ RAW RESPONSE: " + response);

            // parse: {"choices":[{"message":{"content":"..."}}]}
            // parse Groq response
            int contentStart = response.indexOf("\"content\":\"") + 11;
            String text = response.substring(contentStart);

// find the end - content ends before the next key
            int endIndex = text.indexOf("\",\"role\"");
            if (endIndex == -1) endIndex = text.indexOf("\"}");
            text = text.substring(0, endIndex);

            text = text.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").trim();

            cachedInsight = text;
            cacheTimestamp = System.currentTimeMillis();

            return ResponseEntity.ok(Map.of("insight", text));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("insight", "Failed to get insights. Try again."));
        }




    }
}
