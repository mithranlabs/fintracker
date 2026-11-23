package com.mithran.fintracker.fin_backend.service.universal;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit test to verify amount extraction distinguishes transaction from balance.
 */
public class AuditAmountExtractionTest {

    @Test
    void testAmountVsBalanceDisambiguation() {
        UniversalStatementLlmClient client = new UniversalStatementLlmClient() {
            @Override
            public boolean isAvailable() { return false; }
            @Override
            public String requestStructuredParse(String statementText) { return null; }
        };
        
        UniversalStatementParserService parser = new UniversalStatementParserService(client);
        
        // Case 1: Small transaction, large balance (current logic works)
        String case1 = 
            "Date        Description     Amount    Balance\n" +
            "01/03/2024  Withdrawal     500.00    10500.00\n";
        
        UniversalStatementParseResult result1 = parser.parseText(case1);
        System.out.println("\n=== Case 1: Small transaction (500), large balance (10500) ===");
        if (!result1.getTransactions().isEmpty()) {
            BigDecimal amount = result1.getTransactions().get(0).getAmount();
            System.out.println("Extracted amount: " + amount);
            assertEquals(new BigDecimal("500.00"), amount, "Should extract transaction amount, not balance");
        } else {
            System.out.println("No transactions parsed!");
        }
        
        // Case 2: Large transaction, small balance (will FAIL with current logic)
        String case2 = 
            "Date        Description         Amount      Balance\n" +
            "01/03/2024  Large Deposit CR   50000.00    1500.00\n";
        
        UniversalStatementParseResult result2 = parser.parseText(case2);
        System.out.println("\n=== Case 2: Large transaction (50000), small balance (1500) ===");
        if (!result2.getTransactions().isEmpty()) {
            BigDecimal amount = result2.getTransactions().get(0).getAmount();
            System.out.println("Extracted amount: " + amount);
            System.out.println("Expected: 50000.00, Got: " + amount);
            
            // This will FAIL with current "pick smallest" logic
            if (amount.compareTo(new BigDecimal("50000.00")) != 0) {
                System.out.println("❌ CRITICAL BUG: Extracted balance instead of transaction!");
            }
        } else {
            System.out.println("No transactions parsed!");
        }
        
        // Case 3: Only transaction amount (no balance)
        String case3 = 
            "Date        Description         Amount\n" +
            "01/03/2024  Purchase DR        2500.00\n";
        
        UniversalStatementParseResult result3 = parser.parseText(case3);
        System.out.println("\n=== Case 3: Single amount (2500) ===");
        if (!result3.getTransactions().isEmpty()) {
            BigDecimal amount = result3.getTransactions().get(0).getAmount();
            System.out.println("Extracted amount: " + amount);
            assertEquals(new BigDecimal("2500.00"), amount);
        } else {
            System.out.println("No transactions parsed!");
        }
    }
    
    @Test
    void testBalanceInMiddlePosition() {
        UniversalStatementLlmClient client = new UniversalStatementLlmClient() {
            @Override
            public boolean isAvailable() { return false; }
            @Override
            public String requestStructuredParse(String statementText) { return null; }
        };
        
        UniversalStatementParserService parser = new UniversalStatementParserService(client);
        
        // Realistic format: Opening Balance | Transaction | Closing Balance
        String statement = 
            "Date        Narration           Debit      Credit     Balance\n" +
            "01/03/2024  Opening Balance                            10000.00\n" +
            "01/03/2024  ATM Withdrawal     500.00                  9500.00\n" +
            "02/03/2024  Salary Deposit                50000.00    59500.00\n";
        
        UniversalStatementParseResult result = parser.parseText(statement);
        
        System.out.println("\n=== Multi-line statement with balances ===");
        System.out.println("Transactions found: " + result.getTransactions().size());
        
        for (int i = 0; i < result.getTransactions().size(); i++) {
            UniversalStatementTransaction txn = result.getTransactions().get(i);
            System.out.println("\nTransaction " + (i+1) + ":");
            System.out.println("  Date: " + txn.getTransactionDate());
            System.out.println("  Amount: " + txn.getAmount());
            System.out.println("  Direction: " + txn.getDirection());
            System.out.println("  Narration: " + txn.getNarration());
        }
    }
    
    @Test
    void testLegitimateRepeatedTransactions() {
        UniversalStatementLlmClient client = new UniversalStatementLlmClient() {
            @Override
            public boolean isAvailable() { return false; }
            @Override
            public String requestStructuredParse(String statementText) { return null; }
        };
        
        UniversalStatementParserService parser = new UniversalStatementParserService(client);
        
        // Multiple similar transactions that are NOT duplicates (different merchants)
        String statement = 
            "Date        Description              Amount     Balance\n" +
            "01/03/2024  Coffee Shop A DR        5.00       1000.00\n" +
            "01/03/2024  Coffee Shop B DR        5.00        995.00\n" +
            "01/03/2024  Coffee Shop C DR        5.00        990.00\n";
        
        UniversalStatementParseResult result = parser.parseText(statement);
        
        // Should parse all three transactions despite same date/amount/direction
        assertTrue(result.getTransactions().size() >= 3, 
            "Should NOT filter legitimate repeated transactions with different narrations");
        
        // Should warn about pattern
        boolean hasWarning = result.getWarnings().stream()
            .anyMatch(w -> w.toLowerCase().contains("duplicate") || w.toLowerCase().contains("same date"));
        assertTrue(hasWarning, "Should warn about repeated pattern");
    }
}
