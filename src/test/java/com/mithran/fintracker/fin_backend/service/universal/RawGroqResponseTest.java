package com.mithran.fintracker.fin_backend.service.universal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Test to capture the RAW Groq API response for diagnosis.
 * Shows exactly what the LLM returns before any parsing.
 * Only runs when explicitly requested via -Drun.live.groq.tests=true.
 */
@EnabledIfSystemProperty(named = "run.live.groq.tests", matches = "true")
public class RawGroqResponseTest {

    private static final String KOTAK_PDF = "bank_stmnt/kotak_chandan.pdf";
    private static final int MAX_CHUNK_SIZE = 8000;

    private GroqLlmClient createClient() throws IOException {
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
    void captureRawGroqResponse() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RAW GROQ RESPONSE CAPTURE TEST");
        System.out.println("=".repeat(80));

        File file = new File(KOTAK_PDF);
        if (!file.exists()) {
            System.out.println("⚠️ PDF not found: " + KOTAK_PDF);
            return;
        }

        GroqLlmClient llmClient = createClient();
        
        if (!llmClient.isAvailable()) {
            System.out.println("⚠️ Groq API key not configured");
            return;
        }

        // Extract first chunk
        String fullText;
        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            fullText = stripper.getText(doc);
        }

        String header = extractHeader(fullText);
        String firstChunk = extractFirstChunk(fullText);
        String chunkWithHeader = header + "\n...\n" + firstChunk;
        
        System.out.println("Chunk length: " + chunkWithHeader.length() + " characters");

        // Call LLM directly
        System.out.println("\n📤 Calling Groq API...");
        long startTime = System.currentTimeMillis();
        
        String rawResponse;
        try {
            rawResponse = llmClient.requestStructuredParse(chunkWithHeader);
        } catch (Exception e) {
            System.err.println("\n❌ API call failed: " + e.getMessage());
            if (e.getMessage().contains("429")) {
                System.err.println("   Rate limit hit - wait 60 seconds and retry");
            }
            throw e;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✅ API responded in " + duration + "ms");
        System.out.println("   Response length: " + rawResponse.length() + " characters");

        // Show response structure
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RAW RESPONSE PREVIEW (first 2000 chars):");
        System.out.println("=".repeat(80));
        System.out.println(rawResponse.substring(0, Math.min(2000, rawResponse.length())));
        if (rawResponse.length() > 2000) {
            System.out.println("\n... [" + (rawResponse.length() - 2000) + " more characters] ...");
        }

        // Check for key markers
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESPONSE STRUCTURE ANALYSIS:");
        System.out.println("=".repeat(80));
        System.out.println("Contains \"choices\": " + (rawResponse.contains("\"choices\"") ? "✅ YES" : "❌ NO"));
        System.out.println("Contains \"content\": " + (rawResponse.contains("\"content\"") ? "✅ YES" : "❌ NO"));
        System.out.println("Contains \"[\" array: " + (rawResponse.contains("[") ? "✅ YES" : "❌ NO"));
        System.out.println("Contains \"date\" field: " + (rawResponse.contains("\"date\"") ? "✅ YES" : "❌ NO"));
        System.out.println("Contains \"amount\" field: " + (rawResponse.contains("\"amount\"") ? "✅ YES" : "❌ NO"));
        
        // Try to find content field position
        if (rawResponse.contains("\"content\":")) {
            int contentPos = rawResponse.indexOf("\"content\":");
            System.out.println("\n\"content\" field found at position: " + contentPos);
            
            // Show 500 chars after "content":
            int startPreview = contentPos;
            int endPreview = Math.min(contentPos + 500, rawResponse.length());
            System.out.println("\nContent field preview (500 chars):");
            System.out.println(rawResponse.substring(startPreview, endPreview));
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST COMPLETE - Response captured for diagnosis");
        System.out.println("=".repeat(80));
    }

    private String extractHeader(String fullText) {
        int headerEnd = Math.min(500, fullText.length());
        String header = fullText.substring(0, headerEnd);
        
        String[] lines = header.split("\\r?\\n");
        StringBuilder headerBuilder = new StringBuilder();
        for (String line : lines) {
            headerBuilder.append(line).append("\n");
            if (line.matches(".*\\d{2}[/\\-\\s]\\d{2}[/\\-\\s]\\d{2,4}.*")) {
                break;
            }
        }
        
        return headerBuilder.toString();
    }

    private String extractFirstChunk(String fullText) {
        String[] lines = fullText.split("\\r?\\n");
        StringBuilder chunk = new StringBuilder();
        
        for (String line : lines) {
            if (chunk.length() + line.length() + 1 > MAX_CHUNK_SIZE) {
                break;
            }
            chunk.append(line).append("\n");
        }
        
        return chunk.toString();
    }
}
