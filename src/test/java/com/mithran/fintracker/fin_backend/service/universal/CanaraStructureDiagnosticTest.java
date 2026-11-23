package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Diagnostic to understand Canara PDF structure without assumptions.
 * Dumps raw token data to identify why Kotak logic failed.
 */
public class CanaraStructureDiagnosticTest {

    private static final String CANARA_PDF = "bank_stmnt/canara_chandhan.pdf";
    
    @Test
    public void dumpCanaraStructure() throws IOException {
        File pdfFile = new File(CANARA_PDF);
        
        System.out.println("====================================================================================================");
        System.out.println("CANARA BANK STATEMENT STRUCTURE DIAGNOSTIC");
        System.out.println("====================================================================================================");
        
        try (PDDocument document = PDDocument.load(pdfFile)) {
            System.out.println("✓ PDF loaded: " + pdfFile.getName());
            System.out.println("   Total pages: " + document.getNumberOfPages());
            System.out.println();
            
            // Test first 3 pages to see structure variation
            for (int pageNum = 0; pageNum < Math.min(3, document.getNumberOfPages()); pageNum++) {
                System.out.println("====================================================================================================");
                System.out.println("PAGE " + (pageNum + 1) + " ANALYSIS");
                System.out.println("====================================================================================================");
                
                PDFTextStripper stripper = new TokenExtractor(pageNum + 1);
                stripper.getText(document);
                
                System.out.println();
            }
        }
        
        System.out.println("====================================================================================================");
        System.out.println("DIAGNOSTIC COMPLETE");
        System.out.println("====================================================================================================");
    }
    
    private static class TokenExtractor extends PDFTextStripper {
        private final int targetPage;
        private final List<Token> tokens = new ArrayList<>();
        
        public TokenExtractor(int page) throws IOException {
            this.targetPage = page;
            this.setStartPage(page);
            this.setEndPage(page);
        }
        
        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            for (TextPosition pos : positions) {
                String t = pos.getUnicode().trim();
                if (!t.isEmpty()) {
                    tokens.add(new Token(
                        t,
                        Math.round(pos.getX() * 100.0) / 100.0,
                        Math.round(pos.getY() * 100.0) / 100.0
                    ));
                }
            }
        }
        
        @Override
        public String getText(org.apache.pdfbox.pdmodel.PDDocument doc) throws IOException {
            String result = super.getText(doc);
            analyzeTokens();
            return result;
        }
        
        private void analyzeTokens() {
            System.out.println("Total tokens extracted: " + tokens.size());
            System.out.println();
            
            // Group by Y coordinate to show rows
            Map<Double, List<Token>> rowMap = new TreeMap<>();
            for (Token t : tokens) {
                double roundedY = Math.round(t.y / 5.0) * 5.0; // Group within 5 pixels
                rowMap.computeIfAbsent(roundedY, k -> new ArrayList<>()).add(t);
            }
            
            System.out.println("FIRST 15 ROWS (grouped by Y-coordinate):");
            System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");
            
            int rowCount = 0;
            for (Map.Entry<Double, List<Token>> entry : rowMap.entrySet()) {
                if (rowCount++ >= 15) break;
                
                double y = entry.getKey();
                List<Token> row = entry.getValue();
                row.sort(Comparator.comparingDouble(t -> t.x));
                
                System.out.printf("Y=%-6.1f | ", y);
                for (Token t : row) {
                    System.out.printf("[X=%-6.1f: %-15s] ", t.x, 
                        t.text.length() > 15 ? t.text.substring(0, 12) + "..." : t.text);
                }
                System.out.println();
            }
            
            System.out.println();
            System.out.println("Y-COORDINATE RANGES:");
            System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");
            double minY = tokens.stream().mapToDouble(t -> t.y).min().orElse(0);
            double maxY = tokens.stream().mapToDouble(t -> t.y).max().orElse(0);
            System.out.printf("Min Y: %.2f, Max Y: %.2f, Range: %.2f\n", minY, maxY, maxY - minY);
            
            // Find potential headers (Y < 150 was Kotak assumption)
            System.out.println();
            System.out.println("TOKENS IN HEADER REGION (Y < 150):");
            System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");
            tokens.stream()
                .filter(t -> t.y < 150)
                .sorted(Comparator.comparingDouble((Token t) -> t.y).thenComparing((Token t) -> t.x))
                .forEach(t -> System.out.printf("  Y=%-6.1f X=%-6.1f: %s\n", t.y, t.x, t.text));
            
            // Find date-like tokens
            System.out.println();
            System.out.println("DATE-LIKE TOKENS (DD-MMM pattern):");
            System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            Set<String> monthSet = new HashSet<>(Arrays.asList(months));
            
            tokens.stream()
                .filter(t -> t.text.matches("\\d{1,2}") || monthSet.contains(t.text))
                .sorted(Comparator.comparingDouble((Token t) -> t.y).thenComparing((Token t) -> t.x))
                .limit(30)
                .forEach(t -> System.out.printf("  Y=%-6.1f X=%-6.1f: %s\n", t.y, t.x, t.text));
            
            // Find numeric tokens (potential amounts)
            System.out.println();
            System.out.println("NUMERIC TOKENS (with commas/decimals):");
            System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");
            tokens.stream()
                .filter(t -> t.text.matches("[\\d,]+\\.\\d{2}"))
                .sorted(Comparator.comparingDouble((Token t) -> t.y).thenComparing((Token t) -> t.x))
                .limit(30)
                .forEach(t -> System.out.printf("  Y=%-6.1f X=%-6.1f: %s\n", t.y, t.x, t.text));
            
            // X-coordinate clustering
            System.out.println();
            System.out.println("X-COORDINATE CLUSTERS (potential columns):");
            System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");
            List<Double> xCoords = tokens.stream()
                .map(t -> t.x)
                .distinct()
                .sorted()
                .toList();
            
            List<List<Double>> clusters = new ArrayList<>();
            List<Double> currentCluster = new ArrayList<>();
            currentCluster.add(xCoords.get(0));
            
            for (int i = 1; i < xCoords.size(); i++) {
                double gap = xCoords.get(i) - xCoords.get(i - 1);
                if (gap > 30) { // Same gap threshold as Kotak
                    clusters.add(new ArrayList<>(currentCluster));
                    currentCluster.clear();
                }
                currentCluster.add(xCoords.get(i));
            }
            if (!currentCluster.isEmpty()) {
                clusters.add(currentCluster);
            }
            
            System.out.println("Found " + clusters.size() + " X-coordinate clusters:");
            for (int i = 0; i < clusters.size(); i++) {
                List<Double> cluster = clusters.get(i);
                double min = cluster.stream().mapToDouble(d -> d).min().orElse(0);
                double max = cluster.stream().mapToDouble(d -> d).max().orElse(0);
                long tokenCount = tokens.stream().filter(t -> t.x >= min && t.x <= max).count();
                System.out.printf("  Cluster %d: X ∈ [%.2f, %.2f], Tokens: %d\n", i + 1, min, max, tokenCount);
            }
        }
    }
    
    private static class Token {
        String text;
        double x;
        double y;
        
        Token(String text, double x, double y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}
