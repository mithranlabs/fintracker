package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Geometry-based proof of concept for Universal Statement Parser.
 * 
 * Hypothesis: Preserving PDF word coordinates and validating transaction sequences 
 * against running balances will be more reliable than parsing flattened text with 
 * bank-specific regexes.
 * 
 * This test extracts one page of Kotak statement to demonstrate:
 * 1. Word coordinate extraction (x, y, text)
 * 2. Column detection based on geometry
 * 3. Balance equation validation: Previous + Credits - Debits = Current
 */
public class GeometryDiagnosticTest {

    private static final String KOTAK_PDF = "bank_stmnt/kotak_chandan.pdf";
    private static final int TEST_PAGE = 1; // Second page (0-indexed) - likely contains transactions
    
    // Column detection thresholds (will be refined based on actual data)
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
    
    /**
     * Token with coordinates extracted from PDF.
     * Package-private for reuse in comparison tests.
     */
    static class Token {
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
            return String.format("Page %d | X: %6.2f | Y: %6.2f | W: %5.2f | H: %5.2f | %-10s | %s",
                pageNumber, x, y, width, height, 
                proposedColumn != null ? proposedColumn : "UNASSIGNED", 
                redact(text));
        }
        
        private String redact(String text) {
            // Redact potential sensitive data (keep structure for analysis)
            if (text.matches("\\d{4,}")) {
                return "****" + text.substring(Math.max(0, text.length() - 4));
            }
            return text;
        }
    }
    
    /**
     * Transaction row parsed from geometry.
     * Package-private for reuse in comparison tests.
     */
    static class TransactionRow {
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
                !dateValid && date != null ? " [INVALID DATE: " + dateValidationMessage + "]" : "");
        }
    }
    
    /**
     * Extract parsed transactions from Kotak PDF using geometry-based parsing.
     * Package-private for reuse in comparison tests.
     * 
     * @param pdfPath Path to PDF file
     * @param pageIndex 0-based page index
     * @return List of parsed transaction rows
     */
    static List<TransactionRow> extractTransactionsFromPDF(String pdfPath, int pageIndex) throws Exception {
        File file = new File(pdfPath);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        
        try (PDDocument document = PDDocument.load(file)) {
            GeometryDiagnosticTest test = new GeometryDiagnosticTest();
            List<Token> tokens = test.extractTokensWithCoordinates(document, pageIndex);
            ColumnBoundaries boundaries = test.detectColumns(tokens);
            test.assignColumnsToTokens(tokens, boundaries);
            return test.assembleTransactionRows(tokens);
        }
    }

    @Test
    void testGeometryBasedExtraction() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("GEOMETRY-BASED PDF PARSING PROOF OF CONCEPT");
        System.out.println("=".repeat(100));
        
        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("⚠️ PDF not found: " + KOTAK_PDF);
            System.out.println("   Test skipped");
            return;
        }
        
        try (PDDocument document = PDDocument.load(file)) {
            System.out.println("✅ PDF loaded: " + file.getName());
            System.out.println("   Total pages: " + document.getNumberOfPages());
            System.out.println("   Extracting page: " + (TEST_PAGE + 1));
            
            // Extract tokens with coordinates
            List<Token> tokens = extractTokensWithCoordinates(document, TEST_PAGE);
            System.out.println("\n✅ Extracted " + tokens.size() + " tokens with coordinates");
            
            // Detect column boundaries
            System.out.println("\n" + "=".repeat(100));
            System.out.println("STEP 1: COLUMN DETECTION");
            System.out.println("=".repeat(100));
            
            ColumnBoundaries boundaries = detectColumns(tokens);
            assignColumnsToTokens(tokens, boundaries);
            
            // Check for date-like tokens
            System.out.println("\nSearching for date-like tokens...");
            List<Token> datelikeTokens = tokens.stream()
                .filter(t -> t.text.matches(".*\\d{2}.*") && t.text.length() >= 8 && t.text.length() <= 15)
                .limit(20)
                .collect(Collectors.toList());
            
            if (!datelikeTokens.isEmpty()) {
                System.out.println("Date-like tokens found:");
                datelikeTokens.forEach(t -> System.out.println("  " + t));
            }
            
            // Show sample tokens from each column
            System.out.println("\nSample tokens by column:");
            for (String col : Arrays.asList("DATE", "NARRATION", "DEBIT", "CREDIT", "BALANCE")) {
                List<Token> colTokens = tokens.stream()
                    .filter(t -> col.equals(t.proposedColumn))
                    .limit(5)
                    .collect(Collectors.toList());
                
                if (!colTokens.isEmpty()) {
                    System.out.println("\n  " + col + ":");
                    colTokens.forEach(t -> System.out.println("    " + t));
                }
            }
            
            // Group tokens into transaction rows
            System.out.println("\n" + "=".repeat(100));
            System.out.println("STEP 2: TRANSACTION ROW ASSEMBLY");
            System.out.println("=".repeat(100));
            
            List<TransactionRow> rows = assembleTransactionRows(tokens);
            System.out.println("\n✅ Assembled " + rows.size() + " potential transaction rows");
            
            if (rows.isEmpty()) {
                System.out.println("\n❌ No transaction rows identified");
                System.out.println("\n⚠️  Analyzing why detection failed...");
                
                long totalTokens = tokens.size();
                long unassigned = tokens.stream().filter(t -> t.proposedColumn == null).count();
                long dateTokens = tokens.stream().filter(t -> "DATE".equals(t.proposedColumn)).count();
                
                System.out.println("  Total tokens: " + totalTokens);
                System.out.println("  Unassigned tokens: " + unassigned);
                System.out.println("  Date tokens found: " + dateTokens);
                
                System.out.println("\n  First 100 tokens (to understand structure):");
                tokens.stream().limit(100).forEach(t -> System.out.println("    " + t));
                
                dumpAllTokens(tokens);
                return;
            }
            
            // Show first few rows
            System.out.println("\nFirst " + Math.min(5, rows.size()) + " rows:");
            for (int i = 0; i < Math.min(5, rows.size()); i++) {
                System.out.println("  Row " + (i + 1) + ": " + rows.get(i));
            }
            
            // Validate balance equation
            System.out.println("\n" + "=".repeat(100));
            System.out.println("STEP 3: BALANCE EQUATION VALIDATION");
            System.out.println("=".repeat(100));
            System.out.println("Testing: Previous Balance + Credits - Debits = Current Balance\n");
            
            validateBalanceEquations(rows);
            
            // Summary
            System.out.println("\n" + "=".repeat(100));
            System.out.println("PROOF OF CONCEPT SUMMARY");
            System.out.println("=".repeat(100));
            
            summarizeFindings(tokens, rows);
        }
    }
    
    List<Token> extractTokensWithCoordinates(PDDocument document, int pageIndex) throws IOException {
        List<Token> tokens = new ArrayList<>();
        
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                // Group consecutive TextPositions into words
                StringBuilder wordBuilder = new StringBuilder();
                double wordX = 0, wordY = 0, wordWidth = 0, wordHeight = 0;
                boolean inWord = false;
                
                for (TextPosition pos : textPositions) {
                    String character = pos.getUnicode();
                    
                    if (character.trim().isEmpty()) {
                        // Space - end current word
                        if (inWord) {
                            tokens.add(new Token(wordBuilder.toString(), wordX, wordY, wordWidth, wordHeight, pageIndex));
                            wordBuilder.setLength(0);
                            inWord = false;
                        }
                    } else {
                        // Character - add to word
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
                
                // Add final word if any
                if (inWord) {
                    tokens.add(new Token(wordBuilder.toString(), wordX, wordY, wordWidth, wordHeight, pageIndex));
                }
            }
        };
        
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document); // Triggers the extraction
        
        return tokens;
    }
    
    ColumnBoundaries detectColumns(List<Token> tokens) {
        ColumnBoundaries bounds = new ColumnBoundaries();
        
        // Step 1: Try to detect date column from header
        Token dateHeader = tokens.stream()
            .filter(t -> t.y < 150) // Headers typically near top
            .filter(t -> t.text.toLowerCase().contains("date"))
            .min(Comparator.comparingDouble(t -> t.x)) // Leftmost "Date" header
            .orElse(null);
        
        if (dateHeader != null) {
            // Use header position to define date column boundary
            bounds.dateMinX = dateHeader.x - 10;
            bounds.dateMaxX = dateHeader.x + 60; // Reasonable width for date column
            bounds.dateColumnFromHeader = true;
            bounds.detectionMethod = "HEADER_BASED";
            System.out.println("Date column detected from header '" + dateHeader.text + "' at X=" + 
                             String.format("%.2f", dateHeader.x));
            System.out.println("Date column: X range [" + String.format("%.2f", bounds.dateMinX) + 
                             " - " + String.format("%.2f", bounds.dateMaxX) + "] (HEADER-DERIVED)");
        } else {
            // Fallback: Use month-name clustering
            System.out.println("⚠️ No date header found. Using fallback: month-name clustering");
            bounds = detectDateColumnFromMonthNames(tokens);
        }
        
        // Step 2: Detect amount columns (unchanged logic)
        Pattern amountPattern = Pattern.compile("^[\\d,]+\\.\\d{2}$");
        List<Double> amountXs = tokens.stream()
            .filter(t -> amountPattern.matcher(t.text).matches())
            .map(t -> t.x)
            .collect(Collectors.toList());
        
        if (!amountXs.isEmpty()) {
            Collections.sort(amountXs);
            
            // Cluster amounts into 2-3 columns (debit, credit, balance)
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
        
        // Step 3: Narration is between date and amounts
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
        
        // Collect X positions for date components
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
        
        // Cluster day tokens by X-coordinate (might have multiple clusters)
        Collections.sort(dayXs);
        List<List<Double>> dayClusters = clusterXPositions(dayXs, 3);
        
        // Find the cluster that overlaps with month names
        double monthMinX = monthXs.stream().min(Double::compare).get();
        double monthMaxX = monthXs.stream().max(Double::compare).get();
        
        List<Double> actualDateDayXs = dayClusters.stream()
            .filter(cluster -> {
                double clusterMin = cluster.stream().min(Double::compare).get();
                double clusterMax = cluster.stream().max(Double::compare).get();
                // Check if this cluster is near month tokens (within 30 pixels)
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
            System.out.println("  Day cluster used: X=" + String.format("%.2f", actualDateDayXs.get(0)) + 
                             " (near months at X=" + String.format("%.2f", monthMinX) + ")");
            System.out.println("  Other day clusters ignored (likely row numbers)");
        } else {
            System.out.println("⚠️ AMBIGUOUS: Could not determine date column from month clustering");
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
            
            // If gap > 30 pixels, start new cluster
            if (gap > 30 && clusters.size() < maxClusters - 1) {
                clusters.add(currentCluster);
                currentCluster = new ArrayList<>();
            }
            currentCluster.add(sortedXs.get(i));
        }
        clusters.add(currentCluster);
        
        return clusters;
    }
    
    void assignColumnsToTokens(List<Token> tokens, ColumnBoundaries bounds) {
        // Kotak uses format like: 01-Sep-2024 but tokens are split
        Pattern dayPattern = Pattern.compile("^(0[1-9]|[12][0-9]|3[01])$");
        Pattern monthPattern = Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)$");
        Pattern yearPattern = Pattern.compile("^(202[0-9]|20[3-9][0-9])$");
        Pattern amountPattern = Pattern.compile("^[\\d,]+\\.\\d{2}$");
        
        for (Token token : tokens) {
            double x = token.x;
            
            // Date column components (day, month, year)
            if ((dayPattern.matcher(token.text).matches() || 
                 monthPattern.matcher(token.text).matches() ||
                 yearPattern.matcher(token.text).matches()) && 
                x >= bounds.dateMinX && x <= bounds.dateMaxX) {
                token.proposedColumn = "DATE";
            }
            // Balance column (rightmost)
            else if (amountPattern.matcher(token.text).matches() && 
                     x >= bounds.balanceMinX && x <= bounds.balanceMaxX) {
                token.proposedColumn = "BALANCE";
            }
            // Credit column
            else if (amountPattern.matcher(token.text).matches() && 
                     x >= bounds.creditMinX && x <= bounds.creditMaxX) {
                token.proposedColumn = "CREDIT";
            }
            // Debit column
            else if (amountPattern.matcher(token.text).matches() && 
                     x >= bounds.debitMinX && x <= bounds.debitMaxX) {
                token.proposedColumn = "DEBIT";
            }
            // Narration (between date and amounts)
            else if (x >= bounds.narrationMinX && x < bounds.narrationMaxX) {
                token.proposedColumn = "NARRATION";
            }
        }
    }
    
    List<TransactionRow> assembleTransactionRows(List<Token> tokens) {
        // Group tokens by Y position (same row)
        Map<Double, List<Token>> rowMap = new TreeMap<>();
        
        for (Token token : tokens) {
            // Round Y to nearest 2 pixels to group tokens on same line
            double roundedY = Math.round(token.y / 2.0) * 2.0;
            rowMap.computeIfAbsent(roundedY, k -> new ArrayList<>()).add(token);
        }
        
        List<TransactionRow> rows = new ArrayList<>();
        
        for (Map.Entry<Double, List<Token>> entry : rowMap.entrySet()) {
            List<Token> rowTokens = entry.getValue();
            
            // Check if row has transaction-like structure
            boolean hasDate = rowTokens.stream().anyMatch(t -> "DATE".equals(t.proposedColumn));
            boolean hasAmount = rowTokens.stream().anyMatch(t -> 
                "DEBIT".equals(t.proposedColumn) || 
                "CREDIT".equals(t.proposedColumn) || 
                "BALANCE".equals(t.proposedColumn));
            
            if (!hasDate || !hasAmount) {
                continue; // Skip header/footer rows
            }
            
            TransactionRow row = new TransactionRow();
            row.yPosition = entry.getKey();
            
            // Collect date tokens (only from DATE column), sorted left-to-right by X
            List<Token> dateCandidates = rowTokens.stream()
                .filter(t -> "DATE".equals(t.proposedColumn))
                .sorted(Comparator.comparingDouble(t -> t.x))
                .collect(Collectors.toList());
            
            for (Token token : rowTokens) {
                switch (token.proposedColumn != null ? token.proposedColumn : "") {
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
            
            // Extract exactly one DD-MMM-YYYY triplet anchored on the month token.
            // Row-number tokens (e.g. "10", "11") may land in the date column range
            // and match the day pattern; using the month as an unambiguous anchor
            // prevents them from being included in the date string.
            String extractedDate = extractDateTriplet(dateCandidates);
            if (extractedDate != null) {
                row.date = extractedDate;
                validateAndNormalizeDate(row);
            }
            
            rows.add(row);
        }
        
        return rows;
    }
    
    /**
     * Given a list of DATE-tagged tokens for a single row (sorted ascending by X),
     * extracts exactly one DD-MMM-YYYY triplet anchored on the month-name token.
     *
     * <p>Row-number tokens such as "10", "11", … may match the day pattern and fall
     * within the date-column X range, producing corrupted dates like "10-01-Sep-2026".
     * By anchoring on the month name (which is unambiguous) and looking immediately
     * to its left for a valid day and immediately to its right for a valid year, we
     * obtain the correct "01-Sep-2026" while discarding the row-number token.
     *
     * @param dateCandidates DATE-tagged tokens for one row, sorted left-to-right by X
     * @return "DD-MMM-YYYY" string, or null if no valid triplet is found
     */
    String extractDateTriplet(List<Token> dateCandidates) {
        Pattern monthPattern = Pattern.compile("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)$");
        Pattern dayPattern   = Pattern.compile("^(0[1-9]|[12][0-9]|3[01])$");
        Pattern yearPattern  = Pattern.compile("^(20[2-9][0-9])$");

        // Find the index of the month token — it is unambiguous
        int monthIdx = -1;
        for (int i = 0; i < dateCandidates.size(); i++) {
            if (monthPattern.matcher(dateCandidates.get(i).text).matches()) {
                monthIdx = i;
                break;
            }
        }
        if (monthIdx < 0) {
            // No month found — fall back to joining all tokens (legacy behaviour)
            if (!dateCandidates.isEmpty()) {
                return dateCandidates.stream().map(t -> t.text).collect(Collectors.joining("-"));
            }
            return null;
        }

        String month = dateCandidates.get(monthIdx).text;

        // Day: the token immediately to the left of the month that matches the day pattern
        String day = null;
        for (int i = monthIdx - 1; i >= 0; i--) {
            if (dayPattern.matcher(dateCandidates.get(i).text).matches()) {
                day = dateCandidates.get(i).text;
                break;
            }
        }

        // Year: the token immediately to the right of the month that matches the year pattern
        String year = null;
        for (int i = monthIdx + 1; i < dateCandidates.size(); i++) {
            if (yearPattern.matcher(dateCandidates.get(i).text).matches()) {
                year = dateCandidates.get(i).text;
                break;
            }
        }

        if (day != null && year != null) {
            return day + "-" + month + "-" + year;
        }

        // Partial date — return what we have so validation can flag it
        if (day != null) return day + "-" + month;
        if (year != null) return month + "-" + year;
        return month;
    }

    private void validateAndNormalizeDate(TransactionRow row) {
        if (row.date == null || row.date.isEmpty()) {
            row.dateValid = false;
            row.dateValidationMessage = "Empty date";
            return;
        }
        
        // Expected format: DD-MMM-YYYY or similar
        String[] parts = row.date.split("-");
        
        // Check component count
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
        
        // Validate day (01-31)
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
        
        // Validate month (Jan-Dec)
        String month = parts[1];
        List<String> validMonths = Arrays.asList("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                                 "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
        if (!validMonths.contains(month)) {
            row.dateValid = false;
            row.dateValidationMessage = "Invalid month: " + month;
            return;
        }
        
        // Validate year (2020-2099)
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
        
        // Basic calendar validation (no Feb 31, etc.)
        int day = Integer.parseInt(parts[0]);
        int monthIndex = validMonths.indexOf(month) + 1;
        int year = Integer.parseInt(parts[2]);
        
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        
        // Leap year check
        if (monthIndex == 2 && isLeapYear(year)) {
            daysInMonth[1] = 29;
        }
        
        if (day > daysInMonth[monthIndex - 1]) {
            row.dateValid = false;
            row.dateValidationMessage = "Invalid calendar date: " + month + " " + day;
            return;
        }
        
        // All validations passed
        row.dateValid = true;
    }
    
    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
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
        
        System.out.println("Balance equation validation results:");
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
    
    BigDecimal parseBigDecimal(String text) {
        try {
            return new BigDecimal(text.replaceAll(",", ""));
        } catch (Exception e) {
            return null;
        }
    }
    
    private void dumpAllTokens(List<Token> tokens) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("DEBUG: ALL EXTRACTED TOKENS");
        System.out.println("=".repeat(100));
        tokens.stream().limit(50).forEach(System.out::println);
        if (tokens.size() > 50) {
            System.out.println("... and " + (tokens.size() - 50) + " more tokens");
        }
    }
    
    private void summarizeFindings(List<Token> tokens, List<TransactionRow> rows) {
        System.out.println("\n1. COORDINATE PRESERVATION:");
        System.out.println("   ✅ PDFBox TextPosition API preserves X/Y coordinates for each character/word");
        System.out.println("   ✅ Extracted " + tokens.size() + " tokens with precise geometry");
        
        System.out.println("\n2. COLUMN DETECTION:");
        long dateTokens = tokens.stream().filter(t -> "DATE".equals(t.proposedColumn)).count();
        long narrationTokens = tokens.stream().filter(t -> "NARRATION".equals(t.proposedColumn)).count();
        long debitTokens = tokens.stream().filter(t -> "DEBIT".equals(t.proposedColumn)).count();
        long creditTokens = tokens.stream().filter(t -> "CREDIT".equals(t.proposedColumn)).count();
        long balanceTokens = tokens.stream().filter(t -> "BALANCE".equals(t.proposedColumn)).count();
        
        System.out.println("   Date tokens: " + dateTokens);
        System.out.println("   Narration tokens: " + narrationTokens);
        System.out.println("   Debit tokens: " + debitTokens);
        System.out.println("   Credit tokens: " + creditTokens);
        System.out.println("   Balance tokens: " + balanceTokens);
        
        boolean columnDetectionWorked = dateTokens > 0 && balanceTokens > 0;
        System.out.println("   Status: " + (columnDetectionWorked ? "✅ FEASIBLE" : "❌ FAILED"));
        
        System.out.println("\n3. TRANSACTION ROW ASSEMBLY:");
        System.out.println("   Assembled " + rows.size() + " transaction rows");
        
        // Date validation statistics
        long validDates = rows.stream().filter(r -> r.dateValid).count();
        long invalidDates = rows.stream().filter(r -> !r.dateValid).count();
        
        System.out.println("\n4. DATE VALIDATION:");
        System.out.println("   Valid dates: " + validDates + " (" + 
                         String.format("%.1f", 100.0 * validDates / rows.size()) + "%)");
        System.out.println("   Invalid dates: " + invalidDates + " (" + 
                         String.format("%.1f", 100.0 * invalidDates / rows.size()) + "%)");
        
        if (invalidDates > 0) {
            System.out.println("\n   Sample invalid dates:");
            rows.stream()
                .filter(r -> !r.dateValid)
                .limit(3)
                .forEach(r -> System.out.println("     " + r.date + " - " + r.dateValidationMessage));
        }
        
        System.out.println("\n5. FEASIBILITY ASSESSMENT:");
        boolean dateValidationPassed = validDates == rows.size();
        if (columnDetectionWorked && rows.size() > 0 && dateValidationPassed) {
            System.out.println("   ✅ Geometry-based parsing is FEASIBLE with current Java stack");
            System.out.println("   ✅ No new dependencies required (PDFBox 2.0.30 sufficient)");
            System.out.println("   ✅ Coordinate-based approach can distinguish columns");
            System.out.println("   ✅ Date validation: 100% of dates are valid");
        } else {
            if (!dateValidationPassed) {
                System.out.println("   ⚠️ Date validation failed: " + invalidDates + " invalid dates found");
            }
            System.out.println("   ⚠️ Further refinement needed for this statement format");
        }
        
        System.out.println("\n6. WHAT IS PRESERVED:");
        System.out.println("   ✅ Word-level text");
        System.out.println("   ✅ X/Y coordinates (exact positioning)");
        System.out.println("   ✅ Word width and height");
        System.out.println("   ✅ Page number");
        
        System.out.println("\n7. WHAT IS LOST:");
        System.out.println("   ❌ Font information (not extracted in this POC)");
        System.out.println("   ❌ Color information (not typically needed)");
        System.out.println("   ❌ Line/box drawing (PDFBox requires separate graphics extraction)");
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("PROOF OF CONCEPT COMPLETE");
        System.out.println("=".repeat(100));
    }
    
    @Test
    void testDateValidation_RowWithSequenceNumber() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("REGRESSION TEST: Row with sequence number and split date");
        System.out.println("=".repeat(80));
        
        // Simulate tokens from a row: "20" at X=39, "02-Sep-2026" at X=72-95
        List<Token> tokens = new ArrayList<>();
        tokens.add(new Token("Date", 75.0, 120.0, 20.0, 5.0, 1)); // Header
        tokens.add(new Token("20", 39.0, 154.0, 7.0, 5.0, 1));    // Row number (should be ignored)
        tokens.add(new Token("02", 72.0, 154.0, 7.0, 5.0, 1));    // Date day
        tokens.add(new Token("Sep", 81.0, 154.0, 12.0, 5.0, 1));  // Date month
        tokens.add(new Token("2026", 95.0, 154.0, 15.0, 5.0, 1)); // Date year
        tokens.add(new Token("100.00", 470.0, 154.0, 20.0, 5.0, 1)); // Credit
        tokens.add(new Token("1000.00", 530.0, 154.0, 25.0, 5.0, 1)); // Balance
        
        ColumnBoundaries bounds = detectColumns(tokens);
        assignColumnsToTokens(tokens, bounds);
        
        System.out.println("\nToken assignments:");
        tokens.stream()
            .filter(t -> t.y > 140) // Skip header
            .forEach(t -> System.out.println("  " + t));
        
        List<TransactionRow> rows = assembleTransactionRows(tokens);
        
        if (rows.isEmpty()) {
            System.out.println("\n❌ FAILED: No rows assembled");
            return;
        }
        
        TransactionRow row = rows.get(0);
        System.out.println("\nExtracted date: " + row.date);
        System.out.println("Date valid: " + row.dateValid);
        if (!row.dateValid) {
            System.out.println("Validation message: " + row.dateValidationMessage);
        }
        
        // Verify expectations
        boolean passed = row.date.equals("02-Sep-2026") && row.dateValid;
        System.out.println("\n" + (passed ? "✅ PASSED" : "❌ FAILED") + ": Date should be '02-Sep-2026' (without row number '20')");
        assertNotNull(row.date, "Date should not be null");
        assertEquals("02-Sep-2026", row.date, "Date should be '02-Sep-2026' (without row number '20')");
        assertTrue(row.dateValid, "Date should be valid");
    }
    
    @Test
    void testDateValidation_RowWithoutSequenceNumber() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("REGRESSION TEST: Row without sequence number");
        System.out.println("=".repeat(80));
        
        // Simulate tokens from a row without sequence number
        List<Token> tokens = new ArrayList<>();
        tokens.add(new Token("Date", 75.0, 120.0, 20.0, 5.0, 1)); // Header
        tokens.add(new Token("06", 72.0, 160.0, 7.0, 5.0, 1));    // Date day
        tokens.add(new Token("Sep", 81.0, 160.0, 12.0, 5.0, 1));  // Date month
        tokens.add(new Token("2026", 95.0, 160.0, 15.0, 5.0, 1)); // Date year
        tokens.add(new Token("200.00", 470.0, 160.0, 20.0, 5.0, 1)); // Credit
        tokens.add(new Token("1200.00", 530.0, 160.0, 25.0, 5.0, 1)); // Balance
        
        ColumnBoundaries bounds = detectColumns(tokens);
        assignColumnsToTokens(tokens, bounds);
        
        System.out.println("\nToken assignments:");
        tokens.stream()
            .filter(t -> t.y > 140) // Skip header
            .forEach(t -> System.out.println("  " + t));
        
        List<TransactionRow> rows = assembleTransactionRows(tokens);
        
        if (rows.isEmpty()) {
            System.out.println("\n❌ FAILED: No rows assembled");
            return;
        }
        
        TransactionRow row = rows.get(0);
        System.out.println("\nExtracted date: " + row.date);
        System.out.println("Date valid: " + row.dateValid);
        
        boolean passed = row.date.equals("06-Sep-2026") && row.dateValid;
        System.out.println("\n" + (passed ? "✅ PASSED" : "❌ FAILED") + ": Date should be '06-Sep-2026'");
        assertNotNull(row.date, "Date should not be null");
        assertEquals("06-Sep-2026", row.date, "Date should be '06-Sep-2026'");
        assertTrue(row.dateValid, "Date should be valid");
    }
    
    @Test
    void testDateValidation_InvalidDate() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("REGRESSION TEST: Invalid/incomplete date");
        System.out.println("=".repeat(80));
        
        // Simulate row with invalid date (Feb 31)
        List<Token> tokens = new ArrayList<>();
        tokens.add(new Token("Date", 75.0, 120.0, 20.0, 5.0, 1)); // Header
        tokens.add(new Token("31", 72.0, 170.0, 7.0, 5.0, 1));    // Invalid day for Feb
        tokens.add(new Token("Feb", 81.0, 170.0, 12.0, 5.0, 1));  // February
        tokens.add(new Token("2026", 95.0, 170.0, 15.0, 5.0, 1)); // Year
        tokens.add(new Token("300.00", 470.0, 170.0, 20.0, 5.0, 1)); // Credit
        tokens.add(new Token("1500.00", 530.0, 170.0, 25.0, 5.0, 1)); // Balance
        
        ColumnBoundaries bounds = detectColumns(tokens);
        assignColumnsToTokens(tokens, bounds);
        
        List<TransactionRow> rows = assembleTransactionRows(tokens);
        
        if (rows.isEmpty()) {
            System.out.println("\n❌ FAILED: No rows assembled");
            return;
        }
        
        TransactionRow row = rows.get(0);
        System.out.println("\nExtracted date: " + row.date);
        System.out.println("Date valid: " + row.dateValid);
        System.out.println("Validation message: " + row.dateValidationMessage);
        
        boolean passed = !row.dateValid && row.dateValidationMessage.contains("Invalid calendar date");
        System.out.println("\n" + (passed ? "✅ PASSED" : "❌ FAILED") + ": Should reject invalid calendar date (Feb 31)");
        assertFalse(row.dateValid, "Should reject invalid calendar date (Feb 31)");
        assertTrue(row.dateValidationMessage.contains("Invalid calendar date"));
    }

    @Test
    void testDateValidation_RowNumberTenCorruptionPattern() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("REGRESSION TEST: Row 10 corruption pattern (10-01-Sep-2026 vs 01-Sep-2026)");
        System.out.println("=".repeat(80));

        // Test the exact corruption scenario: Row 10 in Kotak statement
        // Row number "10" at X=36, Date "01" at X=72, "Sep" at X=81, "2026" at X=95
        List<Token> tokens = new ArrayList<>();
        tokens.add(new Token("Date", 75.0, 120.0, 20.0, 5.0, 1)); // Header
        tokens.add(new Token("10", 36.0, 603.8, 7.0, 5.0, 1));     // Row number 10 (matches dayPattern 10-31)
        tokens.add(new Token("01", 72.0, 603.8, 7.0, 5.0, 1));     // Actual date day
        tokens.add(new Token("Sep", 81.0, 603.8, 12.0, 5.0, 1));   // Actual date month
        tokens.add(new Token("2026", 95.0, 603.8, 15.0, 5.0, 1));  // Actual date year
        tokens.add(new Token("100.00", 466.0, 603.8, 20.0, 5.0, 1)); // Credit
        tokens.add(new Token("608.82", 535.0, 603.8, 25.0, 5.0, 1)); // Balance

        ColumnBoundaries bounds = detectColumns(tokens);
        assignColumnsToTokens(tokens, bounds);

        List<TransactionRow> rows = assembleTransactionRows(tokens);
        assertFalse(rows.isEmpty(), "Rows should be assembled");

        TransactionRow row = rows.get(0);
        assertNotNull(row.date, "Extracted date should not be null");
        assertNotEquals("10-01-Sep-2026", row.date, "Date must not be corrupted with row number '10'");
        assertEquals("01-Sep-2026", row.date, "Date should be '01-Sep-2026'");
        assertTrue(row.dateValid, "Date should be valid: " + row.dateValidationMessage);

        // Also test extractDateTriplet directly with row number token explicitly in date candidates
        // (simulating fallback clustering where row number falls inside date column range)
        List<Token> dateCandidatesWithRowNum = new ArrayList<>();
        Token rowNumToken = new Token("10", 36.0, 603.8, 7.0, 5.0, 1);
        rowNumToken.proposedColumn = "DATE";
        Token dayToken = new Token("01", 72.0, 603.8, 7.0, 5.0, 1);
        dayToken.proposedColumn = "DATE";
        Token monthToken = new Token("Sep", 81.0, 603.8, 12.0, 5.0, 1);
        monthToken.proposedColumn = "DATE";
        Token yearToken = new Token("2026", 95.0, 603.8, 15.0, 5.0, 1);
        yearToken.proposedColumn = "DATE";

        dateCandidatesWithRowNum.add(rowNumToken);
        dateCandidatesWithRowNum.add(dayToken);
        dateCandidatesWithRowNum.add(monthToken);
        dateCandidatesWithRowNum.add(yearToken);

        String directExtractedDate = extractDateTriplet(dateCandidatesWithRowNum);
        assertNotEquals("10-01-Sep-2026", directExtractedDate, 
            "extractDateTriplet must not produce corrupted date with prepended row number");
        assertEquals("01-Sep-2026", directExtractedDate, 
            "extractDateTriplet must extract pure '01-Sep-2026' anchored on month token");
    }
}
