package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Token Normalization POC: Test whether character-level TextPosition tokens
 * can be reliably reconstructed into words and dates using coordinate-based grouping.
 * 
 * Goal: Convert Canara's character-level tokens into semantically meaningful spans
 * without hardcoding specific dates or manual character combinations.
 * 
 * Phases:
 * 1. Extract character-level tokens (preserve all coordinates)
 * 2. Group characters into text spans using configurable thresholds
 * 3. Verify reconstructed spans against both Canara and Kotak
 */
public class TokenNormalizationPOCTest {

    // Test files
    private static final String KOTAK_PDF = "bank_stmnt/kotak_chandan.pdf";
    private static final String CANARA_PDF = "bank_stmnt/canara_chandhan.pdf";
    
    // Configurable thresholds for token normalization
    private static class NormalizationConfig {
        // Maximum horizontal gap (pixels) between characters in same word
        double maxCharGapX = 2.0;
        
        // Maximum vertical gap (pixels) between characters in same word
        double maxCharGapY = 2.0;
        
        // Y-coordinate tolerance for row grouping (pixels)
        double rowGroupingTolerance = 2.0;
        
        // Minimum word width (pixels) to avoid spurious tokens
        double minWordWidth = 1.0;
        
        public NormalizationConfig() {}
        
        public NormalizationConfig(double maxCharGapX, double maxCharGapY, double rowGroupingTolerance) {
            this.maxCharGapX = maxCharGapX;
            this.maxCharGapY = maxCharGapY;
            this.rowGroupingTolerance = rowGroupingTolerance;
        }
        
        @Override
        public String toString() {
            return String.format("CharGapX=%.1f, CharGapY=%.1f, RowTolerance=%.1f", 
                maxCharGapX, maxCharGapY, rowGroupingTolerance);
        }
    }
    
    /**
     * Character-level token extracted from PDF.
     * Preserves precise position for later aggregation.
     */
    private static class CharToken {
        String text;        // Single character (usually)
        double x;           // Left edge
        double y;           // Baseline position
        double width;       // Character width
        double height;      // Character height
        int pageNumber;     // Page index (0-based)
        
        CharToken(String text, double x, double y, double width, double height, int pageNumber) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.pageNumber = pageNumber;
        }
        
        double rightEdge() {
            return x + width;
        }
        
        @Override
        public String toString() {
            return String.format("'%s' @ (%.2f, %.2f)", text, x, y);
        }
    }
    
    /**
     * Reconstructed text span from multiple character tokens.
     * Preserves original coordinate range and page.
     */
    private static class TextSpan {
        String text;              // Reconstructed text
        double startX;            // Left edge of first character
        double endX;              // Right edge of last character
        double y;                 // Baseline (averaged)
        double height;            // Max height across characters
        int pageNumber;           // Page index
        int charCount;            // Number of original characters
        
        TextSpan(String text, double startX, double endX, double y, double height, int pageNumber, int charCount) {
            this.text = text;
            this.startX = startX;
            this.endX = endX;
            this.y = y;
            this.height = height;
            this.pageNumber = pageNumber;
            this.charCount = charCount;
        }
        
        double width() {
            return endX - startX;
        }
        
        @Override
        public String toString() {
            return String.format("Page %d | Y=%-6.2f | X=[%-6.2f - %-6.2f] | Chars: %2d | Text: '%s'",
                pageNumber, y, startX, endX, charCount, text);
        }
    }

    @Test
    void testCanaraTokenNormalization() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("CANARA TOKEN NORMALIZATION POC");
        System.out.println("=".repeat(100));
        
        File file = new File(CANARA_PDF);
        if (!file.exists()) {
            System.out.println("⚠️ PDF not found: " + CANARA_PDF);
            return;
        }
        
        try (PDDocument document = PDDocument.load(file)) {
            System.out.println("✅ PDF loaded: " + file.getName());
            System.out.println("   Total pages: " + document.getNumberOfPages());
            
            // Test on page with transaction data (Page 2, index 1)
            int testPage = 1;
            System.out.println("   Testing page: " + (testPage + 1));
            
            // Extract character-level tokens
            List<CharToken> charTokens = extractCharacterTokens(document, testPage);
            System.out.println("\n📊 EXTRACTION RESULTS:");
            System.out.println("   Character-level tokens: " + charTokens.size());
            
            // Test multiple threshold configurations
            NormalizationConfig[] configs = {
                new NormalizationConfig(2.0, 2.0, 2.0),   // Strict
                new NormalizationConfig(3.0, 2.0, 2.0),   // Medium
                new NormalizationConfig(5.0, 3.0, 3.0)    // Loose
            };
            
            for (NormalizationConfig config : configs) {
                System.out.println("\n" + "=".repeat(100));
                System.out.println("NORMALIZATION WITH CONFIG: " + config);
                System.out.println("=".repeat(100));
                
                List<TextSpan> spans = normalizeTokens(charTokens, config);
                evaluateNormalization(charTokens, spans, config);
            }
        }
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("CANARA POC COMPLETE");
        System.out.println("=".repeat(100));
    }
    
    @Test
    void testKotakTokenNormalization() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("KOTAK TOKEN NORMALIZATION POC (Regression Check)");
        System.out.println("=".repeat(100));
        
        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("⚠️ PDF not found: " + KOTAK_PDF);
            return;
        }
        
        try (PDDocument document = PDDocument.load(file)) {
            System.out.println("✅ PDF loaded: " + file.getName());
            System.out.println("   Total pages: " + document.getNumberOfPages());
            
            int testPage = 0;
            System.out.println("   Testing page: " + (testPage + 1));
            
            List<CharToken> charTokens = extractCharacterTokens(document, testPage);
            System.out.println("\n📊 EXTRACTION RESULTS:");
            System.out.println("   Character-level tokens: " + charTokens.size());
            
            NormalizationConfig config = new NormalizationConfig(2.0, 2.0, 2.0);
            System.out.println("\n" + "=".repeat(100));
            System.out.println("NORMALIZATION WITH CONFIG: " + config);
            System.out.println("=".repeat(100));
            
            List<TextSpan> spans = normalizeTokens(charTokens, config);
            evaluateNormalization(charTokens, spans, config);
        }
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("KOTAK POC COMPLETE");
        System.out.println("=".repeat(100));
    }

    /**
     * Extract character-level tokens from PDF page.
     * Each TextPosition becomes one token (no word grouping).
     */
    private List<CharToken> extractCharacterTokens(PDDocument document, int pageIndex) throws IOException {
        List<CharToken> tokens = new ArrayList<>();
        
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                for (TextPosition pos : textPositions) {
                    String character = pos.getUnicode();
                    // Keep all characters including spaces for analysis
                    if (!character.isEmpty()) {
                        tokens.add(new CharToken(
                            character,
                            Math.round(pos.getX() * 100.0) / 100.0,
                            Math.round(pos.getY() * 100.0) / 100.0,
                            Math.round(pos.getWidth() * 100.0) / 100.0,
                            Math.round(pos.getHeight() * 100.0) / 100.0,
                            pageIndex
                        ));
                    }
                }
            }
        };
        
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);
        
        return tokens;
    }

    /**
     * Group character-level tokens into text spans using coordinate proximity.
     * 
     * Algorithm:
     * 1. Sort tokens by Y (row), then X (left-to-right)
     * 2. Within each row, group adjacent characters into spans
     * 3. Break span when gap exceeds threshold or Y changes
     */
    private List<TextSpan> normalizeTokens(List<CharToken> charTokens, NormalizationConfig config) {
        if (charTokens.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<TextSpan> spans = new ArrayList<>();
        
        // Group tokens by approximate Y coordinate (row)
        Map<Long, List<CharToken>> rowMap = charTokens.stream()
            .collect(Collectors.groupingBy(t -> Math.round(t.y / config.rowGroupingTolerance)));
        
        // Process each row independently
        for (List<CharToken> rowTokens : rowMap.values()) {
            // Sort left to right
            rowTokens.sort(Comparator.comparingDouble(t -> t.x));
            
            // Group into spans
            StringBuilder spanBuilder = new StringBuilder();
            double spanStartX = 0, spanEndX = 0;
            double spanY = 0, spanHeight = 0;
            int spanPage = 0;
            int spanCharCount = 0;
            CharToken prevToken = null;
            
            for (CharToken token : rowTokens) {
                // Skip pure whitespace tokens in span building
                if (token.text.trim().isEmpty()) {
                    // Treat space as span separator
                    if (spanCharCount > 0) {
                        // End current span
                        spans.add(new TextSpan(
                            spanBuilder.toString(),
                            spanStartX, spanEndX, spanY, spanHeight,
                            spanPage, spanCharCount
                        ));
                        spanBuilder.setLength(0);
                        spanCharCount = 0;
                        prevToken = null;
                    }
                    continue;
                }
                
                boolean startNewSpan = false;
                
                if (prevToken == null) {
                    // First character in row
                    startNewSpan = true;
                } else {
                    // Check if this character continues previous span
                    double xGap = token.x - prevToken.rightEdge();
                    double yGap = Math.abs(token.y - prevToken.y);
                    
                    if (xGap > config.maxCharGapX || yGap > config.maxCharGapY) {
                        // Gap too large - end current span
                        spans.add(new TextSpan(
                            spanBuilder.toString(),
                            spanStartX, spanEndX, spanY, spanHeight,
                            spanPage, spanCharCount
                        ));
                        spanBuilder.setLength(0);
                        spanCharCount = 0;
                        startNewSpan = true;
                    }
                }
                
                if (startNewSpan) {
                    spanStartX = token.x;
                    spanY = token.y;
                    spanHeight = token.height;
                    spanPage = token.pageNumber;
                }
                
                spanBuilder.append(token.text);
                spanEndX = token.rightEdge();
                spanHeight = Math.max(spanHeight, token.height);
                spanCharCount++;
                prevToken = token;
            }
            
            // Add final span in row
            if (spanCharCount > 0) {
                spans.add(new TextSpan(
                    spanBuilder.toString(),
                    spanStartX, spanEndX, spanY, spanHeight,
                    spanPage, spanCharCount
                ));
            }
        }
        
        // Sort spans by Y (top to bottom), then X (left to right)
        spans.sort(Comparator.comparingDouble((TextSpan s) -> s.y).thenComparingDouble(s -> s.startX));
        
        return spans;
    }

    /**
     * Evaluate normalization quality by checking for expected patterns.
     */
    private void evaluateNormalization(List<CharToken> charTokens, List<TextSpan> spans, NormalizationConfig config) {
        System.out.println("\n📊 NORMALIZATION METRICS:");
        System.out.println("   Input character tokens: " + charTokens.size());
        System.out.println("   Output text spans: " + spans.size());
        System.out.println("   Compression ratio: " + String.format("%.2f%%", 
            (1.0 - spans.size() / (double) charTokens.size()) * 100));
        
        // Analyze span characteristics
        DoubleSummaryStatistics widthStats = spans.stream()
            .mapToDouble(TextSpan::width)
            .summaryStatistics();
        
        DoubleSummaryStatistics charStats = spans.stream()
            .mapToDouble(s -> s.charCount)
            .summaryStatistics();
        
        System.out.println("\n📏 SPAN STATISTICS:");
        System.out.println("   Width (px):  min=%.2f, max=%.2f, avg=%.2f".formatted(
            widthStats.getMin(), widthStats.getMax(), widthStats.getAverage()));
        System.out.println("   Char count:  min=%.0f, max=%.0f, avg=%.2f".formatted(
            charStats.getMin(), charStats.getMax(), charStats.getAverage()));
        
        // Check for header keywords
        System.out.println("\n🔍 HEADER KEYWORD DETECTION:");
        String[] headerKeywords = {"Date", "Particulars", "Deposits", "Withdrawals", "Balance", "Description", "Narration"};
        
        for (String keyword : headerKeywords) {
            List<TextSpan> matches = spans.stream()
                .filter(s -> s.text.equalsIgnoreCase(keyword))
                .collect(Collectors.toList());
            
            if (!matches.isEmpty()) {
                System.out.println("   ✅ Found '" + keyword + "': " + matches.size() + " occurrence(s)");
                matches.forEach(s -> System.out.println("      " + s));
            } else {
                System.out.println("   ❌ Missing '" + keyword + "'");
            }
        }
        
        // Check for date patterns (DD-MM-YYYY or DD-MMM-YYYY)
        System.out.println("\n📅 DATE PATTERN DETECTION:");
        List<TextSpan> dateSpans = spans.stream()
            .filter(s -> s.text.matches("\\d{2}-\\d{2}-\\d{4}") || s.text.matches("\\d{2}-[A-Za-z]{3}-\\d{4}"))
            .collect(Collectors.toList());
        
        System.out.println("   Date-like spans found: " + dateSpans.size());
        if (!dateSpans.isEmpty()) {
            System.out.println("   First 10 dates:");
            dateSpans.stream().limit(10).forEach(s -> {
                String validation = validateDateSpan(s.text);
                System.out.println("      " + s.text + " @ Y=" + String.format("%.2f", s.y) + 
                    " X=" + String.format("%.2f", s.startX) + " " + validation);
            });
        }
        
        // Check for numeric amounts (decimal format)
        System.out.println("\n💰 NUMERIC AMOUNT DETECTION:");
        List<TextSpan> amountSpans = spans.stream()
            .filter(s -> s.text.matches("[\\d,]+\\.\\d{2}"))
            .collect(Collectors.toList());
        
        System.out.println("   Amount-like spans found: " + amountSpans.size());
        if (!amountSpans.isEmpty()) {
            System.out.println("   First 10 amounts:");
            amountSpans.stream().limit(10).forEach(s -> 
                System.out.println("      " + s.text + " @ Y=" + String.format("%.2f", s.y) + 
                    " X=" + String.format("%.2f", s.startX)));
        }
        
        // Show sample of first 20 spans
        System.out.println("\n📝 SAMPLE SPANS (first 20):");
        spans.stream().limit(20).forEach(s -> System.out.println("   " + s));
    }

    /**
     * Validate a date string and return validation message.
     */
    private String validateDateSpan(String dateText) {
        // Try DD-MM-YYYY format
        if (dateText.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] parts = dateText.split("-");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            
            if (year < 2020 || year > 2099) return "[INVALID: Year out of range]";
            if (month < 1 || month > 12) return "[INVALID: Month out of range]";
            
            int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            if (month == 2 && isLeapYear(year)) daysInMonth[1] = 29;
            
            if (day < 1 || day > daysInMonth[month - 1]) {
                return "[INVALID: Day " + day + " invalid for month " + month + "]";
            }
            
            return "[VALID]";
        }
        
        // Try DD-MMM-YYYY format
        if (dateText.matches("\\d{2}-[A-Za-z]{3}-\\d{4}")) {
            String[] parts = dateText.split("-");
            int day = Integer.parseInt(parts[0]);
            String monthStr = parts[1];
            int year = Integer.parseInt(parts[2]);
            
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            int month = -1;
            for (int i = 0; i < months.length; i++) {
                if (months[i].equalsIgnoreCase(monthStr)) {
                    month = i + 1;
                    break;
                }
            }
            
            if (month == -1) return "[INVALID: Unknown month '" + monthStr + "']";
            if (year < 2020 || year > 2099) return "[INVALID: Year out of range]";
            
            int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            if (month == 2 && isLeapYear(year)) daysInMonth[1] = 29;
            
            if (day < 1 || day > daysInMonth[month - 1]) {
                return "[INVALID: Day " + day + " invalid for " + monthStr + "]";
            }
            
            return "[VALID]";
        }
        
        return "[UNKNOWN FORMAT]";
    }

    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}
