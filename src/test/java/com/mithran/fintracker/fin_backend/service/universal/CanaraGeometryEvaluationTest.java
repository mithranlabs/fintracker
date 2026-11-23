package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cross-bank evaluation: Apply Kotak-trained geometry diagnostic to Canara statement.
 * 
 * Purpose: Measure how well the fixed geometry approach generalizes to a different bank
 * without tuning the algorithm first.
 * 
 * IMPORTANT: This test REUSES the exact logic from GeometryDiagnosticTest.java.
 * Any failures reveal limitations of the current approach, not bugs.
 */
public class CanaraGeometryEvaluationTest {

    private static final String CANARA_PDF = "bank_stmnt/canara_chandhan.pdf";
    private static final int TEST_PAGE = 0; // First page (0-indexed)
    
    // Reuse exact same classes from GeometryDiagnosticTest
    private static class ColumnBoundaries {
        double dateMinX = Double.MAX_VALUE;
        double dateMaxX = 0;
        double narrationMinX = Double.MAX_VALUE;
        double narrationMaxX = 0;
        double debitMinX = Double.MAX_VALUE;
        double debitMaxX = 0;
        double creditMinX = Double.MAX_VALUE;
        double creditMaxX = 0;
        double balanceMinX = Double.MAX_VALUE;
        double balanceMaxX = 0;
        boolean dateColumnFromHeader = false;
        String detectionMethod = "unknown";
    }
    
    private static class Token {
        String text;
        double x;
        double y;
        double width;
        double height;
        int pageNumber;
        String proposedColumn;
        
        Token(String text, double x, double y, double width, double height, int pageNumber) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.pageNumber = pageNumber;
        }
        
        @Override
        public String toString() {
            return String.format("Page %d | X: %6.2f | Y: %6.2f | %-10s | %s",
                pageNumber, x, y,
                proposedColumn != null ? proposedColumn : "UNASSIGNED", 
                text.length() > 40 ? text.substring(0, 37) + "..." : text);
        }
    }
    
    private static class TransactionRow {
        String date;
        String narration;
        BigDecimal debit;
        BigDecimal credit;
        BigDecimal balance;
        double yPosition;
        boolean dateValid = false;
        String dateValidationMessage = "";
        
        @Override
        public String toString() {
            return String.format("Date: %s | Narration: %s | Debit: %s | Credit: %s | Balance: %s%s",
                date != null ? date : "NULL",
                narration != null ? (narration.length() > 30 ? narration.substring(0, 27) + "..." : narration) : "NULL",
                debit != null ? debit.toString() : "NULL",
                credit != null ? credit.toString() : "NULL",
                balance != null ? balance.toString() : "NULL",
                !dateValid && date != null ? " [INVALID: " + dateValidationMessage + "]" : "");
        }
    }

    @Test
    void evaluateCanaraStatement() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("CROSS-BANK EVALUATION: CANARA BANK STATEMENT");
        System.out.println("Using geometry logic trained on Kotak - NO TUNING");
        System.out.println("=".repeat(100));
        
        File file = new File(CANARA_PDF);
        if (!file.exists()) {
            System.out.println("⚠️ PDF not found: " + CANARA_PDF);
            return;
        }
        
        try (PDDocument document = PDDocument.load(file)) {
            System.out.println("✅ PDF loaded: " + file.getName());
            System.out.println("   Total pages: " + document.getNumberOfPages());
            System.out.println("   Testing page: " + (TEST_PAGE + 1));
            
            // Extract tokens with coordinates
            List<Token> tokens = extractTokensWithCoordinates(document, TEST_PAGE);
            System.out.println("\n✅ Extracted " + tokens.size() + " tokens with coordinates");
            
            // EVALUATION METRIC 1: Column Headers
            System.out.println("\n" + "=".repeat(100));
            System.out.println("METRIC 1: COLUMN HEADER DETECTION");
            System.out.println("=".repeat(100));
            
            List<Token> headerCandidates = tokens.stream()
                .filter(t -> t.y < 150)
                .filter(t -> t.text.toLowerCase().matches(".*(date|description|narration|debit|credit|balance|withdrawal|deposit).*"))
                .collect(Collectors.toList());
            
            System.out.println("\nHeader tokens found: " + headerCandidates.size());
            if (!headerCandidates.isEmpty()) {
                Map<Long, List<Token>> headerByY = headerCandidates.stream()
                    .collect(Collectors.groupingBy(t -> Math.round(t.y)));
                
                for (Map.Entry<Long, List<Token>> entry : headerByY.entrySet()) {
                    System.out.println("\n  Y=" + entry.getKey() + ":");
                    entry.getValue().stream()
                        .sorted(Comparator.comparingDouble(t -> t.x))
                        .forEach(t -> System.out.println("    X=" + String.format("%.2f", t.x) + " : " + t.text));
                }
            } else {
                System.out.println("  ⚠️ No standard headers found in Y<150 region");
            }
            
            // Detect column boundaries (same logic as Kotak)
            System.out.println("\n" + "=".repeat(100));
            System.out.println("COLUMN DETECTION (Using Kotak Logic)");
            System.out.println("=".repeat(100));
            
            ColumnBoundaries bounds = detectColumns(tokens);
            assignColumnsToTokens(tokens, bounds);
            
            // EVALUATION METRIC 2: Date Column Assignment
            System.out.println("\n" + "=".repeat(100));
            System.out.println("METRIC 2: DATE COLUMN DETECTION");
            System.out.println("=".repeat(100));
            
            long dateTokens = tokens.stream().filter(t -> "DATE".equals(t.proposedColumn)).count();
            System.out.println("\nDate tokens assigned: " + dateTokens);
            System.out.println("Detection method: " + bounds.detectionMethod);
            System.out.println("Date column from header: " + bounds.dateColumnFromHeader);
            
            if (dateTokens > 0) {
                System.out.println("\nFirst 10 date tokens:");
                tokens.stream()
                    .filter(t -> "DATE".equals(t.proposedColumn))
                    .limit(10)
                    .forEach(t -> System.out.println("  " + t));
            }
            
            // EVALUATION METRIC 3: Numeric Column Detection
            System.out.println("\n" + "=".repeat(100));
            System.out.println("METRIC 3: NUMERIC COLUMN DETECTION");
            System.out.println("=".repeat(100));
            
            long debitTokens = tokens.stream().filter(t -> "DEBIT".equals(t.proposedColumn)).count();
            long creditTokens = tokens.stream().filter(t -> "CREDIT".equals(t.proposedColumn)).count();
            long balanceTokens = tokens.stream().filter(t -> "BALANCE".equals(t.proposedColumn)).count();
            
            System.out.println("\nDebit tokens: " + debitTokens);
            System.out.println("Credit tokens: " + creditTokens);
            System.out.println("Balance tokens: " + balanceTokens);
            
            if (debitTokens + creditTokens + balanceTokens == 0) {
                System.out.println("\n⚠️ WARNING: No numeric columns detected");
                System.out.println("  This may indicate:");
                System.out.println("  - Different column layout than Kotak");
                System.out.println("  - Different amount format");
                System.out.println("  - Scanned PDF without extractable text");
            }
            
            // EVALUATION METRIC 4: Transaction Row Assembly
            System.out.println("\n" + "=".repeat(100));
            System.out.println("METRIC 4: TRANSACTION ROW ASSEMBLY");
            System.out.println("=".repeat(100));
            
            List<TransactionRow> rows = assembleTransactionRows(tokens);
            System.out.println("\nAssembled rows: " + rows.size());
            
            if (rows.isEmpty()) {
                System.out.println("⚠️ No transaction rows assembled");
                System.out.println("\nDiagnostic: Checking why assembly failed...");
                
                long tokensWithDate = tokens.stream().filter(t -> "DATE".equals(t.proposedColumn)).count();
                long tokensWithAmount = tokens.stream().filter(t -> 
                    "DEBIT".equals(t.proposedColumn) || 
                    "CREDIT".equals(t.proposedColumn) || 
                    "BALANCE".equals(t.proposedColumn)).count();
                
                System.out.println("  Tokens with DATE assignment: " + tokensWithDate);
                System.out.println("  Tokens with amount assignment: " + tokensWithAmount);
                
                if (tokensWithDate == 0) {
                    System.out.println("  → Issue: No date tokens detected");
                }
                if (tokensWithAmount == 0) {
                    System.out.println("  → Issue: No amount tokens detected");
                }
            } else {
                // Analyze row completeness
                long rowsWithDate = rows.stream().filter(r -> r.date != null).count();
                long rowsWithValidDate = rows.stream().filter(r -> r.dateValid).count();
                long rowsWithNarration = rows.stream().filter(r -> r.narration != null && !r.narration.trim().isEmpty()).count();
                long rowsWithDebit = rows.stream().filter(r -> r.debit != null).count();
                long rowsWithCredit = rows.stream().filter(r -> r.credit != null).count();
                long rowsWithBalance = rows.stream().filter(r -> r.balance != null).count();
                
                System.out.println("\nRow Completeness:");
                System.out.println("  Rows with date field: " + rowsWithDate + " (" + (100 * rowsWithDate / rows.size()) + "%)");
                System.out.println("  Rows with VALID date: " + rowsWithValidDate + " (" + (100 * rowsWithValidDate / rows.size()) + "%)");
                System.out.println("  Rows with narration: " + rowsWithNarration + " (" + (100 * rowsWithNarration / rows.size()) + "%)");
                System.out.println("  Rows with debit: " + rowsWithDebit);
                System.out.println("  Rows with credit: " + rowsWithCredit);
                System.out.println("  Rows with balance: " + rowsWithBalance + " (" + (100 * rowsWithBalance / rows.size()) + "%)");
                
                System.out.println("\nFirst 5 rows:");
                for (int i = 0; i < Math.min(5, rows.size()); i++) {
                    System.out.println("  Row " + (i + 1) + ": " + rows.get(i));
                }
            }
            
            // EVALUATION METRIC 5: Balance Reconciliation
            System.out.println("\n" + "=".repeat(100));
            System.out.println("METRIC 5: BALANCE EQUATION VALIDATION");
            System.out.println("=".repeat(100));
            
            if (rows.size() < 2) {
                System.out.println("\n⚠️ Insufficient rows for balance validation (need at least 2)");
            } else {
                validateBalanceEquations(rows);
            }
            
            // EVALUATION METRIC 6: Example Success & Failure
            System.out.println("\n" + "=".repeat(100));
            System.out.println("METRIC 6: EXAMPLE COMPARISON");
            System.out.println("=".repeat(100));
            
            if (rows.isEmpty()) {
                System.out.println("\n⚠️ Cannot provide examples - no rows assembled");
            } else {
                // Find one successful row
                TransactionRow successRow = rows.stream()
                    .filter(r -> r.dateValid && r.balance != null)
                    .findFirst()
                    .orElse(null);
                
                // Find one problematic row
                TransactionRow failureRow = rows.stream()
                    .filter(r -> !r.dateValid || r.balance == null || r.date == null)
                    .findFirst()
                    .orElse(null);
                
                if (successRow != null) {
                    System.out.println("\n✅ SUCCESSFUL ROW EXAMPLE:");
                    System.out.println("  " + successRow);
                    System.out.println("  → Date is valid calendar date");
                    System.out.println("  → All required fields present");
                } else {
                    System.out.println("\n⚠️ NO FULLY SUCCESSFUL ROWS");
                }
                
                if (failureRow != null) {
                    System.out.println("\n❌ PROBLEMATIC ROW EXAMPLE:");
                    System.out.println("  " + failureRow);
                    if (!failureRow.dateValid && failureRow.date != null) {
                        System.out.println("  → Date validation failed: " + failureRow.dateValidationMessage);
                    }
                    if (failureRow.balance == null) {
                        System.out.println("  → Balance field missing");
                    }
                    if (failureRow.date == null) {
                        System.out.println("  → Date field missing");
                    }
                } else if (successRow != null) {
                    System.out.println("\n✅ NO PROBLEMATIC ROWS FOUND - All rows valid");
                }
            }
            
            // Final Assessment
            System.out.println("\n" + "=".repeat(100));
            System.out.println("CROSS-BANK COMPATIBILITY ASSESSMENT");
            System.out.println("=".repeat(100));
            
            boolean headerDetected = bounds.dateColumnFromHeader;
            boolean datesExtracted = dateTokens > 0;
            boolean amountsExtracted = (debitTokens + creditTokens + balanceTokens) > 0;
            boolean rowsAssembled = rows.size() > 0;
            boolean datesValid = rows.stream().anyMatch(r -> r.dateValid);
            
            System.out.println("\nCompatibility Checklist:");
            System.out.println("  [" + (headerDetected ? "✅" : "❌") + "] Header-based detection works");
            System.out.println("  [" + (datesExtracted ? "✅" : "❌") + "] Date column detected");
            System.out.println("  [" + (amountsExtracted ? "✅" : "❌") + "] Amount columns detected");
            System.out.println("  [" + (rowsAssembled ? "✅" : "❌") + "] Transaction rows assembled");
            System.out.println("  [" + (datesValid ? "✅" : "❌") + "] At least some valid dates");
            
            int successCount = (headerDetected ? 1 : 0) + (datesExtracted ? 1 : 0) + 
                             (amountsExtracted ? 1 : 0) + (rowsAssembled ? 1 : 0) + (datesValid ? 1 : 0);
            
            System.out.println("\nOverall Score: " + successCount + "/5");
            
            if (successCount >= 4) {
                System.out.println("Assessment: ✅ Good cross-bank compatibility");
            } else if (successCount >= 2) {
                System.out.println("Assessment: ⚠️ Partial compatibility - requires tuning");
            } else {
                System.out.println("Assessment: ❌ Poor compatibility - major differences detected");
            }
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("EVALUATION COMPLETE");
            System.out.println("=".repeat(100));
        }
    }
    
    // Reuse exact same extraction logic from GeometryDiagnosticTest
    private List<Token> extractTokensWithCoordinates(PDDocument document, int pageIndex) throws IOException {
        List<Token> tokens = new ArrayList<>();
        
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                StringBuilder wordBuilder = new StringBuilder();
                double wordX = 0, wordY = 0, wordWidth = 0, wordHeight = 0;
                boolean inWord = false;
                
                for (TextPosition pos : textPositions) {
                    String character = pos.getUnicode();
                    
                    if (character.trim().isEmpty()) {
                        if (inWord) {
                            tokens.add(new Token(wordBuilder.toString(), wordX, wordY, wordWidth, wordHeight, pageIndex));
                            wordBuilder.setLength(0);
                            inWord = false;
                        }
                    } else {
                        if (!inWord) {
                            wordX = pos.getX();
                            wordY = pos.getY();
                            wordWidth = 0;
                            wordHeight = pos.getHeight();
                            inWord = true;
                        }
                        wordBuilder.append(character);
                        wordWidth += pos.getWidth();
                    }
                }
                
                if (inWord) {
                    tokens.add(new Token(wordBuilder.toString(), wordX, wordY, wordWidth, wordHeight, pageIndex));
                }
            }
        };
        
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);
        
        return tokens;
    }
    
    // Reuse exact same detection logic from GeometryDiagnosticTest
    private ColumnBoundaries detectColumns(List<Token> tokens) {
        ColumnBoundaries bounds = new ColumnBoundaries();
        
        // Step 1: Try to detect date column from header
        Token dateHeader = tokens.stream()
            .filter(t -> t.y < 150)
            .filter(t -> t.text.toLowerCase().contains("date"))
            .min(Comparator.comparingDouble(t -> t.x))
            .orElse(null);
        
        if (dateHeader != null) {
            bounds.dateMinX = dateHeader.x - 10;
            bounds.dateMaxX = dateHeader.x + 60;
            bounds.dateColumnFromHeader = true;
            bounds.detectionMethod = "HEADER_BASED";
            System.out.println("Date column detected from header '" + dateHeader.text + "' at X=" + 
                             String.format("%.2f", dateHeader.x));
            System.out.println("Date column: X range [" + String.format("%.2f", bounds.dateMinX) + 
                             " - " + String.format("%.2f", bounds.dateMaxX) + "] (HEADER-DERIVED)");
        } else {
            System.out.println("⚠️ No date header found. Using fallback: month-name clustering");
            bounds = detectDateColumnFromMonthNames(tokens);
        }
        
        // Step 2: Detect amount columns
        Pattern amountPattern = Pattern.compile("^[\\d,]+\\.\\d{2}$");
        List<Double> amountXs = tokens.stream()
            .filter(t -> amountPattern.matcher(t.text).matches())
            .map(t -> t.x)
            .collect(Collectors.toList());
        
        if (!amountXs.isEmpty()) {
            Collections.sort(amountXs);
            List<List<Double>> clusters = clusterXPositions(amountXs, 3);
            
            if (clusters.size() >= 2) {
                if (clusters.size() == 2) {
                    bounds.creditMinX = clusters.get(0).get(0) - 10;
                    bounds.creditMaxX = clusters.get(0).get(clusters.get(0).size() - 1) + 10;
                    bounds.balanceMinX = clusters.get(1).get(0) - 10;
                    bounds.balanceMaxX = clusters.get(1).get(clusters.get(1).size() - 1) + 10;
                    
                    System.out.println("Credit column: X range [" + String.format("%.2f", bounds.creditMinX) + 
                                     " - " + String.format("%.2f", bounds.creditMaxX) + "]");
                    System.out.println("Balance column: X range [" + String.format("%.2f", bounds.balanceMinX) + 
                                     " - " + String.format("%.2f", bounds.balanceMaxX) + "]");
                } else if (clusters.size() >= 3) {
                    bounds.debitMinX = clusters.get(0).get(0) - 10;
                    bounds.debitMaxX = clusters.get(0).get(clusters.get(0).size() - 1) + 10;
                    bounds.creditMinX = clusters.get(1).get(0) - 10;
                    bounds.creditMaxX = clusters.get(1).get(clusters.get(1).size() - 1) + 10;
                    bounds.balanceMinX = clusters.get(2).get(0) - 10;
                    bounds.balanceMaxX = clusters.get(2).get(clusters.get(2).size() - 1) + 10;
                    
                    System.out.println("Debit column: X range [" + String.format("%.2f", bounds.debitMinX) + 
                                     " - " + String.format("%.2f", bounds.debitMaxX) + "]");
                    System.out.println("Credit column: X range [" + String.format("%.2f", bounds.creditMinX) + 
                                     " - " + String.format("%.2f", bounds.creditMaxX) + "]");
                    System.out.println("Balance column: X range [" + String.format("%.2f", bounds.balanceMinX) + 
                                     " - " + String.format("%.2f", bounds.balanceMaxX) + "]");
                }
            }
        }
        
        // Step 3: Narration between date and amounts
        if (bounds.dateMaxX > 0 && bounds.debitMinX < Double.MAX_VALUE) {
            bounds.narrationMinX = bounds.dateMaxX;
            bounds.narrationMaxX = bounds.debitMinX;
        } else if (bounds.dateMaxX > 0 && bounds.creditMinX < Double.MAX_VALUE) {
            bounds.narrationMinX = bounds.dateMaxX;
            bounds.narrationMaxX = bounds.creditMinX;
        }
        
        if (bounds.narrationMinX < Double.MAX_VALUE) {
            System.out.println("Narration column: X range [" + String.format("%.2f", bounds.narrationMinX) + 
                             " - " + String.format("%.2f", bounds.narrationMaxX) + "]");
        }
        
        return bounds;
    }
    
    private ColumnBoundaries detectDateColumnFromMonthNames(List<Token> tokens) {
        ColumnBoundaries bounds = new ColumnBoundaries();
        bounds.detectionMethod = "MONTH_NAME_CLUSTERING";
        
        Pattern dayPattern = Pattern.compile("^(0[1-9]|[12][0-9]|3[01])$");
        Pattern monthPattern = Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)$");
        Pattern yearPattern = Pattern.compile("^(202[0-9]|20[3-9][0-9])$");
        
        List<Double> dayXs = tokens.stream()
            .filter(t -> dayPattern.matcher(t.text).matches())
            .map(t -> t.x)
            .collect(Collectors.toList());
        
        List<Double> monthXs = tokens.stream()
            .filter(t -> monthPattern.matcher(t.text).matches())
            .map(t -> t.x)
            .collect(Collectors.toList());
        
        List<Double> yearXs = tokens.stream()
            .filter(t -> yearPattern.matcher(t.text).matches())
            .map(t -> t.x)
            .collect(Collectors.toList());
        
        if (dayXs.isEmpty() || monthXs.isEmpty() || yearXs.isEmpty()) {
            System.out.println("⚠️ AMBIGUOUS: Insufficient date tokens for clustering");
            return bounds;
        }
        
        Collections.sort(dayXs);
        List<List<Double>> dayClusters = clusterXPositions(dayXs, 3);
        
        double monthMinX = monthXs.stream().min(Double::compare).get();
        
        List<Double> actualDateDayXs = dayClusters.stream()
            .filter(cluster -> {
                double clusterMax = cluster.stream().max(Double::compare).get();
                return Math.abs(clusterMax - monthMinX) < 30;
            })
            .findFirst()
            .orElse(dayClusters.isEmpty() ? new ArrayList<>() : dayClusters.get(dayClusters.size() - 1));
        
        if (!actualDateDayXs.isEmpty()) {
            bounds.dateMinX = actualDateDayXs.stream().min(Double::compare).get() - 5;
            bounds.dateMaxX = yearXs.stream().max(Double::compare).get() + 10;
            
            System.out.println("Date column (via month clustering): X range [" + 
                             String.format("%.2f", bounds.dateMinX) + " - " + 
                             String.format("%.2f", bounds.dateMaxX) + "] (FALLBACK)");
        }
        
        return bounds;
    }
    
    private List<List<Double>> clusterXPositions(List<Double> sortedXs, int maxClusters) {
        List<List<Double>> clusters = new ArrayList<>();
        if (sortedXs.isEmpty()) return clusters;
        
        List<Double> currentCluster = new ArrayList<>();
        currentCluster.add(sortedXs.get(0));
        
        for (int i = 1; i < sortedXs.size(); i++) {
            double gap = sortedXs.get(i) - sortedXs.get(i - 1);
            
            if (gap > 30 && clusters.size() < maxClusters - 1) {
                clusters.add(currentCluster);
                currentCluster = new ArrayList<>();
            }
            currentCluster.add(sortedXs.get(i));
        }
        clusters.add(currentCluster);
        
        return clusters;
    }
    
    private void assignColumnsToTokens(List<Token> tokens, ColumnBoundaries bounds) {
        Pattern dayPattern = Pattern.compile("^(0[1-9]|[12][0-9]|3[01])$");
        Pattern monthPattern = Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)$");
        Pattern yearPattern = Pattern.compile("^(202[0-9]|20[3-9][0-9])$");
        Pattern amountPattern = Pattern.compile("^[\\d,]+\\.\\d{2}$");
        
        for (Token token : tokens) {
            double x = token.x;
            
            if ((dayPattern.matcher(token.text).matches() || 
                 monthPattern.matcher(token.text).matches() ||
                 yearPattern.matcher(token.text).matches()) && 
                x >= bounds.dateMinX && x <= bounds.dateMaxX) {
                token.proposedColumn = "DATE";
            }
            else if (amountPattern.matcher(token.text).matches() && 
                     x >= bounds.balanceMinX && x <= bounds.balanceMaxX) {
                token.proposedColumn = "BALANCE";
            }
            else if (amountPattern.matcher(token.text).matches() && 
                     x >= bounds.creditMinX && x <= bounds.creditMaxX) {
                token.proposedColumn = "CREDIT";
            }
            else if (amountPattern.matcher(token.text).matches() && 
                     x >= bounds.debitMinX && x <= bounds.debitMaxX) {
                token.proposedColumn = "DEBIT";
            }
            else if (x >= bounds.narrationMinX && x < bounds.narrationMaxX) {
                token.proposedColumn = "NARRATION";
            }
        }
    }
    
    private List<TransactionRow> assembleTransactionRows(List<Token> tokens) {
        Map<Double, List<Token>> rowMap = new TreeMap<>();
        
        for (Token token : tokens) {
            double roundedY = Math.round(token.y / 2.0) * 2.0;
            rowMap.computeIfAbsent(roundedY, k -> new ArrayList<>()).add(token);
        }
        
        List<TransactionRow> rows = new ArrayList<>();
        
        for (Map.Entry<Double, List<Token>> entry : rowMap.entrySet()) {
            List<Token> rowTokens = entry.getValue();
            
            boolean hasDate = rowTokens.stream().anyMatch(t -> "DATE".equals(t.proposedColumn));
            boolean hasAmount = rowTokens.stream().anyMatch(t -> 
                "DEBIT".equals(t.proposedColumn) || 
                "CREDIT".equals(t.proposedColumn) || 
                "BALANCE".equals(t.proposedColumn));
            
            if (!hasDate || !hasAmount) {
                continue;
            }
            
            TransactionRow row = new TransactionRow();
            row.yPosition = entry.getKey();
            
            List<String> dateComponents = new ArrayList<>();
            
            for (Token token : rowTokens) {
                switch (token.proposedColumn != null ? token.proposedColumn : "") {
                    case "DATE":
                        dateComponents.add(token.text);
                        break;
                    case "NARRATION":
                        row.narration = (row.narration != null ? row.narration + " " : "") + token.text;
                        break;
                    case "DEBIT":
                        row.debit = parseBigDecimal(token.text);
                        break;
                    case "CREDIT":
                        row.credit = parseBigDecimal(token.text);
                        break;
                    case "BALANCE":
                        row.balance = parseBigDecimal(token.text);
                        break;
                }
            }
            
            if (!dateComponents.isEmpty()) {
                row.date = String.join("-", dateComponents);
                validateAndNormalizeDate(row);
            }
            
            rows.add(row);
        }
        
        return rows;
    }
    
    private void validateAndNormalizeDate(TransactionRow row) {
        if (row.date == null || row.date.isEmpty()) {
            row.dateValid = false;
            row.dateValidationMessage = "Empty date";
            return;
        }
        
        String[] parts = row.date.split("-");
        
        if (parts.length < 3) {
            row.dateValid = false;
            row.dateValidationMessage = "Incomplete date (expected DD-MMM-YYYY)";
            return;
        }
        
        if (parts.length > 3) {
            row.dateValid = false;
            row.dateValidationMessage = "Too many components (expected DD-MMM-YYYY, got " + parts.length + ")";
            return;
        }
        
        try {
            int day = Integer.parseInt(parts[0]);
            if (day < 1 || day > 31) {
                row.dateValid = false;
                row.dateValidationMessage = "Invalid day: " + day;
                return;
            }
        } catch (NumberFormatException e) {
            row.dateValid = false;
            row.dateValidationMessage = "Day is not numeric: " + parts[0];
            return;
        }
        
        String month = parts[1];
        List<String> validMonths = Arrays.asList("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                                 "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
        if (!validMonths.contains(month)) {
            row.dateValid = false;
            row.dateValidationMessage = "Invalid month: " + month;
            return;
        }
        
        try {
            int year = Integer.parseInt(parts[2]);
            if (year < 2020 || year > 2099) {
                row.dateValid = false;
                row.dateValidationMessage = "Invalid year: " + year;
                return;
            }
        } catch (NumberFormatException e) {
            row.dateValid = false;
            row.dateValidationMessage = "Year is not numeric: " + parts[2];
            return;
        }
        
        int day = Integer.parseInt(parts[0]);
        int monthIndex = validMonths.indexOf(month) + 1;
        int year = Integer.parseInt(parts[2]);
        
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        
        if (monthIndex == 2 && isLeapYear(year)) {
            daysInMonth[1] = 29;
        }
        
        if (day > daysInMonth[monthIndex - 1]) {
            row.dateValid = false;
            row.dateValidationMessage = "Invalid calendar date: " + month + " " + day;
            return;
        }
        
        row.dateValid = true;
    }
    
    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
    
    private BigDecimal parseBigDecimal(String text) {
        try {
            return new BigDecimal(text.replaceAll(",", ""));
        } catch (Exception e) {
            return null;
        }
    }
    
    private void validateBalanceEquations(List<TransactionRow> rows) {
        int validEquations = 0;
        int invalidEquations = 0;
        int missingData = 0;
        
        List<String> examples = new ArrayList<>();
        
        for (int i = 1; i < rows.size(); i++) {
            TransactionRow prev = rows.get(i - 1);
            TransactionRow curr = rows.get(i);
            
            if (prev.balance == null || curr.balance == null) {
                missingData++;
                continue;
            }
            
            BigDecimal expectedBalance = prev.balance;
            
            if (curr.credit != null) {
                expectedBalance = expectedBalance.add(curr.credit);
            }
            if (curr.debit != null) {
                expectedBalance = expectedBalance.subtract(curr.debit);
            }
            
            boolean valid = expectedBalance.compareTo(curr.balance) == 0;
            
            if (valid) {
                validEquations++;
                if (examples.size() < 2) {
                    examples.add("✅ VALID: " + formatEquation(prev.balance, curr.credit, curr.debit, curr.balance));
                }
            } else {
                invalidEquations++;
                if (examples.size() < 2) {
                    examples.add("❌ INVALID: " + formatEquation(prev.balance, curr.credit, curr.debit, curr.balance) + 
                               " (Expected: " + expectedBalance + ")");
                }
            }
        }
        
        System.out.println("\nBalance equation validation results:");
        System.out.println("  Valid equations: " + validEquations);
        System.out.println("  Invalid equations: " + invalidEquations);
        System.out.println("  Missing data: " + missingData);
        
        if (!examples.isEmpty()) {
            System.out.println("\nExample validations:");
            examples.forEach(ex -> System.out.println("  " + ex));
        }
    }
    
    private String formatEquation(BigDecimal prev, BigDecimal credit, BigDecimal debit, BigDecimal current) {
        return String.format("%s + %s - %s = %s",
            prev != null ? prev : "NULL",
            credit != null ? credit : "0",
            debit != null ? debit : "0",
            current != null ? current : "NULL");
    }
}
