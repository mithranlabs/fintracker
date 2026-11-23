package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for token normalization.
 * Verifies that character-level extraction and span reconstruction work correctly.
 */
public class TokenNormalizationRegressionTest {

    private static final String CANARA_PDF = "bank_stmnt/canara_chandhan.pdf";
    
    private static class NormalizationConfig {
        double maxCharGapX = 2.0;
        double maxCharGapY = 2.0;
        double rowGroupingTolerance = 2.0;
        
        NormalizationConfig(double maxCharGapX, double maxCharGapY, double rowGroupingTolerance) {
            this.maxCharGapX = maxCharGapX;
            this.maxCharGapY = maxCharGapY;
            this.rowGroupingTolerance = rowGroupingTolerance;
        }
    }
    
    private static class CharToken {
        String text;
        double x, y, width, height;
        int pageNumber;
        
        CharToken(String text, double x, double y, double width, double height, int pageNumber) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.pageNumber = pageNumber;
        }
        
        double rightEdge() { return x + width; }
    }
    
    private static class TextSpan {
        String text;
        double startX, endX, y, height;
        int pageNumber, charCount;
        
        TextSpan(String text, double startX, double endX, double y, double height, int pageNumber, int charCount) {
            this.text = text;
            this.startX = startX;
            this.endX = endX;
            this.y = y;
            this.height = height;
            this.pageNumber = pageNumber;
            this.charCount = charCount;
        }
    }

    @Test
    void testCanaraHeaderReconstruction() throws Exception {
        File file = new File(CANARA_PDF);
        assertTrue(file.exists(), "Canara PDF should exist");
        
        try (PDDocument document = PDDocument.load(file)) {
            List<CharToken> charTokens = extractCharacterTokens(document, 1); // Page 2
            NormalizationConfig config = new NormalizationConfig(2.0, 2.0, 2.0);
            List<TextSpan> spans = normalizeTokens(charTokens, config);
            
            // Check that standard headers are reconstructed
            List<String> headerTexts = spans.stream()
                .filter(s -> s.y < 100) // Header region
                .map(s -> s.text)
                .collect(Collectors.toList());
            
            assertTrue(headerTexts.contains("Date"), "Should reconstruct 'Date' header");
            assertTrue(headerTexts.contains("Particulars"), "Should reconstruct 'Particulars' header");
            assertTrue(headerTexts.contains("Deposits"), "Should reconstruct 'Deposits' header");
            assertTrue(headerTexts.contains("Withdrawals"), "Should reconstruct 'Withdrawals' header");
            assertTrue(headerTexts.contains("Balance"), "Should reconstruct 'Balance' header");
        }
    }

    @Test
    void testCanaraDateReconstruction() throws Exception {
        File file = new File(CANARA_PDF);
        assertTrue(file.exists(), "Canara PDF should exist");
        
        try (PDDocument document = PDDocument.load(file)) {
            List<CharToken> charTokens = extractCharacterTokens(document, 1); // Page 2
            NormalizationConfig config = new NormalizationConfig(2.0, 2.0, 2.0);
            List<TextSpan> spans = normalizeTokens(charTokens, config);
            
            // Check that dates are reconstructed in DD-MM-YYYY format
            List<TextSpan> dateSpans = spans.stream()
                .filter(s -> s.text.matches("\\d{2}-\\d{2}-\\d{4}"))
                .collect(Collectors.toList());
            
            assertTrue(dateSpans.size() >= 5, "Should find at least 5 date spans on transaction page");
            
            // Verify first date is valid
            TextSpan firstDate = dateSpans.get(0);
            assertEquals("23-08-2026", firstDate.text, "First date should be 23-08-2026");
            assertTrue(isValidDate(firstDate.text), "Date should be calendar-valid");
        }
    }

    @Test
    void testCanaraNumericAmountReconstruction() throws Exception {
        File file = new File(CANARA_PDF);
        assertTrue(file.exists(), "Canara PDF should exist");
        
        try (PDDocument document = PDDocument.load(file)) {
            List<CharToken> charTokens = extractCharacterTokens(document, 1); // Page 2
            NormalizationConfig config = new NormalizationConfig(2.0, 2.0, 2.0);
            List<TextSpan> spans = normalizeTokens(charTokens, config);
            
            // Check that decimal amounts are reconstructed
            List<TextSpan> amountSpans = spans.stream()
                .filter(s -> s.text.matches("[\\d,]+\\.\\d{2}"))
                .collect(Collectors.toList());
            
            assertTrue(amountSpans.size() >= 10, "Should find at least 10 amount spans");
            
            // Verify amounts are in expected X-coordinate ranges (Deposits/Withdrawals/Balance columns)
            for (TextSpan amount : amountSpans) {
                assertTrue(amount.startX > 300, "Amount should be in right half of page (X > 300)");
            }
        }
    }

    @Test
    void testCanaraColumnCoordinatePreservation() throws Exception {
        File file = new File(CANARA_PDF);
        assertTrue(file.exists(), "Canara PDF should exist");
        
        try (PDDocument document = PDDocument.load(file)) {
            List<CharToken> charTokens = extractCharacterTokens(document, 1); // Page 2
            NormalizationConfig config = new NormalizationConfig(2.0, 2.0, 2.0);
            List<TextSpan> spans = normalizeTokens(charTokens, config);
            
            // Dates should be in left column (X < 100)
            List<TextSpan> dateSpans = spans.stream()
                .filter(s -> s.text.matches("\\d{2}-\\d{2}-\\d{4}"))
                .collect(Collectors.toList());
            
            for (TextSpan date : dateSpans) {
                assertTrue(date.startX < 100, "Date should be in left column (X < 100), found X=" + date.startX);
            }
            
            // Balance amounts should be in rightmost column (X > 500)
            List<TextSpan> balanceSpans = spans.stream()
                .filter(s -> s.text.matches("[\\d,]+\\.\\d{2}"))
                .filter(s -> s.startX > 500)
                .collect(Collectors.toList());
            
            assertTrue(balanceSpans.size() >= 5, "Should find balance amounts in rightmost column (X > 500)");
        }
    }

    @Test
    void testCompressionRatio() throws Exception {
        File file = new File(CANARA_PDF);
        assertTrue(file.exists(), "Canara PDF should exist");
        
        try (PDDocument document = PDDocument.load(file)) {
            List<CharToken> charTokens = extractCharacterTokens(document, 1); // Page 2
            NormalizationConfig config = new NormalizationConfig(2.0, 2.0, 2.0);
            List<TextSpan> spans = normalizeTokens(charTokens, config);
            
            double compressionRatio = (1.0 - spans.size() / (double) charTokens.size()) * 100;
            
            // Canara character-level extraction produces ~1064 chars, should compress to ~83 spans
            assertTrue(compressionRatio > 85.0, "Compression ratio should be > 85% (actual: " + compressionRatio + "%)");
            assertTrue(charTokens.size() > 1000, "Should extract > 1000 character tokens");
            assertTrue(spans.size() < 100, "Should compress to < 100 spans");
        }
    }

    private List<CharToken> extractCharacterTokens(PDDocument document, int pageIndex) throws IOException {
        List<CharToken> tokens = new ArrayList<>();
        
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                for (TextPosition pos : textPositions) {
                    String character = pos.getUnicode();
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

    private List<TextSpan> normalizeTokens(List<CharToken> charTokens, NormalizationConfig config) {
        if (charTokens.isEmpty()) return Collections.emptyList();
        
        List<TextSpan> spans = new ArrayList<>();
        
        Map<Long, List<CharToken>> rowMap = charTokens.stream()
            .collect(Collectors.groupingBy(t -> Math.round(t.y / config.rowGroupingTolerance)));
        
        for (List<CharToken> rowTokens : rowMap.values()) {
            rowTokens.sort(Comparator.comparingDouble(t -> t.x));
            
            StringBuilder spanBuilder = new StringBuilder();
            double spanStartX = 0, spanEndX = 0, spanY = 0, spanHeight = 0;
            int spanPage = 0, spanCharCount = 0;
            CharToken prevToken = null;
            
            for (CharToken token : rowTokens) {
                if (token.text.trim().isEmpty()) {
                    if (spanCharCount > 0) {
                        spans.add(new TextSpan(spanBuilder.toString(), spanStartX, spanEndX, spanY, spanHeight, spanPage, spanCharCount));
                        spanBuilder.setLength(0);
                        spanCharCount = 0;
                        prevToken = null;
                    }
                    continue;
                }
                
                boolean startNewSpan = false;
                
                if (prevToken == null) {
                    startNewSpan = true;
                } else {
                    double xGap = token.x - prevToken.rightEdge();
                    double yGap = Math.abs(token.y - prevToken.y);
                    
                    if (xGap > config.maxCharGapX || yGap > config.maxCharGapY) {
                        spans.add(new TextSpan(spanBuilder.toString(), spanStartX, spanEndX, spanY, spanHeight, spanPage, spanCharCount));
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
            
            if (spanCharCount > 0) {
                spans.add(new TextSpan(spanBuilder.toString(), spanStartX, spanEndX, spanY, spanHeight, spanPage, spanCharCount));
            }
        }
        
        spans.sort(Comparator.comparingDouble((TextSpan s) -> s.y).thenComparingDouble(s -> s.startX));
        return spans;
    }

    private boolean isValidDate(String dateText) {
        if (!dateText.matches("\\d{2}-\\d{2}-\\d{4}")) return false;
        
        String[] parts = dateText.split("-");
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = Integer.parseInt(parts[2]);
        
        if (year < 2020 || year > 2099) return false;
        if (month < 1 || month > 12) return false;
        
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (month == 2 && isLeapYear(year)) daysInMonth[1] = 29;
        
        return day >= 1 && day <= daysInMonth[month - 1];
    }

    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}
