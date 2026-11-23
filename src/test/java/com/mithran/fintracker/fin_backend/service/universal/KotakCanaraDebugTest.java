package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * Diagnostic test to understand why Kotak and Canara statements fail.
 * Extracts text and analyzes structure without modifying original files.
 */
public class KotakCanaraDebugTest {

    private static final String KOTAK_PDF = "bank_stmnt/kotak_chandan.pdf";
    private static final String CANARA_PDF = "bank_stmnt/canara_chandhan.pdf";

    @Test
    void diagnoseKotakStatement() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("KOTAK STATEMENT DIAGNOSIS");
        System.out.println("=".repeat(80));

        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("❌ File not found: " + KOTAK_PDF);
            return;
        }

        try (PDDocument doc = PDDocument.load(file)) {
            System.out.println("✅ PDF opened successfully");
            System.out.println("   Pages: " + doc.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            System.out.println("✅ Text extracted");
            System.out.println("   Total characters: " + text.length());
            System.out.println("   Total lines: " + text.split("\\n").length);

            // Check if text is meaningful
            if (text.trim().isEmpty()) {
                System.out.println("❌ EMPTY TEXT - Likely scanned/image PDF");
                return;
            }

            // Analyze content
            String[] lines = text.split("\\r?\\n");
            int nonEmptyLines = 0;
            int linesWithDates = 0;
            int linesWithAmounts = 0;

            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    nonEmptyLines++;
                    if (trimmed.matches(".*\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{2,4}.*")) {
                        linesWithDates++;
                    }
                    if (trimmed.matches(".*\\d+\\.\\d{2}.*")) {
                        linesWithAmounts++;
                    }
                }
            }

            System.out.println("   Non-empty lines: " + nonEmptyLines);
            System.out.println("   Lines with dates: " + linesWithDates);
            System.out.println("   Lines with amounts: " + linesWithAmounts);

            // Print sanitized sample
            System.out.println("\n--- SANITIZED SAMPLE (first 30 non-empty lines) ---");
            int printed = 0;
            for (String line : lines) {
                if (printed >= 30) break;
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !containsSensitiveData(trimmed)) {
                    System.out.println(sanitizeLine(trimmed));
                    printed++;
                }
            }

            // Look for transaction patterns
            System.out.println("\n--- TRANSACTION PATTERN ANALYSIS ---");
            analyzeTransactionPatterns(lines);

        } catch (Exception e) {
            System.out.println("❌ Error processing PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void diagnoseCanaraStatement() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CANARA STATEMENT DIAGNOSIS");
        System.out.println("=".repeat(80));

        File file = new File(CANARA_PDF);
        if (!file.exists()) {
            System.out.println("❌ File not found: " + CANARA_PDF);
            return;
        }

        try (PDDocument doc = PDDocument.load(file)) {
            System.out.println("✅ PDF opened successfully");
            System.out.println("   Pages: " + doc.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            System.out.println("✅ Text extracted");
            System.out.println("   Total characters: " + text.length());
            System.out.println("   Total lines: " + text.split("\\n").length);

            if (text.trim().isEmpty()) {
                System.out.println("❌ EMPTY TEXT - Likely scanned/image PDF");
                return;
            }

            String[] lines = text.split("\\r?\\n");
            int nonEmptyLines = 0;
            int linesWithDates = 0;
            int linesWithAmounts = 0;

            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    nonEmptyLines++;
                    if (trimmed.matches(".*\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{2,4}.*")) {
                        linesWithDates++;
                    }
                    if (trimmed.matches(".*\\d+\\.\\d{2}.*")) {
                        linesWithAmounts++;
                    }
                }
            }

            System.out.println("   Non-empty lines: " + nonEmptyLines);
            System.out.println("   Lines with dates: " + linesWithDates);
            System.out.println("   Lines with amounts: " + linesWithAmounts);

            System.out.println("\n--- SANITIZED SAMPLE (first 30 non-empty lines) ---");
            int printed = 0;
            for (String line : lines) {
                if (printed >= 30) break;
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !containsSensitiveData(trimmed)) {
                    System.out.println(sanitizeLine(trimmed));
                    printed++;
                }
            }

            System.out.println("\n--- TRANSACTION PATTERN ANALYSIS ---");
            analyzeTransactionPatterns(lines);

        } catch (Exception e) {
            System.out.println("❌ Error processing PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void testParserWithKotakStatement() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TESTING UNIVERSAL PARSER WITH KOTAK STATEMENT");
        System.out.println("=".repeat(80));

        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("❌ File not found");
            return;
        }

        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            // Create parser (without Groq client for now)
            UniversalStatementLlmClient mockLlmClient = new UniversalStatementLlmClient() {
                @Override
                public boolean isAvailable() {
                    return false;
                }

                @Override
                public String requestStructuredParse(String statementText) {
                    return null;
                }
            };

            // Enable debug mode
            System.setProperty("parser.debug", "true");

            UniversalStatementParserService parser = new UniversalStatementParserService(mockLlmClient);
            UniversalStatementParseResult result = parser.parseText(text);

            System.setProperty("parser.debug", "false");

            System.out.println("Parser Results:");
            System.out.println("   Method: " + result.getDetectionMethod());
            System.out.println("   Transactions found: " + result.getTransactions().size());
            System.out.println("   Confidence: " + result.getOverallConfidence());
            System.out.println("   Warnings: " + result.getWarnings().size());

            for (String warning : result.getWarnings()) {
                System.out.println("   ⚠️ " + warning);
            }

            if (result.getTransactions().isEmpty()) {
                System.out.println("\n❌ NO TRANSACTIONS FOUND - This is the failure!");
            } else {
                System.out.println("\n✅ Transactions extracted:");
                for (UniversalStatementTransaction txn : result.getTransactions()) {
                    System.out.println("   " + txn.getTransactionDate() + " | " + 
                                     txn.getAmount() + " | " + 
                                     txn.getDirection() + " | " + 
                                     txn.getNarration().substring(0, Math.min(50, txn.getNarration().length())));
                }
            }
        }
    }

    @Test
    void testParserWithCanaraStatement() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TESTING UNIVERSAL PARSER WITH CANARA STATEMENT");
        System.out.println("=".repeat(80));

        File file = new File(CANARA_PDF);
        if (!file.exists()) {
            System.out.println("❌ File not found");
            return;
        }

        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            UniversalStatementLlmClient mockLlmClient = new UniversalStatementLlmClient() {
                @Override
                public boolean isAvailable() {
                    return false;
                }

                @Override
                public String requestStructuredParse(String statementText) {
                    return null;
                }
            };

            UniversalStatementParserService parser = new UniversalStatementParserService(mockLlmClient);
            UniversalStatementParseResult result = parser.parseText(text);

            System.out.println("Parser Results:");
            System.out.println("   Method: " + result.getDetectionMethod());
            System.out.println("   Transactions found: " + result.getTransactions().size());
            System.out.println("   Confidence: " + result.getOverallConfidence());
            System.out.println("   Warnings: " + result.getWarnings().size());

            for (String warning : result.getWarnings()) {
                System.out.println("   ⚠️ " + warning);
            }

            if (result.getTransactions().isEmpty()) {
                System.out.println("\n❌ NO TRANSACTIONS FOUND - This is the failure!");
            } else {
                System.out.println("\n✅ Transactions extracted:");
                for (UniversalStatementTransaction txn : result.getTransactions()) {
                    System.out.println("   " + txn.getTransactionDate() + " | " + 
                                     txn.getAmount() + " | " + 
                                     txn.getDirection() + " | " + 
                                     txn.getNarration().substring(0, Math.min(50, txn.getNarration().length())));
                }
            }
        }
    }

    private void analyzeTransactionPatterns(String[] lines) {
        // Look for common patterns
        boolean hasDebitCredit = false;
        boolean hasWithdrawalDeposit = false;
        boolean hasIndianFormatting = false;
        String dateFormat = "unknown";

        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("debit") || lower.contains("credit")) {
                hasDebitCredit = true;
            }
            if (lower.contains("withdrawal") || lower.contains("deposit")) {
                hasWithdrawalDeposit = true;
            }
            if (line.matches(".*\\d{1,3},\\d{2,3},\\d{3}.*")) {
                hasIndianFormatting = true;
            }

            // Detect date format
            if (line.matches(".*\\d{2}/\\d{2}/\\d{4}.*")) {
                dateFormat = "DD/MM/YYYY";
            } else if (line.matches(".*\\d{2}-\\d{2}-\\d{4}.*")) {
                dateFormat = "DD-MM-YYYY";
            }
        }

        System.out.println("Has Debit/Credit columns: " + hasDebitCredit);
        System.out.println("Has Withdrawal/Deposit columns: " + hasWithdrawalDeposit);
        System.out.println("Has Indian number formatting: " + hasIndianFormatting);
        System.out.println("Date format: " + dateFormat);
    }

    private boolean containsSensitiveData(String line) {
        // Skip lines with account numbers, names, addresses
        String lower = line.toLowerCase();
        return lower.contains("account number") ||
               lower.contains("customer id") ||
               lower.contains("address") ||
               lower.contains("mobile") ||
               lower.contains("email") ||
               line.matches(".*\\d{10,16}.*"); // Long numbers (account/card)
    }

    private String sanitizeLine(String line) {
        // Mask any remaining numbers that look like accounts
        line = line.replaceAll("\\d{10,}", "XXXX");
        // Mask PAN/Aadhaar patterns
        line = line.replaceAll("[A-Z]{5}\\d{4}[A-Z]", "PANXX");
        line = line.replaceAll("\\d{4}\\s\\d{4}\\s\\d{4}", "AADHXX");
        return line;
    }

    @Test
    void detailedKotakLineAnalysis() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DETAILED KOTAK LINE-BY-LINE ANALYSIS");
        System.out.println("=".repeat(80));

        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("❌ File not found");
            return;
        }

        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            String[] lines = text.split("\\r?\\n");

            System.out.println("Looking for lines with BOTH date AND amount...\n");

            int candidateLines = 0;
            for (int i = 0; i < Math.min(100, lines.length); i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                boolean hasDate = line.matches(".*\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{2,4}.*");
                boolean hasAmount = line.matches(".*\\d+\\.\\d{2}.*");

                if (hasDate && hasAmount) {
                    candidateLines++;
                    System.out.println("✅ Line " + i + " (HAS BOTH):");
                    System.out.println("   " + sanitizeLine(line));
                } else if (hasDate) {
                    System.out.println("📅 Line " + i + " (date only):");
                    System.out.println("   " + sanitizeLine(line));
                } else if (hasAmount && i < 50) {
                    // Only show first 50 amount lines
                    System.out.println("💰 Line " + i + " (amount only):");
                    System.out.println("   " + sanitizeLine(line));
                }
            }

            System.out.println("\n" + "=".repeat(80));
            System.out.println("SUMMARY: Found " + candidateLines + " lines with BOTH date AND amount");
            System.out.println("=".repeat(80));
        }
    }

    @Test
    void detailedCanaraLineAnalysis() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DETAILED CANARA LINE-BY-LINE ANALYSIS");
        System.out.println("=".repeat(80));

        File file = new File(CANARA_PDF);
        if (!file.exists()) {
            System.out.println("❌ File not found");
            return;
        }

        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            String[] lines = text.split("\\r?\\n");

            System.out.println("Looking for lines with BOTH date AND amount...\n");

            int candidateLines = 0;
            for (int i = 0; i < Math.min(100, lines.length); i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                boolean hasDate = line.matches(".*\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{2,4}.*");
                boolean hasAmount = line.matches(".*\\d+\\.\\d{2}.*");

                if (hasDate && hasAmount) {
                    candidateLines++;
                    System.out.println("✅ Line " + i + " (HAS BOTH):");
                    System.out.println("   " + sanitizeLine(line));
                } else if (hasDate) {
                    System.out.println("📅 Line " + i + " (date only):");
                    System.out.println("   " + sanitizeLine(line));
                } else if (hasAmount && i < 50) {
                    System.out.println("💰 Line " + i + " (amount only):");
                    System.out.println("   " + sanitizeLine(line));
                }
            }

            System.out.println("\n" + "=".repeat(80));
            System.out.println("SUMMARY: Found " + candidateLines + " lines with BOTH date AND amount");
            System.out.println("=".repeat(80));
        }
    }
}