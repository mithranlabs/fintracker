package com.mithran.fintracker.fin_backend.service.universal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Debug test to understand why fixtures are failing.
 */
class FixtureDebugTest {

    private UniversalStatementParserService parser;

    @BeforeEach
    void setup() {
        UniversalStatementLlmClient mockLlmClient = new UniversalStatementLlmClient() {
            @Override
            public String requestStructuredParse(String statementText) {
                return null; // Force heuristic parsing
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        parser = new UniversalStatementParserService(mockLlmClient);
    }

    private String loadFixture(String filename) throws IOException {
        return Files.readString(Paths.get("src/test/resources/sample_statements/" + filename));
    }

    @Test
    void debugMultilineNarration() throws IOException {
        String text = loadFixture("multiline_narration_statement.txt");
        System.out.println("=== Multiline Narration Statement ===");
        System.out.println("Text length: " + text.length());
        System.out.println("First 500 chars:\n" + text.substring(0, Math.min(500, text.length())));
        
        UniversalStatementParseResult result = parser.parseText(text);
        System.out.println("\nResult:");
        System.out.println("  Success: " + result.isSuccessful());
        System.out.println("  Transactions: " + result.getTransactions().size());
        System.out.println("  Confidence: " + result.getOverallConfidence());
        System.out.println("  Method: " + result.getDetectionMethod());
        System.out.println("  Warnings: " + result.getWarnings().size());
        
        for (String warning : result.getWarnings()) {
            System.out.println("    - " + warning);
        }
        
        for (int i = 0; i < Math.min(3, result.getTransactions().size()); i++) {
            UniversalStatementTransaction txn = result.getTransactions().get(i);
            System.out.println("  Txn " + (i+1) + ": " + txn.getTransactionDate() + " | " + 
                             txn.getAmount() + " | " + txn.getDirection());
        }
    }

    @Test
    void debugHybridSingleMultiline() throws IOException {
        String text = loadFixture("hybrid_single_multiline.txt");
        System.out.println("\n=== Hybrid Single+Multiline Statement ===");
        System.out.println("Text length: " + text.length());
        
        UniversalStatementParseResult result = parser.parseText(text);
        System.out.println("\nResult:");
        System.out.println("  Success: " + result.isSuccessful());
        System.out.println("  Transactions: " + result.getTransactions().size() + " (expected 5)");
        System.out.println("  Confidence: " + result.getOverallConfidence());
        
        for (UniversalStatementTransaction txn : result.getTransactions()) {
            System.out.println("  " + txn.getTransactionDate() + " | " + txn.getAmount() + " | " + txn.getDirection() + " | " + 
                             (txn.getNarration() != null ? txn.getNarration().substring(0, Math.min(30, txn.getNarration().length())) : "null"));
        }
    }

    @Test
    void debugIndianFormatting() throws IOException {
        String text = loadFixture("indian_formatting_statement.txt");
        System.out.println("\n=== Indian Formatting Statement ===");
        
        UniversalStatementParseResult result = parser.parseText(text);
        System.out.println("\nResult:");
        System.out.println("  Success: " + result.isSuccessful());
        System.out.println("  Transactions: " + result.getTransactions().size() + " (expected 6)");
        
        for (UniversalStatementTransaction txn : result.getTransactions()) {
            System.out.println("  " + txn.getTransactionDate() + " | " + txn.getAmount() + " | " + 
                             (txn.getNarration() != null ? txn.getNarration().substring(0, Math.min(40, txn.getNarration().length())) : "null"));
        }
    }

    @Test
    void debugMalformed() throws IOException {
        String text = loadFixture("malformed_ambiguous_statement.txt");
        System.out.println("\n=== Malformed Statement ===");
        
        UniversalStatementParseResult result = parser.parseText(text);
        System.out.println("\nResult:");
        System.out.println("  Success: " + result.isSuccessful());
        System.out.println("  Transactions: " + result.getTransactions().size() + " (expected 2-4)");
        System.out.println("  Warnings: " + result.getWarnings().size());
        
        for (UniversalStatementTransaction txn : result.getTransactions()) {
            System.out.println("  " + txn.getTransactionDate() + " | " + txn.getAmount() + " | " + txn.getDirection());
        }
    }

    @Test
    void debugMultipleSameDate() throws IOException {
        String text = loadFixture("multiple_txns_same_date_multiline.txt");
        System.out.println("\n=== Multiple Same Date Multiline ===");
        
        UniversalStatementParseResult result = parser.parseText(text);
        System.out.println("\nResult:");
        System.out.println("  Success: " + result.isSuccessful());
        System.out.println("  Transactions: " + result.getTransactions().size() + " (expected 5)");
        System.out.println("  Confidence: " + result.getOverallConfidence());
        
        for (UniversalStatementTransaction txn : result.getTransactions()) {
            System.out.println("  " + txn.getTransactionDate() + " | " + txn.getAmount() + " | " + txn.getDirection());
        }
    }
}
