package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import com.mithran.fintracker.fin_backend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@RestController
@RequestMapping("/insights")
public class InsightsController {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    private String cachedInsight = null;
    private long cacheTimestamp = 0;

    private static final long CACHE_DURATION_MS = 2 * 60 * 1000;

    @Value("${groq.api.key:${gemini.api.key:}}")
    private String geminiApiKey;

    public InsightsController(
            TransactionRepository transactionRepository,
            UserRepository userRepository) {

        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getInsights(HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity
                    .status(401)
                    .body("Not logged in");
        }

        User user = userRepository
                .findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(404)
                    .body("User not found");
        }

        List<Transaction> all =
                transactionRepository.findByUser(user);

        if (all.isEmpty()) {
            return ResponseEntity.ok(
                    Map.of(
                            "insight",
                            "No transactions found. Upload a statement to get insights."
                    )
            );
        }

        // ---------------------------------------------------------
        // Calculate spending
        // ---------------------------------------------------------

        Map<String, Double> categorySpend =
                new LinkedHashMap<>();

        double totalIncome = 0;
        double totalExpense = 0;

        Map<String, Double> merchantSpend =
                new HashMap<>();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);

        Date thirtyDaysAgo = cal.getTime();

        for (Transaction t : all) {

            if (t.getDate() == null ||
                    t.getDate().before(thirtyDaysAgo)) {
                continue;
            }

            if ("income".equals(t.getType())) {

                totalIncome += t.getAmount();

            } else {

                totalExpense += t.getAmount();

                String cat =
                        t.getCategory() != null
                                ? t.getCategory().getName()
                                : "Uncategorized";

                categorySpend.merge(
                        cat,
                        t.getAmount(),
                        Double::sum
                );

                String merchant =
                        t.getNote() != null
                                ? t.getNote()
                                : "Unknown";

                if (merchant.startsWith("Paid to ")) {
                    merchant = merchant.substring(8);
                }

                merchantSpend.merge(
                        merchant,
                        t.getAmount(),
                        Double::sum
                );
            }
        }

        // ---------------------------------------------------------
        // Top merchant
        // ---------------------------------------------------------

        String topMerchant =
                merchantSpend.entrySet()
                        .stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("N/A");

        // ---------------------------------------------------------
        // Build prompt
        // ---------------------------------------------------------

        StringBuilder prompt = new StringBuilder();

        prompt.append(
                "You are a personal finance advisor. " +
                        "Analyze this user's spending and give 3-4 short, " +
                        "friendly, actionable insights.\n\n"
        );

        prompt.append("Last 30 days summary:\n");

        prompt.append("- Total Income: ₹")
                .append(String.format("%.2f", totalIncome))
                .append("\n");

        prompt.append("- Total Expense: ₹")
                .append(String.format("%.2f", totalExpense))
                .append("\n");

        prompt.append("- Net Savings: ₹")
                .append(String.format(
                        "%.2f",
                        totalIncome - totalExpense
                ))
                .append("\n");

        prompt.append("- Top merchant: ")
                .append(topMerchant)
                .append("\n");

        prompt.append("- Spending by category:\n");

        categorySpend.forEach((cat, amt) ->
                prompt.append("  * ")
                        .append(cat)
                        .append(": ₹")
                        .append(String.format("%.2f", amt))
                        .append("\n")
        );

        prompt.append(
                "\nKeep response concise, use bullet points, " +
                        "and mention specific numbers.\n" +
                        "Return only the final user-facing insights. " +
                        "Do not include reasoning or internal analysis."
        );

        // ---------------------------------------------------------
        // Call Groq
        // ---------------------------------------------------------

        try {

            String urlStr =
                    "https://api.groq.com/openai/v1/chat/completions";

            System.out.println(
                    ">>> CALLING GROQ API NOW <<<"
            );

            URL url = new URL(urlStr);

            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");

            conn.setRequestProperty(
                    "Content-Type",
                    "application/json"
            );

            conn.setRequestProperty(
                    "Authorization",
                    "Bearer " + geminiApiKey
            );

            conn.setDoOutput(true);

            // -----------------------------------------------------
            // Minimal JSON escaping
            // -----------------------------------------------------

            String safePrompt = prompt.toString()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "")
                    .replace("\n", "\\n");

            String requestBody =
                    "{\"model\":\"openai/gpt-oss-20b\"," +
                            "\"messages\":[{" +
                            "\"role\":\"user\"," +
                            "\"content\":\"" +
                            safePrompt +
                            "\"}]}";

            try (OutputStream os = conn.getOutputStream()) {

                os.write(requestBody.getBytes("UTF-8"));
            }

            // -----------------------------------------------------
            // Response
            // -----------------------------------------------------

            int status = conn.getResponseCode();

            System.out.println(
                    ">>> GROQ RESPONSE STATUS: " + status
            );

            InputStream stream;

            if (status >= 200 && status < 300) {
                stream = conn.getInputStream();
            } else {
                stream = conn.getErrorStream();
            }

            String response =
                    stream != null
                            ? new String(
                            stream.readAllBytes(),
                            "UTF-8"
                    )
                            : "";

            System.out.println(
                    ">>> GROQ RAW RESPONSE: " + response
            );

            if (status != 200) {

                System.out.println(
                        "GROQ ERROR " +
                                status +
                                ": " +
                                response
                );

                return ResponseEntity
                        .status(500)
                        .body(
                                Map.of(
                                        "insight",
                                        "API error " +
                                                status +
                                                ". Check console."
                                )
                        );
            }

            // -----------------------------------------------------
            // Extract ONLY content
            // -----------------------------------------------------

            String text = extractContent(response);

            if (text.isEmpty()) {

                return ResponseEntity
                        .status(500)
                        .body(
                                Map.of(
                                        "insight",
                                        "AI returned an empty response."
                                )
                        );
            }

            cachedInsight = text;
            cacheTimestamp =
                    System.currentTimeMillis();

            return ResponseEntity.ok(
                    Map.of("insight", text)
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "insight",
                                    "Failed to get insights. Try again."
                            )
                    );
        }
    }

    // -------------------------------------------------------------
    // Extract content from Groq JSON without adding dependencies
    // -------------------------------------------------------------

    private String extractContent(String response) {

        String marker = "\"content\":\"";

        int start = response.indexOf(marker);

        if (start == -1) {
            return "";
        }

        start += marker.length();

        StringBuilder result = new StringBuilder();

        boolean escaped = false;

        for (int i = start; i < response.length(); i++) {

            char c = response.charAt(i);

            if (escaped) {

                switch (c) {

                    case 'n':
                        result.append('\n');
                        break;

                    case 'r':
                        result.append('\r');
                        break;

                    case 't':
                        result.append('\t');
                        break;

                    case '"':
                        result.append('"');
                        break;

                    case '\\':
                        result.append('\\');
                        break;

                    default:
                        result.append(c);
                }

                escaped = false;

            } else if (c == '\\') {

                escaped = true;

            } else if (c == '"') {

                // End of content
                break;

            } else {

                result.append(c);
            }
        }

        return result.toString().trim();
    }
}