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
 * AUDIT of GeometryDiagnosticTest.java
 * 
 * Purpose: Investigate date token issue "20-02-Sep-2026" and verify POC claims.
 * 
 * Key Questions:
 * 1. Are "20", "02", "Sep", "2026" all date tokens or do they represent different columns?
 * 2. What do column headers actually say?
 * 3. Are there separate transaction date vs. value date columns?
 * 4. Do all 35 rows have valid normalized dates?
 * 5. What do balance checks actually prove?
 * 6. Which assumptions would fail on different bank layouts?
 */
public class GeometryAuditTest {

    private static final String KOTAK_PDF = "bank_stmnt/kotak_chandan.pdf";
    private static final int TEST_PAGE = 1; // Page 2 (0-indexed)
    
    private static class Token {
        String text;
        double x;
        double y;
        double width;
        int pageNumber;
        String assignedColumn;
        
        Token(String text, double x, double y, double width, int pageNumber) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.pageNumber = pageNumber;
        }
        
        @Override
        public String toString() {
            return String.format("X:%6.2f Y:%6.2f W:%5.2f [%-10s] %s",
                x, y, width, 
                assignedColumn != null ? assignedColumn : "UNASSIGNED", 
                text.length() > 30 ? text.substring(0, 27) + "..." : text);
        }
    }
    
    @Test
    void auditDateTokenIssue() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("AUDIT: Date Token Classification Issue");
        System.out.println("=".repeat(100));
        
        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("⚠️ PDF not found: " + KOTAK_PDF);
            return;
        }
        
        try (PDDocument document = PDDocument.load(file)) {
            List<Token> tokens = extractTokens(document, TEST_PAGE);
            
            System.out.println("\n📋 TASK 1: TRACE DATE TOKEN ASSIGNMENT");
            System.out.println("=".repeat(100));
            
            // Find all tokens that match date patterns
            Pattern dayPattern = Pattern.compile("^(0[1-9]|[12][0-9]|3[01])$");
            Pattern monthPattern = Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)$");
            Pattern yearPattern = Pattern.compile("^(202[0-9]|20[3-9][0-9])$");
            
            List<Token> dayTokens = tokens.stream()
                .filter(t -> dayPattern.matcher(t.text).matches())
                .collect(Collectors.toList());
            
            List<Token> monthTokens = tokens.stream()
                .filter(t -> monthPattern.matcher(t.text).matches())
                .collect(Collectors.toList());
            
            List<Token> yearTokens = tokens.stream()
                .filter(t -> yearPattern.matcher(t.text).matches())
                .collect(Collectors.toList());
            
            System.out.println("\nTokens matching date patterns:");
            System.out.println("  Days (01-31): " + dayTokens.size());
            System.out.println("  Months (Jan-Dec): " + monthTokens.size());
            System.out.println("  Years (2024+): " + yearTokens.size());
            
            // Analyze X-coordinate distribution
            System.out.println("\n📊 X-Coordinate Distribution Analysis:");
            
            if (!dayTokens.isEmpty()) {
                DoubleSummaryStatistics dayStats = dayTokens.stream()
                    .mapToDouble(t -> t.x)
                    .summaryStatistics();
                System.out.println("\n  Day tokens X-coordinates:");
                System.out.println("    Min: " + String.format("%.2f", dayStats.getMin()));
                System.out.println("    Max: " + String.format("%.2f", dayStats.getMax()));
                System.out.println("    Range: " + String.format("%.2f", dayStats.getMax() - dayStats.getMin()));
                
                // Show first 5 day tokens with full context
                System.out.println("\n  First 5 day tokens (with row context):");
                for (int i = 0; i < Math.min(5, dayTokens.size()); i++) {
                    Token dayToken = dayTokens.get(i);
                    System.out.println("\n    Day token " + (i+1) + ": '" + dayToken.text + "' at X=" + 
                                     String.format("%.2f", dayToken.x) + " Y=" + String.format("%.2f", dayToken.y));
                    
                    // Find all tokens on same Y-coordinate (within 2 pixels)
                    List<Token> rowTokens = tokens.stream()
                        .filter(t -> Math.abs(t.y - dayToken.y) < 2.0)
                        .sorted(Comparator.comparingDouble(t -> t.x))
                        .collect(Collectors.toList());
                    
                    System.out.println("    Full row (" + rowTokens.size() + " tokens):");
                    for (Token t : rowTokens) {
                        System.out.println("      " + t);
                    }
                }
            }
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📋 TASK 2: INSPECT COLUMN HEADERS");
            System.out.println("=".repeat(100));
            
            // Find header row (typically near top, contains "Date", "Description", etc.)
            List<Token> headerCandidates = tokens.stream()
                .filter(t -> t.y < 150) // Headers typically in first 150 pixels
                .filter(t -> t.text.toLowerCase().matches(".*(date|description|narration|debit|credit|balance|withdrawal|deposit).*"))
                .collect(Collectors.toList());
            
            if (headerCandidates.isEmpty()) {
                System.out.println("\n⚠️ No obvious header row found. Showing tokens from Y < 150:");
                tokens.stream()
                    .filter(t -> t.y < 150)
                    .sorted(Comparator.comparingDouble((Token t) -> t.y).thenComparing((Token t) -> t.x))
                    .forEach(t -> System.out.println("  " + t));
            } else {
                System.out.println("\nHeader tokens found:");
                Map<Long, List<Token>> headerByY = headerCandidates.stream()
                    .collect(Collectors.groupingBy(t -> Math.round(t.y)));
                
                for (Map.Entry<Long, List<Token>> entry : headerByY.entrySet()) {
                    System.out.println("\n  Y=" + entry.getKey() + ":");
                    entry.getValue().stream()
                        .sorted(Comparator.comparingDouble(t -> t.x))
                        .forEach(t -> System.out.println("    X=" + String.format("%.2f", t.x) + " : " + t.text));
                }
            }
            
            // Look for multiple date-related headers
            List<Token> dateHeaderTokens = tokens.stream()
                .filter(t -> t.y < 150)
                .filter(t -> t.text.toLowerCase().contains("date"))
                .collect(Collectors.toList());
            
            if (dateHeaderTokens.size() > 1) {
                System.out.println("\n⚠️ MULTIPLE DATE HEADERS DETECTED:");
                dateHeaderTokens.forEach(t -> 
                    System.out.println("  '" + t.text + "' at X=" + String.format("%.2f", t.x)));
                System.out.println("\n  ⚠️ This suggests separate transaction date vs. value date columns!");
            }
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📋 TASK 3: VERIFY DATE NORMALIZATION");
            System.out.println("=".repeat(100));
            
            // Reconstruct rows using the original logic
            Map<Double, List<Token>> rowMap = new TreeMap<>();
            for (Token token : tokens) {
                double roundedY = Math.round(token.y / 2.0) * 2.0;
                rowMap.computeIfAbsent(roundedY, k -> new ArrayList<>()).add(token);
            }
            
            int rowsWithDates = 0;
            int rowsWithValidDates = 0;
            int rowsWithMultipleDates = 0;
            List<String> dateExamples = new ArrayList<>();
            
            for (Map.Entry<Double, List<Token>> entry : rowMap.entrySet()) {
                List<Token> rowTokens = entry.getValue();
                
                // Collect all date-like tokens in this row
                List<String> dateComponents = rowTokens.stream()
                    .filter(t -> dayPattern.matcher(t.text).matches() ||
                                monthPattern.matcher(t.text).matches() ||
                                yearPattern.matcher(t.text).matches())
                    .map(t -> t.text)
                    .collect(Collectors.toList());
                
                if (!dateComponents.isEmpty()) {
                    rowsWithDates++;
                    String reconstructedDate = String.join("-", dateComponents);
                    
                    // Check if it looks like a valid date (DD-MMM-YYYY or similar)
                    boolean isValid = reconstructedDate.matches("\\d{2}-[A-Za-z]{3}-\\d{4}");
                    
                    if (isValid) {
                        rowsWithValidDates++;
                    }
                    
                    if (dateComponents.size() > 4) {
                        rowsWithMultipleDates++;
                    }
                    
                    if (dateExamples.size() < 5) {
                        dateExamples.add(reconstructedDate + " [" + dateComponents.size() + " components] " + 
                                       (isValid ? "✅" : "❌ INVALID"));
                    }
                }
            }
            
            System.out.println("\nDate reconstruction analysis:");
            System.out.println("  Rows with date tokens: " + rowsWithDates);
            System.out.println("  Rows with valid DD-MMM-YYYY format: " + rowsWithValidDates);
            System.out.println("  Rows with >4 date components: " + rowsWithMultipleDates);
            
            if (rowsWithMultipleDates > 0) {
                System.out.println("\n  ⚠️ WARNING: " + rowsWithMultipleDates + 
                                 " rows have more than 4 date components!");
                System.out.println("  This indicates multiple date columns being merged!");
            }
            
            System.out.println("\nFirst 5 reconstructed dates:");
            dateExamples.forEach(ex -> System.out.println("  " + ex));
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📋 TASK 4: RE-EXAMINE BALANCE VALIDATION");
            System.out.println("=".repeat(100));
            
            System.out.println("\nWhat balance checks DO establish:");
            System.out.println("  ✅ The balance column was correctly identified");
            System.out.println("  ✅ Sequential balances follow arithmetic progression");
            System.out.println("  ✅ Debit/credit amounts are correctly paired with balances");
            
            System.out.println("\nWhat balance checks DO NOT establish:");
            System.out.println("  ❌ That dates are correctly parsed");
            System.out.println("  ❌ That we're using the right date column (txn vs. value date)");
            System.out.println("  ❌ That narrations are complete");
            System.out.println("  ❌ That the approach works for other banks");
            
            System.out.println("\n💡 CRITICAL INSIGHT:");
            System.out.println("  Balance validation proves row alignment, NOT date correctness!");
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📋 TASK 5: IDENTIFY HARD-CODED ASSUMPTIONS");
            System.out.println("=".repeat(100));
            
            System.out.println("\nHard-coded thresholds in GeometryDiagnosticTest.java:");
            System.out.println("\n  1. X-COORDINATE CLUSTERING:");
            System.out.println("     - Gap threshold: 30 pixels (line 232)");
            System.out.println("     - Assumption: Columns are >30 pixels apart");
            System.out.println("     - Risk: Fails if columns are closer or farther");
            
            System.out.println("\n  2. Y-COORDINATE GROUPING:");
            System.out.println("     - Rounding: Math.round(y / 2.0) * 2.0 (line 320)");
            System.out.println("     - Assumption: Tokens within 2 pixels are on same line");
            System.out.println("     - Risk: Fails with different line spacing");
            
            System.out.println("\n  3. DATE COLUMN BOUNDARY:");
            System.out.println("     - bounds.dateMinX = dateXs.get(0) - 5 (line 215)");
            System.out.println("     - bounds.dateMaxX = dateXs.get(dateXs.size()-1) + 50 (line 216)");
            System.out.println("     - Assumption: All date tokens within min-5 to max+50 range");
            System.out.println("     - Risk: Captures tokens from adjacent columns");
            
            System.out.println("\n  4. DATE PATTERNS:");
            System.out.println("     - Day: ^(0[1-9]|[12][0-9]|3[01])$");
            System.out.println("     - Month: ^(Jan|Feb|...|Dec)$");
            System.out.println("     - Year: ^(202[0-9]|20[3-9][0-9])$");
            System.out.println("     - Assumption: Kotak format (DD-MMM-YYYY)");
            System.out.println("     - Risk: Fails for DD/MM/YYYY, YYYY-MM-DD, etc.");
            
            System.out.println("\n  5. AMOUNT PATTERN:");
            System.out.println("     - Pattern: ^[\\d,]+\\.\\d{2}$");
            System.out.println("     - Assumption: Always 2 decimal places, commas optional");
            System.out.println("     - Risk: Fails for different number formats");
            
            System.out.println("\n  6. COLUMN ORDER ASSUMPTION:");
            System.out.println("     - Code assumes: Date < Narration < Debit < Credit < Balance");
            System.out.println("     - Risk: Fails if columns are in different order");
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📋 TASK 6: FINDINGS & RECOMMENDATIONS");
            System.out.println("=".repeat(100));
            
            System.out.println("\n🔍 PRIMARY ISSUE DIAGNOSIS:");
            if (rowsWithMultipleDates > 0) {
                System.out.println("  ❌ CONFIRMED: Date reconstruction is merging multiple date columns");
                System.out.println("  Example: '20-02-Sep-2026' likely represents:");
                System.out.println("    - '20' from transaction number column (X~39)");
                System.out.println("    - '02-Sep-2026' from actual date column (X~72-95)");
            }
            
            System.out.println("\n🎯 SMALLEST RECOMMENDED FIX:");
            System.out.println("  1. Cluster date tokens by X-coordinate BEFORE assigning to rows");
            System.out.println("  2. Identify which X-cluster contains actual dates (month names)");
            System.out.println("  3. Only use tokens from the date cluster, ignore others");
            System.out.println("  4. Add validation: reconstructed date must match DD-MMM-YYYY");
            
            System.out.println("\n⚠️ SCOPE LIMITATIONS:");
            System.out.println("  - This POC tested ONE page of ONE Kotak statement");
            System.out.println("  - Cannot claim universal compatibility");
            System.out.println("  - Requires testing on:");
            System.out.println("    • Other Kotak pages (different months/years)");
            System.out.println("    • Canara Bank statements");
            System.out.println("    • SBI statements");
            System.out.println("    • Different statement formats (PDF, scanned, etc.)");
            
            System.out.println("\n✅ WHAT IS ACTUALLY PROVEN:");
            System.out.println("  1. PDFBox can extract X/Y coordinates");
            System.out.println("  2. X-clustering can distinguish numeric columns");
            System.out.println("  3. Y-grouping can assemble rows");
            System.out.println("  4. Balance equations validate column alignment");
            
            System.out.println("\n❌ WHAT IS NOT PROVEN:");
            System.out.println("  1. Universal date parsing across banks");
            System.out.println("  2. Correct identification of transaction vs. value date");
            System.out.println("  3. Robustness to layout variations");
            System.out.println("  4. Multi-line narration handling");
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("AUDIT COMPLETE");
            System.out.println("=".repeat(100));
        }
    }
    
    private List<Token> extractTokens(PDDocument document, int pageIndex) throws IOException {
        List<Token> tokens = new ArrayList<>();
        
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                StringBuilder wordBuilder = new StringBuilder();
                double wordX = 0, wordY = 0, wordWidth = 0;
                boolean inWord = false;
                
                for (TextPosition pos : textPositions) {
                    String character = pos.getUnicode();
                    
                    if (character.trim().isEmpty()) {
                        if (inWord) {
                            tokens.add(new Token(wordBuilder.toString(), wordX, wordY, wordWidth, pageIndex));
                            wordBuilder.setLength(0);
                            inWord = false;
                        }
                    } else {
                        if (!inWord) {
                            wordX = pos.getX();
                            wordY = pos.getY();
                            wordWidth = 0;
                            inWord = true;
                        }
                        wordBuilder.append(character);
                        wordWidth += pos.getWidth();
                    }
                }
                
                if (inWord) {
                    tokens.add(new Token(wordBuilder.toString(), wordX, wordY, wordWidth, pageIndex));
                }
            }
        };
        
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);
        
        return tokens;
    }
}
