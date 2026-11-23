package com.mithran.fintracker.fin_backend.service.universal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

/**
 * Groq API implementation of UniversalStatementLlmClient.
 * Follows the existing InsightsController pattern for HTTP communication.
 */
@Component
public class GroqLlmClient implements UniversalStatementLlmClient {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "openai/gpt-oss-20b"; // Production model: 1000 T/sec, $0.075/$0.30 per 1M tokens
    private static final int TIMEOUT_MS = 60000; // 60 seconds
    private static final int MAX_CHUNK_SIZE = 8000; // Characters per chunk (conservative for token limits)
    private static final int CHUNK_OVERLAP = 500; // Overlap to avoid splitting transactions

    @Override
    public String requestStructuredParse(String statementText) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Groq API key not configured");
        }

        // Check if statement needs chunking
        if (statementText.length() <= MAX_CHUNK_SIZE) {
            return requestSingleParse(statementText);
        } else {
            return requestChunkedParse(statementText);
        }
    }

    /**
     * Parse statement in a single API call.
     */
    private String requestSingleParse(String statementText) throws Exception {
        String prompt = buildParsingPrompt(statementText, false);
        return makeApiCall(prompt);
    }

    /**
     * Parse long statement in chunks and merge results.
     */
    private String requestChunkedParse(String statementText) throws Exception {
        // Extract header (first 500 chars for context)
        String header = extractStatementHeader(statementText);
        
        // Split into chunks with overlap
        java.util.List<String> chunks = splitIntoChunks(statementText);
        
        // Parse each chunk
        java.util.List<String> chunkResponses = new java.util.ArrayList<>();
        java.util.List<Integer> failedChunks = new java.util.ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String chunkWithHeader = header + "\n...\n" + chunk;
            String prompt = buildParsingPrompt(chunkWithHeader, true);
            
            try {
                String response = makeApiCall(prompt);
                chunkResponses.add(response);
            } catch (Exception e) {
                // Log error but continue with other chunks
                System.err.println("Error parsing chunk " + (i + 1) + "/" + chunks.size() + ": " + e.getMessage());
                chunkResponses.add(null); // Preserve position
                failedChunks.add(i + 1);
            }
        }
        
        // Check if all chunks failed
        if (chunkResponses.stream().allMatch(r -> r == null)) {
            throw new RuntimeException("All " + chunks.size() + " chunks failed to parse");
        }
        
        // Merge all successful chunks
        return mergeChunkResponses(chunkResponses, failedChunks);
    }
    
    /**
     * Merge multiple chunk responses into a single response.
     * Extracts JSON arrays from each response and combines them.
     */
    private String mergeChunkResponses(java.util.List<String> responses, java.util.List<Integer> failedChunks) {
        java.util.List<String> allTransactionJsons = new java.util.ArrayList<>();
        
        for (String response : responses) {
            if (response == null) {
                continue; // Skip failed chunks
            }
            
            // Extract JSON array from this response
            String jsonArray = extractJsonArray(response);
            if (jsonArray != null && !jsonArray.trim().equals("[]")) {
                // Extract individual transaction objects from array
                java.util.List<String> transactions = extractTransactionsFromArray(jsonArray);
                allTransactionJsons.addAll(transactions);
            }
        }
        
        // Build merged response in Groq API format
        StringBuilder mergedJson = new StringBuilder();
        mergedJson.append("{\"choices\":[{\"message\":{\"content\":\"[");
        
        for (int i = 0; i < allTransactionJsons.size(); i++) {
            if (i > 0) {
                mergedJson.append(",");
            }
            mergedJson.append(escapeJson(allTransactionJsons.get(i)));
        }
        
        mergedJson.append("]\"}}]}");
        
        // Add warning about failed chunks if any
        if (!failedChunks.isEmpty()) {
            System.err.println("Warning: " + failedChunks.size() + " chunk(s) failed: " + failedChunks);
        }
        
        return mergedJson.toString();
    }
    
    /**
     * Extract JSON array from Groq API response.
     */
    private String extractJsonArray(String response) {
        // Look for JSON array in response
        int arrayStart = response.indexOf('[');
        int arrayEnd = response.lastIndexOf(']');
        
        if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
            return response.substring(arrayStart, arrayEnd + 1);
        }
        
        // Try to extract from "content" field (Groq format)
        String marker = "\"content\":\"";
        int start = response.indexOf(marker);
        if (start != -1) {
            start += marker.length();
            // Find the JSON array in the content
            int contentArrayStart = response.indexOf('[', start);
            if (contentArrayStart != -1) {
                int contentArrayEnd = findMatchingBracket(response, contentArrayStart);
                if (contentArrayEnd != -1) {
                    String arrayContent = response.substring(contentArrayStart, contentArrayEnd + 1);
                    // Unescape JSON escapes
                    return arrayContent.replace("\\\"", "\"").replace("\\n", "\n");
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find matching closing bracket for an opening bracket.
     */
    private int findMatchingBracket(String text, int openPos) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Extract individual transaction JSON objects from a JSON array string.
     */
    private java.util.List<String> extractTransactionsFromArray(String jsonArray) {
        java.util.List<String> transactions = new java.util.ArrayList<>();
        
        // Remove outer brackets
        String content = jsonArray.trim();
        if (content.startsWith("[")) {
            content = content.substring(1);
        }
        if (content.endsWith("]")) {
            content = content.substring(0, content.length() - 1);
        }
        content = content.trim();
        
        if (content.isEmpty()) {
            return transactions;
        }
        
        // Parse individual objects
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        StringBuilder currentObject = new StringBuilder();
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (escaped) {
                currentObject.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                currentObject.append(c);
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                currentObject.append(c);
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    depth++;
                    currentObject.append(c);
                } else if (c == '}') {
                    depth--;
                    currentObject.append(c);
                    if (depth == 0) {
                        // Complete object found
                        transactions.add(currentObject.toString().trim());
                        currentObject = new StringBuilder();
                    }
                } else if (c == ',' && depth == 0) {
                    // Skip comma between objects
                    continue;
                } else {
                    currentObject.append(c);
                }
            } else {
                currentObject.append(c);
            }
        }
        
        // Add last object if any
        if (currentObject.length() > 0 && currentObject.toString().trim().length() > 0) {
            transactions.add(currentObject.toString().trim());
        }
        
        return transactions;
    }

    /**
     * Make actual API call to Groq.
     */
    private String makeApiCall(String prompt) throws Exception {
        URL url = new URL(GROQ_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        String safePrompt = escapeJson(prompt);
        String requestBody = "{\"model\":\"" + MODEL + "\"," +
                "\"messages\":[{" +
                "\"role\":\"user\"," +
                "\"content\":\"" + safePrompt + "\"}]}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes("UTF-8"));
        }

        int status = conn.getResponseCode();

        InputStream stream;
        if (status >= 200 && status < 300) {
            stream = conn.getInputStream();
        } else {
            stream = conn.getErrorStream();
        }

        String response = stream != null 
                ? new String(stream.readAllBytes(), "UTF-8") 
                : "";

        if (status != 200) {
            throw new RuntimeException("Groq API error " + status + ": " + 
                    (response.length() > 200 ? response.substring(0, 200) : response));
        }

        return response;
    }

    /**
     * Extract header section for context in chunked parsing.
     */
    private String extractStatementHeader(String statementText) {
        int headerEnd = Math.min(500, statementText.length());
        String header = statementText.substring(0, headerEnd);
        
        // Try to find end of header (first transaction line)
        String[] lines = header.split("\\r?\\n");
        StringBuilder headerBuilder = new StringBuilder();
        for (String line : lines) {
            headerBuilder.append(line).append("\n");
            // Stop at first line that looks like a transaction (has date pattern)
            if (line.matches(".*\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{2,4}.*")) {
                break;
            }
        }
        
        return headerBuilder.toString();
    }

    /**
     * Split statement into overlapping chunks.
     */
    private java.util.List<String> splitIntoChunks(String statementText) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        String[] lines = statementText.split("\\r?\\n");
        
        StringBuilder currentChunk = new StringBuilder();
        int currentSize = 0;
        
        for (String line : lines) {
            int lineSize = line.length() + 1; // +1 for newline
            
            // If adding this line exceeds chunk size and we have content, finalize chunk
            if (currentSize + lineSize > MAX_CHUNK_SIZE && currentSize > 0) {
                chunks.add(currentChunk.toString());
                
                // Start new chunk with overlap (last few lines)
                currentChunk = new StringBuilder();
                currentSize = 0;
                
                // Add overlap from previous chunk
                String[] prevLines = chunks.get(chunks.size() - 1).split("\\r?\\n");
                int overlapLines = 0;
                int overlapSize = 0;
                for (int i = prevLines.length - 1; i >= 0 && overlapSize < CHUNK_OVERLAP; i--) {
                    overlapSize += prevLines[i].length() + 1;
                    overlapLines++;
                }
                
                for (int i = prevLines.length - overlapLines; i < prevLines.length; i++) {
                    currentChunk.append(prevLines[i]).append("\n");
                    currentSize += prevLines[i].length() + 1;
                }
            }
            
            currentChunk.append(line).append("\n");
            currentSize += lineSize;
        }
        
        // Add final chunk
        if (currentSize > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return chunks;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private String buildParsingPrompt(String statementText, boolean isChunk) {
        return "You are an expert bank statement parser. Extract ALL transactions from this bank statement.\n\n" +
                
                "JSON FORMAT (return ONLY this, no markdown, no explanations):\n" +
                "[\n" +
                "  {\n" +
                "    \"date\": \"YYYY-MM-DD\",\n" +
                "    \"narration\": \"complete transaction description\",\n" +
                "    \"amount\": \"1234.56\",\n" +
                "    \"direction\": \"DEBIT\" or \"CREDIT\" or \"UNKNOWN\",\n" +
                "    \"balance\": \"12345.67\",\n" +
                "    \"reference\": \"transaction reference if present\"\n" +
                "  }\n" +
                "]\n\n" +
                
                "CRITICAL RULES:\n" +
                "1. MULTI-LINE TRANSACTIONS: Many transactions span multiple lines. The date and amount " +
                "may be on separate lines. Combine all lines belonging to one transaction.\n\n" +
                
                "2. TRANSACTION vs BALANCE: Statements have TWO types of amounts:\n" +
                "   - Transaction amount (debit/credit/withdrawal/deposit)\n" +
                "   - Running balance (rightmost column, cumulative)\n" +
                "   Extract the TRANSACTION amount, not the balance. Include balance in 'balance' field.\n\n" +
                
                "3. DIRECTION DETECTION:\n" +
                "   - DEBIT: withdrawal, debit, DR, paid, payment, transfer out, negative sign, left column\n" +
                "   - CREDIT: deposit, credit, CR, received, salary, transfer in, right column\n" +
                "   - UNKNOWN: if truly ambiguous after checking all context\n" +
                "   Use column positions, headers (Debit/Credit, Withdrawal/Deposit), and keywords.\n\n" +
                
                "4. EXCLUDE NON-TRANSACTIONS:\n" +
                "   - Opening Balance / Closing Balance rows (these are NOT transactions)\n" +
                "   - Page numbers, headers, footers, bank names\n" +
                "   - Summary totals, subtotals\n" +
                "   - Lines with only '---' or '==='\n\n" +
                
                "5. DATE HANDLING:\n" +
                "   - Parse formats: dd/MM/yyyy, dd-MM-yyyy, dd MMM yyyy, dd.MM.yyyy\n" +
                "   - Convert to YYYY-MM-DD format\n" +
                "   - If date appears in narration (e.g., 'Invoice dated 01/02/2024'), " +
                "use the TRANSACTION date, not the date in description\n\n" +
                
                "6. AMOUNT HANDLING:\n" +
                "   - Parse Indian format: 1,25,000.00 = 125000.00\n" +
                "   - Remove commas and currency symbols\n" +
                "   - Use positive numbers only (direction indicates debit/credit)\n" +
                "   - If a line has multiple amounts, transaction amount is usually NOT the rightmost\n\n" +
                
                "7. NARRATION:\n" +
                "   - Combine multi-line descriptions into single narration\n" +
                "   - Include UPI references, merchant names, transaction details\n" +
                "   - Preserve useful reference numbers\n\n" +
                
                "8. PRESERVE ORDER: Extract transactions in the order they appear.\n\n" +
                
                "9. NO HALLUCINATION: If data is missing or unclear, use null for optional fields. " +
                "Never invent dates, amounts, or transaction details.\n\n" +
                
                (isChunk ? "NOTE: This is a PARTIAL statement (chunk). Extract only transactions in this section.\n\n" : "") +
                
                "Statement text:\n" +
                statementText;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
