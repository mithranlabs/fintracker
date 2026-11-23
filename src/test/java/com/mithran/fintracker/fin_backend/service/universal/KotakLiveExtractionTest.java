package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Live LLM extraction test for Kotak bank statement.
 * ONLY runs if explicitly requested via -Drun.live.groq.tests=true.
 * Does NOT save to database - inspection only.
 */
@EnabledIfSystemProperty(named = "run.live.groq.tests", matches = "true")
public class KotakLiveExtractionTest {

    private static final String KOTAK_PDF = "bank_stmnt/kotak_chandan.pdf";

    private GroqLlmClient createLlmClient() throws IOException {
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
    void testKotakStatementWithLiveLlm() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("KOTAK STATEMENT - LIVE LLM EXTRACTION TEST");
        System.out.println("=".repeat(80));

        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("⚠️ PDF not found: " + KOTAK_PDF);
            System.out.println("   Skipping live extraction test");
            return;
        }

        GroqLlmClient llmClient = createLlmClient();
        if (!llmClient.isAvailable()) {
            System.out.println("⚠️ Groq API key not configured");
            System.out.println("   Skipping live extraction test");
            return;
        }

        // Extract PDF text
        String extractedText;
        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(doc);
            System.out.println("✅ PDF loaded successfully");
            System.out.println("   Pages: " + doc.getNumberOfPages());
            System.out.println("   Extracted text length: " + extractedText.length() + " characters");
        }

        // Parse with Universal Parser (LLM-first)
        UniversalStatementParserService parser = new UniversalStatementParserService(llmClient);
        
        System.out.println("\n📤 Sending to Groq LLM for extraction...");
        System.out.println("   Model: openai/gpt-oss-20b");
        long startTime = System.currentTimeMillis();
        
        UniversalStatementParseResult result = parser.parseText(extractedText);
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✅ Parsing completed in " + duration + "ms");

        // Report results
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EXTRACTION RESULTS");
        System.out.println("=".repeat(80));
        System.out.println("Detection Method: " + result.getDetectionMethod());
        System.out.println("Success: " + result.isSuccessful());
        System.out.println("Transactions Found: " + result.getTransactions().size());
        System.out.println("Overall Confidence: " + String.format("%.2f", result.getOverallConfidence()));
        System.out.println("Warnings: " + result.getWarnings().size());

        if (!result.getWarnings().isEmpty()) {
            System.out.println("\n⚠️ WARNINGS:");
            for (String warning : result.getWarnings()) {
                System.out.println("   - " + warning);
            }
        }

        if (result.getTransactions().isEmpty()) {
            System.out.println("\n❌ NO TRANSACTIONS EXTRACTED");
        } else {
            System.out.println("\n📊 TRANSACTION DETAILS:");
            System.out.println(String.format("%-12s %-40s %-12s %-10s %-12s", 
                "DATE", "NARRATION", "AMOUNT", "TYPE", "BALANCE"));
            System.out.println("-".repeat(90));
            
            List<UniversalStatementTransaction> transactions = result.getTransactions();
            
            // Show first 10 transactions
            int displayCount = Math.min(10, transactions.size());
            for (int i = 0; i < displayCount; i++) {
                UniversalStatementTransaction txn = transactions.get(i);
                String narration = txn.getNarration();
                if (narration.length() > 38) {
                    narration = narration.substring(0, 35) + "...";
                }
                System.out.println(String.format("%-12s %-40s %12s %-10s %12s",
                    txn.getTransactionDate(),
                    narration,
                    txn.getAmount(),
                    txn.getDirection(),
                    txn.getAccountBalance() != null ? txn.getAccountBalance().toString() : "N/A"
                ));
            }
            
            if (transactions.size() > displayCount) {
                System.out.println("... and " + (transactions.size() - displayCount) + " more transactions");
            }

            // Verify data integrity
            System.out.println("\n" + "=".repeat(80));
            System.out.println("DATA INTEGRITY CHECKS");
            System.out.println("=".repeat(80));
            
            int nullDates = 0, nullAmounts = 0, nullDirections = 0, nullNarrations = 0;
            int zeroAmounts = 0;
            
            for (UniversalStatementTransaction txn : transactions) {
                if (txn.getTransactionDate() == null) nullDates++;
                if (txn.getAmount() == null) nullAmounts++;
                else if (txn.getAmount().compareTo(java.math.BigDecimal.ZERO) == 0) zeroAmounts++;
                if (txn.getDirection() == null) nullDirections++;
                if (txn.getNarration() == null || txn.getNarration().trim().isEmpty()) nullNarrations++;
            }
            
            System.out.println("Null dates: " + nullDates);
            System.out.println("Null amounts: " + nullAmounts);
            System.out.println("Zero amounts: " + zeroAmounts);
            System.out.println("Null directions: " + nullDirections);
            System.out.println("Empty narrations: " + nullNarrations);
            
            boolean passed = nullDates == 0 && nullAmounts == 0 && nullDirections == 0;
            System.out.println("\nIntegrity Check: " + (passed ? "✅ PASSED" : "❌ FAILED"));
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST COMPLETE - NO DATA SAVED TO DATABASE");
        System.out.println("=".repeat(80));
    }
}
