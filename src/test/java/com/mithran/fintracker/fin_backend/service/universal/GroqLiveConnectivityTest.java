package com.mithran.fintracker.fin_backend.service.universal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Live connectivity test for Groq API.
 * Only runs when explicitly requested via -Drun.live.groq.tests=true.
 * Does NOT expose financial data or API key.
 */
@EnabledIfSystemProperty(named = "run.live.groq.tests", matches = "true")
public class GroqLiveConnectivityTest {

    private GroqLlmClient createClient() throws IOException {
        // Read API key from application.properties
        Path propsPath = Path.of("src/main/resources/application.properties");
        String content = Files.readString(propsPath);
        
        String apiKey = null;
        for (String line : content.split("\n")) {
            if (line.trim().startsWith("gemini.api.key=")) {
                apiKey = line.substring(line.indexOf('=') + 1).trim();
                break;
            }
        }
        
        GroqLlmClient client = new GroqLlmClient();
        // Use reflection to set the private apiKey field
        try {
            var field = GroqLlmClient.class.getDeclaredField("apiKey");
            field.setAccessible(true);
            field.set(client, apiKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject API key", e);
        }
        
        return client;
    }

    @Test
    void testGroqClientAvailability() throws IOException {
        GroqLlmClient client = createClient();
        boolean available = client.isAvailable();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("GROQ API CONNECTIVITY TEST");
        System.out.println("=".repeat(80));
        System.out.println("API Client Available: " + (available ? "✅ YES" : "❌ NO"));
        
        if (!available) {
            System.out.println("\n⚠️ Groq API key not configured");
            System.out.println("   Set gemini.api.key in src/main/resources/application.properties");
            System.out.println("   Skipping live connectivity test");
        }
    }

    @Test
    void testSimpleConnectivity() throws Exception {
        GroqLlmClient client = createClient();
        
        if (!client.isAvailable()) {
            System.out.println("⚠️ Skipping connectivity test - API key not configured");
            return;
        }

        // Minimal non-financial test data
        String testText = """
            Test Bank Statement
            Statement Period: January 2024
            Account: ****1234
            
            Date        Description              Debit    Credit   Balance
            2024-01-15  Office Supplies          50.00             950.00
            2024-01-16  Salary Deposit                    2000.00  2950.00
            2024-01-17  Utilities Payment        100.00            2850.00
            
            Closing Balance: 2850.00
            """;

        System.out.println("\n" + "=".repeat(80));
        System.out.println("GROQ API LIVE REQUEST TEST");
        System.out.println("=".repeat(80));
        System.out.println("Sending test statement to Groq API...");
        System.out.println("Model: llama-3.3-70b-versatile");
        System.out.println("Statement length: " + testText.length() + " characters");
        
        long startTime = System.currentTimeMillis();
        
        try {
            String response = client.requestStructuredParse(testText);
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.println("\n✅ Groq API responded successfully");
            System.out.println("   Response time: " + duration + "ms");
            System.out.println("   Response length: " + (response != null ? response.length() : 0) + " characters");
            
            if (response != null && !response.trim().isEmpty()) {
                System.out.println("\n📄 Response preview (first 500 chars):");
                System.out.println(response.substring(0, Math.min(500, response.length())));
                if (response.length() > 500) {
                    System.out.println("... [truncated]");
                }
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            System.err.println("\n❌ Groq API connectivity failed after " + duration + "ms");
            System.err.println("   Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.err.println("   Check API key validity and network connection");
            throw e;
        }
        
        System.out.println("\n" + "=".repeat(80));
    }
}
