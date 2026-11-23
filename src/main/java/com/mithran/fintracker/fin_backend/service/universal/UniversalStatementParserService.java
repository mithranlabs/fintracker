package com.mithran.fintracker.fin_backend.service.universal;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal bank statement parser using deterministic heuristics with LLM fallback.
 * Does not modify existing parsers or database.
 */
@Service
public class UniversalStatementParserService {

    private final UniversalStatementLlmClient llmClient;

    // Date patterns: dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy, dd MMM yyyy
    private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    );

    // Transaction line patterns
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{2,4}|\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4})\\b"
    );

    // Indian number format: 1,25,000.00 or 125000.00
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "\\b([\\d,]+\\.\\d{2})\\b"
    );

    // Debit/Credit indicators
    private static final Pattern DEBIT_INDICATOR = Pattern.compile(
            "\\b(?:DR|DEBIT|WDL|WITHDRAWAL|PAID|PMT)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CREDIT_INDICATOR = Pattern.compile(
            "\\b(?:CR|CREDIT|DEP|DEPOSIT|RECEIVED|REC)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // UPI transaction pattern
    private static final Pattern UPI_PATTERN = Pattern.compile(
            "UPI[/-](?:DR|CR|REVERSAL)[/-]\\d+[/-]([^/]+)"
    );

    // Minimum confidence threshold for heuristic parsing
    private static final double MIN_HEURISTIC_CONFIDENCE = 0.7;

    public UniversalStatementParserService(UniversalStatementLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Parse extracted statement text using LLM first, heuristics as fallback.
     */
    public UniversalStatementParseResult parseText(String extractedText) {
        UniversalStatementParseResult result = new UniversalStatementParseResult();

        // Stage A: Input validation
        if (!validateInput(extractedText, result)) {
            return result;
        }

        // Stage B: Try LLM parsing first (if available)
        if (llmClient.isAvailable()) {
            result = attemptLlmParsing(extractedText);
            
            // If LLM parsing succeeded with reasonable confidence, return
            if (result.isSuccessful() && result.getOverallConfidence() >= 0.6) {
                return result;
            }
            
            // LLM failed or low confidence, will try heuristics
            if (!result.isSuccessful()) {
                result.addWarning("LLM parsing failed, falling back to heuristic parsing");
            } else {
                result.addWarning("LLM parsing had low confidence, trying heuristic parsing");
            }
        } else {
            result.addWarning("LLM client unavailable (API key not configured), using heuristic parsing");
        }

        // Stage C: Heuristic fallback
        UniversalStatementParseResult heuristicResult = attemptHeuristicParsing(extractedText);
        
        // Choose the better result between LLM and heuristic
        if (llmClient.isAvailable() && result.getTransactions().size() > 0) {
            // Compare LLM vs heuristic results
            if (heuristicResult.isSuccessful() && 
                heuristicResult.getTransactions().size() > result.getTransactions().size()) {
                // Heuristic found more transactions, use it
                heuristicResult.addWarning("Using heuristic results (found more transactions than LLM)");
                return heuristicResult;
            } else if (heuristicResult.isSuccessful() && 
                       heuristicResult.getOverallConfidence() > result.getOverallConfidence() + 0.2) {
                // Heuristic has significantly higher confidence
                heuristicResult.addWarning("Using heuristic results (higher confidence than LLM)");
                return heuristicResult;
            } else {
                // Stick with LLM results
                result.addWarning("Using LLM results despite heuristic fallback");
                return result;
            }
        } else {
            // LLM not available or produced no results, use heuristic
            if (heuristicResult.isSuccessful()) {
                return heuristicResult;
            }
        }

        // Both methods failed
        result.setDetectionMethod(UniversalStatementParseResult.DetectionMethod.FAILED);
        result.setRequiresManualReview(true);
        result.getWarnings().addAll(heuristicResult.getWarnings());
        
        if (result.getTransactions().isEmpty() && heuristicResult.getTransactions().isEmpty()) {
            result.addWarning("No transactions could be extracted from statement");
        }

        return result;
    }

    /**
     * Stage A: Validate input text.
     */
    private boolean validateInput(String text, UniversalStatementParseResult result) {
        if (text == null || text.trim().isEmpty()) {
            result.addWarning("Input text is null or empty");
            result.setDetectionMethod(UniversalStatementParseResult.DetectionMethod.FAILED);
            // Even if validation fails, note if LLM is unavailable
            if (!llmClient.isAvailable()) {
                result.addWarning("LLM client unavailable (API key not configured)");
            }
            return false;
        }

        if (text.trim().length() < 50) {
            result.addWarning("Input text too short to contain transactions");
            result.setDetectionMethod(UniversalStatementParseResult.DetectionMethod.FAILED);
            // Even if validation fails, note if LLM is unavailable
            if (!llmClient.isAvailable()) {
                result.addWarning("LLM client unavailable (API key not configured)");
            }
            return false;
        }

        // Check if text contains at least some numeric content
        // If no digits but LLM is available, allow it through for LLM to try
        boolean hasDigits = text.contains("0") || text.contains("1") || text.contains("2") || 
            text.contains("3") || text.contains("4") || text.contains("5") ||
            text.contains("6") || text.contains("7") || text.contains("8") || text.contains("9");
            
        if (!hasDigits) {
            if (llmClient.isAvailable()) {
                result.addWarning("Input text contains no numeric data, attempting LLM parsing");
                return true; // Let LLM try
            } else {
                result.addWarning("Input text contains no numeric data");
                result.addWarning("No transactions could be extracted from statement");
                result.setDetectionMethod(UniversalStatementParseResult.DetectionMethod.FAILED);
                result.setRequiresManualReview(true);
                result.addWarning("LLM client unavailable (API key not configured)");
                return false;
            }
        }

        return true;
    }

    /**
     * Stage B: Attempt heuristic parsing.
     */
    private UniversalStatementParseResult attemptHeuristicParsing(String text) {
        UniversalStatementParseResult result = new UniversalStatementParseResult();
        result.setDetectionMethod(UniversalStatementParseResult.DetectionMethod.HEURISTIC);

        String[] lines = text.split("\\r?\\n");
        List<UniversalStatementTransaction> candidates = new ArrayList<>();
        
        // Try to detect column-based layout
        int debitColumnIndex = -1;
        int creditColumnIndex = -1;
        
        for (int i = 0; i < lines.length && i < 5; i++) {
            String line = lines[i].toLowerCase();
            // Check for debit/credit columns
            if (line.contains("debit") && line.contains("credit")) {
                debitColumnIndex = line.indexOf("debit");
                creditColumnIndex = line.indexOf("credit");
                break;
            }
            // Also check for withdrawal/deposit columns
            if (line.contains("withdrawal") && line.contains("deposit")) {
                debitColumnIndex = line.indexOf("withdrawal");
                creditColumnIndex = line.indexOf("deposit");
                break;
            }
        }

        // First pass: Try single-line parsing (backward compatible)
        List<UniversalStatementTransaction> singleLineCandidates = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.isEmpty() || isHeaderFooterLine(line)) {
                continue;
            }

            UniversalStatementTransaction txn = parseTransactionLine(line, i, debitColumnIndex, creditColumnIndex);
            if (txn != null) {
                singleLineCandidates.add(txn);
            }
        }

        // Second pass: Try multi-line parsing
        List<UniversalStatementTransaction> multiLineCandidates = parseMultiLineTransactions(lines, debitColumnIndex, creditColumnIndex);

        // Combine strategies: use the one that found more valid transactions
        // This supports hybrid statements without a hard threshold
        List<UniversalStatementTransaction> validatedSingleLine = validateCandidates(singleLineCandidates, new UniversalStatementParseResult());
        List<UniversalStatementTransaction> validatedMultiLine = validateCandidates(multiLineCandidates, new UniversalStatementParseResult());

        // Choose strategy based on validated transaction count
        List<UniversalStatementTransaction> finalCandidates;
        if (validatedSingleLine.size() >= validatedMultiLine.size() && validatedSingleLine.size() > 0) {
            finalCandidates = singleLineCandidates;  // Use single-line
        } else if (validatedMultiLine.size() > 0) {
            finalCandidates = multiLineCandidates;  // Use multi-line
        } else {
            // Try to combine both if neither alone is sufficient
            finalCandidates = deduplicateTransactions(singleLineCandidates, multiLineCandidates);
        }

        // Final validation
        List<UniversalStatementTransaction> validated = validateCandidates(finalCandidates, result);
        result.setTransactions(validated);

        // Calculate confidence
        double confidence = calculateHeuristicConfidence(validated, lines.length);
        result.setOverallConfidence(confidence);
        result.setRequiresManualReview(confidence < 0.8 || validated.size() < 2);

        return result;
    }

    /**
     * Deduplicate transactions from multiple strategies.
     * Keeps transactions that appear unique by line number.
     */
    private List<UniversalStatementTransaction> deduplicateTransactions(
            List<UniversalStatementTransaction> list1,
            List<UniversalStatementTransaction> list2) {
        
        List<UniversalStatementTransaction> combined = new ArrayList<>(list1);
        Set<String> seenSources = new HashSet<>();
        
        // Track line numbers from first list
        for (UniversalStatementTransaction txn : list1) {
            if (txn.getSourceText() != null) {
                seenSources.add(txn.getSourceText());
            }
        }
        
        // Add from second list only if not duplicate
        for (UniversalStatementTransaction txn : list2) {
            if (txn.getSourceText() != null && !seenSources.contains(txn.getSourceText())) {
                combined.add(txn);
                seenSources.add(txn.getSourceText());
            }
        }
        
        return combined;
    }

    /**
     * Parse transactions that span multiple lines.
     * Accumulates lines between date markers and extracts transactions from the accumulated buffer.
     */
    private List<UniversalStatementTransaction> parseMultiLineTransactions(String[] lines, int debitCol, int creditCol) {
        List<UniversalStatementTransaction> transactions = new ArrayList<>();
        StringBuilder transactionBuffer = new StringBuilder();
        String firstLine = null; // Preserve first line for column-based direction detection
        LocalDate currentDate = null;
        int startLineNumber = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.isEmpty() || isHeaderFooterLine(line)) {
                continue;
            }

            LocalDate lineDate = extractDate(line);
            
            // If we found a new date, process the previous accumulated transaction
            if (lineDate != null) {
                if (currentDate != null && transactionBuffer.length() > 0) {
                    UniversalStatementTransaction txn = parseMultiLineTransaction(
                        firstLine, transactionBuffer.toString(), currentDate, startLineNumber, debitCol, creditCol);
                    if (txn != null) {
                        transactions.add(txn);
                    }
                }
                
                // Start a new transaction
                currentDate = lineDate;
                startLineNumber = i;
                firstLine = line; // Preserve the original first line
                transactionBuffer = new StringBuilder(line);
            } else {
                // Accumulate lines for the current transaction
                if (currentDate != null) {
                    transactionBuffer.append(" ").append(line);
                }
            }
        }

        // Process the last accumulated transaction
        if (currentDate != null && transactionBuffer.length() > 0) {
            UniversalStatementTransaction txn = parseMultiLineTransaction(
                firstLine, transactionBuffer.toString(), currentDate, startLineNumber, debitCol, creditCol);
            if (txn != null) {
                transactions.add(txn);
            }
        }

        return transactions;
    }

    /**
     * Parse a transaction from accumulated multi-line text.
     * 
     * @param firstLine The original first line (with correct column positions for direction detection)
     * @param accumulatedText The full accumulated text (for narration and reference extraction)
     */
    private UniversalStatementTransaction parseMultiLineTransaction(
            String firstLine, String accumulatedText, LocalDate date, int lineNumber, int debitCol, int creditCol) {
        
        // Extract amount from the first line (which has correct column positions)
        BigDecimal amount = extractAmount(firstLine, debitCol, creditCol);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        UniversalStatementTransaction txn = new UniversalStatementTransaction();
        txn.setTransactionDate(date);
        txn.setAmount(amount);
        
        // Determine direction using the FIRST LINE (preserves column positions)
        UniversalStatementTransaction.Direction direction = determineDirectionWithColumns(firstLine, debitCol, creditCol);
        if (direction == UniversalStatementTransaction.Direction.UNKNOWN) {
            // Fall back to pattern-based detection on accumulated text
            direction = determineDirection(accumulatedText);
        }
        
        txn.setDirection(direction);
        // Use accumulated text for narration (includes continuation lines)
        txn.setNarration(extractNarration(accumulatedText));
        txn.setTransactionReference(extractReference(accumulatedText));
        txn.setSourceText("Line " + lineNumber + ": " + 
                (accumulatedText.length() > 80 ? accumulatedText.substring(0, 80) + "..." : accumulatedText));
        txn.setConfidenceScore(0.75); // Slightly lower confidence for multi-line parsing

        return txn;
    }

    /**
     * Parse a single line as a transaction.
     */
    private UniversalStatementTransaction parseTransactionLine(String line, int lineNumber, int debitCol, int creditCol) {
        boolean debug = Boolean.getBoolean("parser.debug");
        
        if (debug) {
            System.out.println("\n=== Parsing line " + lineNumber + ": " + line);
        }
        
        // Need at least a date and an amount
        LocalDate date = extractDate(line);
        if (debug) {
            System.out.println("    Date: " + date);
        }
        if (date == null) {
            return null;
        }

        BigDecimal amount = extractAmount(line, debitCol, creditCol);
        if (debug) {
            System.out.println("    Amount: " + amount);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        UniversalStatementTransaction txn = new UniversalStatementTransaction();
        txn.setTransactionDate(date);
        txn.setAmount(amount);
        
        // Try column-based direction detection first
        UniversalStatementTransaction.Direction direction = determineDirectionWithColumns(line, debitCol, creditCol);
        if (direction == UniversalStatementTransaction.Direction.UNKNOWN) {
            // Fall back to pattern-based detection
            direction = determineDirection(line);
        }
        
        txn.setDirection(direction);
        txn.setNarration(extractNarration(line));
        txn.setTransactionReference(extractReference(line));
        txn.setSourceText("Line " + lineNumber + ": " + 
                (line.length() > 80 ? line.substring(0, 80) + "..." : line));
        txn.setConfidenceScore(0.8); // Base confidence for heuristic parsing

        if (debug) {
            System.out.println("    Direction: " + txn.getDirection());
            System.out.println("    Narration: " + txn.getNarration());
            System.out.println("    ✓ Transaction parsed successfully");
        }

        return txn;
    }
    
    /**
     * Determine direction using column positions.
     */
    private UniversalStatementTransaction.Direction determineDirectionWithColumns(String line, int debitCol, int creditCol) {
        if (debitCol == -1 || creditCol == -1) {
            return UniversalStatementTransaction.Direction.UNKNOWN;
        }
        
        // Find all amounts in the line
        Matcher matcher = AMOUNT_PATTERN.matcher(line);
        List<Integer> amountPositions = new ArrayList<>();
        
        while (matcher.find()) {
            amountPositions.add(matcher.start());
        }
        
        if (amountPositions.isEmpty()) {
            return UniversalStatementTransaction.Direction.UNKNOWN;
        }
        
        // Check which column the first amount is in (ignoring subsequent amounts like balance)
        int firstAmountPos = amountPositions.get(0);
        
        int distDebit = Math.abs(firstAmountPos - debitCol);
        int distCredit = Math.abs(firstAmountPos - creditCol);
        int tolerance = 20;
        
        // If neither column is within tolerance, position is unknown
        if (distDebit >= tolerance && distCredit >= tolerance) {
            return UniversalStatementTransaction.Direction.UNKNOWN;
        }
        
        // If distance difference is too small to reliably distinguish, do not guess
        if (Math.abs(distDebit - distCredit) < 3) {
            return UniversalStatementTransaction.Direction.UNKNOWN;
        }
        
        // Pick strictly closer column within tolerance
        if (distDebit < distCredit && distDebit < tolerance) {
            return UniversalStatementTransaction.Direction.DEBIT;
        } else if (distCredit < distDebit && distCredit < tolerance) {
            return UniversalStatementTransaction.Direction.CREDIT;
        }
        
        return UniversalStatementTransaction.Direction.UNKNOWN;
    }

    /**
     * Extract date from line.
     */
    private LocalDate extractDate(String line) {
        Matcher matcher = DATE_PATTERN.matcher(line);
        
        while (matcher.find()) {
            String dateStr = matcher.group(1);
            
            for (DateTimeFormatter formatter : DATE_FORMATS) {
                try {
                    return LocalDate.parse(dateStr, formatter);
                } catch (DateTimeParseException e) {
                    // Try next format
                }
            }
        }
        
        return null;
    }

    /**
     * Extract amount from line (Indian number format support).
     * Heuristic: Balance typically appears LAST (rightmost position).
     * Use column information if available, otherwise exclude rightmost amount.
     */
    private BigDecimal extractAmount(String line, int debitCol, int creditCol) {
        Matcher matcher = AMOUNT_PATTERN.matcher(line);
        
        List<BigDecimal> amounts = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        
        while (matcher.find()) {
            String amountStr = matcher.group(1).replace(",", "");
            try {
                BigDecimal amount = new BigDecimal(amountStr);
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    amounts.add(amount);
                    positions.add(matcher.start());
                }
            } catch (NumberFormatException e) {
                // Skip invalid amounts
            }
        }

        if (amounts.isEmpty()) {
            return null;
        }

        // If only one amount, return it
        if (amounts.size() == 1) {
            return amounts.get(0);
        }

        // If we have column information, prefer amount near debit/credit columns
        if (debitCol != -1 || creditCol != -1) {
            int tolerance = 30;
            
            for (int i = 0; i < positions.size(); i++) {
                int pos = positions.get(i);
                boolean matchesDebit = (debitCol != -1 && Math.abs(pos - debitCol) < tolerance);
                boolean matchesCredit = (creditCol != -1 && Math.abs(pos - creditCol) < tolerance);
                if (matchesDebit || matchesCredit) {
                    return amounts.get(i);
                }
            }
        }

        // Heuristic: Balance typically appears LAST and rightmost
        // Exclude the rightmost amount and take the first remaining one
        if (amounts.size() >= 2) {
            // Find rightmost amount
            int maxPosIndex = 0;
            for (int i = 1; i < positions.size(); i++) {
                if (positions.get(i) > positions.get(maxPosIndex)) {
                    maxPosIndex = i;
                }
            }
            
            // Return first non-rightmost amount
            for (int i = 0; i < amounts.size(); i++) {
                if (i != maxPosIndex) {
                    return amounts.get(i);
                }
            }
        }

        // Fallback: return first amount
        return amounts.get(0);
    }

    /**
     * Determine transaction direction.
     */
    private UniversalStatementTransaction.Direction determineDirection(String line) {
        boolean hasDebit = DEBIT_INDICATOR.matcher(line).find();
        boolean hasCredit = CREDIT_INDICATOR.matcher(line).find();

        if (hasDebit && !hasCredit) {
            return UniversalStatementTransaction.Direction.DEBIT;
        } else if (hasCredit && !hasDebit) {
            return UniversalStatementTransaction.Direction.CREDIT;
        } else {
            // Ambiguous or no clear indicator
            return UniversalStatementTransaction.Direction.UNKNOWN;
        }
    }

    /**
     * Extract narration/description.
     */
    private String extractNarration(String line) {
        // Extract UPI merchant if present
        Matcher upiMatcher = UPI_PATTERN.matcher(line);
        if (upiMatcher.find()) {
            return upiMatcher.group(1).trim();
        }

        // Otherwise, clean up the line
        String narration = line
                .replaceAll("\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{2,4}", "") // Remove dates
                .replaceAll("[\\d,]+\\.\\d{2}", "") // Remove amounts
                .replaceAll("\\b(DR|CR|DEBIT|CREDIT)\\b", "") // Remove indicators
                .trim();

        return narration.length() > 100 ? narration.substring(0, 100) : narration;
    }

    /**
     * Extract transaction reference if present.
     */
    private String extractReference(String line) {
        // Look for patterns like REF123456, TXN123456, etc.
        Pattern refPattern = Pattern.compile("\\b(REF|TXN|UTR)[:\\s]?([A-Z0-9]{6,})\\b");
        Matcher matcher = refPattern.matcher(line);
        
        if (matcher.find()) {
            return matcher.group(1) + matcher.group(2);
        }
        
        return null;
    }

    /**
     * Check if line is likely a header/footer.
     */
    private boolean isHeaderFooterLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("statement") ||
               lower.contains("opening balance") ||
               lower.contains("closing balance") ||
               lower.contains("page") ||
               lower.contains("branch") ||
               lower.contains("account number") ||
               lower.matches("^[\\s-=*]+$") || // Separator lines
               line.length() < 10;
    }

    /**
     * Stage D: Validate parsed candidates.
     */
    private List<UniversalStatementTransaction> validateCandidates(
            List<UniversalStatementTransaction> candidates,
            UniversalStatementParseResult result) {
        
        List<UniversalStatementTransaction> validated = new ArrayList<>();
        Set<UniversalStatementTransaction> seen = new HashSet<>();
        
        // Track potential duplicates by date+amount+direction
        Map<String, Integer> duplicateKeys = new HashMap<>();

        for (UniversalStatementTransaction txn : candidates) {
            // Required fields check
            if (txn.getTransactionDate() == null) {
                result.addWarning("Transaction missing date: " + txn.getSourceText());
                continue;
            }

            if (txn.getAmount() == null || txn.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                result.addWarning("Transaction has invalid amount: " + txn.getSourceText());
                continue;
            }

            // Additional validation: Check for unreasonably large amounts (> 1 billion)
            if (txn.getAmount().compareTo(new BigDecimal("1000000000")) > 0) {
                result.addWarning("Transaction has suspiciously large amount (> 1 billion): " + txn.getAmount());
                txn.setConfidenceScore(txn.getConfidenceScore() * 0.5);
            }

            // Additional validation: Check for very small amounts (< 0.01)
            if (txn.getAmount().compareTo(new BigDecimal("0.01")) < 0) {
                result.addWarning("Transaction has suspiciously small amount (< 0.01): " + txn.getAmount());
                txn.setConfidenceScore(txn.getConfidenceScore() * 0.7);
            }

            // Date plausibility check
            LocalDate now = LocalDate.now();
            if (txn.getTransactionDate().isAfter(now)) {
                result.addWarning("Transaction has future date: " + txn.getTransactionDate());
                txn.setConfidenceScore(txn.getConfidenceScore() * 0.5);
            }

            LocalDate tenYearsAgo = now.minusYears(10);
            if (txn.getTransactionDate().isBefore(tenYearsAgo)) {
                result.addWarning("Transaction date is very old (>10 years): " + txn.getTransactionDate());
                txn.setConfidenceScore(txn.getConfidenceScore() * 0.7);
            }

            // Additional validation: Check narration for opening/closing balance keywords
            if (txn.getNarration() != null) {
                String lowerNarr = txn.getNarration().toLowerCase();
                if (lowerNarr.contains("opening balance") || lowerNarr.contains("closing balance") ||
                    lowerNarr.contains("opening bal") || lowerNarr.contains("closing bal") ||
                    lowerNarr.matches(".*\\b[oc]\\.?\\s*b\\.?\\b.*")) {
                    result.addWarning("Skipping balance row mistakenly extracted as transaction: " + txn.getNarration());
                    continue;
                }
            }

            // Additional validation: Check narration length and emptiness
            if (txn.getNarration() == null || txn.getNarration().trim().isEmpty()) {
                result.addWarning("Transaction has empty narration: " + txn.getSourceText());
                txn.setConfidenceScore(txn.getConfidenceScore() * 0.9);
            } else if (txn.getNarration().length() > 500) {
                result.addWarning("Transaction has unusually long narration (>500 chars), truncating");
                txn.setNarration(txn.getNarration().substring(0, 497) + "...");
            }

            // Duplicate detection using date+amount+direction
            String dupKey = txn.getTransactionDate() + "|" + txn.getAmount() + "|" + txn.getDirection();
            duplicateKeys.put(dupKey, duplicateKeys.getOrDefault(dupKey, 0) + 1);

            // Check full object equality
            if (seen.contains(txn)) {
                result.addWarning("Duplicate transaction detected: " + txn);
                continue;
            }

            // Check for chunk overlap duplicates: same date, amount, direction and overlapping narration or reference
            boolean isChunkOverlapDuplicate = false;
            for (UniversalStatementTransaction existing : validated) {
                if (Objects.equals(existing.getTransactionDate(), txn.getTransactionDate()) &&
                    Objects.equals(existing.getAmount(), txn.getAmount()) &&
                    existing.getDirection() == txn.getDirection()) {

                    if (existing.getTransactionReference() != null && txn.getTransactionReference() != null &&
                        existing.getTransactionReference().equalsIgnoreCase(txn.getTransactionReference())) {
                        isChunkOverlapDuplicate = true;
                        break;
                    }

                    String n1 = existing.getNarration() != null ? existing.getNarration().trim().toLowerCase() : "";
                    String n2 = txn.getNarration() != null ? txn.getNarration().trim().toLowerCase() : "";
                    if (!n1.isEmpty() && !n2.isEmpty() && (n1.contains(n2) || n2.contains(n1))) {
                        isChunkOverlapDuplicate = true;
                        break;
                    }
                }
            }

            if (isChunkOverlapDuplicate) {
                result.addWarning("Duplicate chunk-overlap transaction detected and skipped: " + txn);
                continue;
            }

            // Ambiguous direction warning
            if (txn.getDirection() == UniversalStatementTransaction.Direction.UNKNOWN) {
                result.addWarning("Transaction has ambiguous direction: " + txn.getSourceText());
                txn.setConfidenceScore(txn.getConfidenceScore() * 0.6);
            }

            seen.add(txn);
            validated.add(txn);
        }

        // Check for duplicate patterns and warn
        for (Map.Entry<String, Integer> entry : duplicateKeys.entrySet()) {
            if (entry.getValue() > 1) {
                result.addWarning("Found " + entry.getValue() + " transactions with same date/amount/direction - possible duplicates");
            }
        }

        return validated;
    }

    /**
     * Calculate overall confidence for heuristic parsing.
     */
    private double calculateHeuristicConfidence(List<UniversalStatementTransaction> transactions, int totalLines) {
        if (transactions.isEmpty()) {
            return 0.0;
        }

        // Base confidence on transaction completeness
        double avgConfidence = transactions.stream()
                .mapToDouble(UniversalStatementTransaction::getConfidenceScore)
                .average()
                .orElse(0.0);

        // Penalize if too few transactions found relative to input size
        double detectionRate = (double) transactions.size() / Math.max(totalLines / 3, 1);
        double detectionBonus = Math.min(detectionRate, 1.0);

        return (avgConfidence * 0.7) + (detectionBonus * 0.3);
    }

    /**
     * Stage C: Attempt LLM-based parsing.
     */
    private UniversalStatementParseResult attemptLlmParsing(String text) {
        UniversalStatementParseResult result = new UniversalStatementParseResult();
        result.setDetectionMethod(UniversalStatementParseResult.DetectionMethod.LLM);

        try {
            String jsonResponse = llmClient.requestStructuredParse(text);
            
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                result.addWarning("LLM returned empty response");
                return result;
            }

            // Extract JSON array from response
            String jsonContent = extractJsonContent(jsonResponse);
            if (jsonContent == null) {
                result.addWarning("Could not extract JSON from LLM response");
                return result;
            }

            // Parse JSON manually (following project's pattern)
            List<UniversalStatementTransaction> transactions = parseJsonTransactions(jsonContent, result);
            
            // Validate LLM results
            List<UniversalStatementTransaction> validated = validateCandidates(transactions, result);
            result.setTransactions(validated);

            if (!validated.isEmpty()) {
                result.setOverallConfidence(0.75); // LLM confidence slightly lower than high-confidence heuristic
                result.setRequiresManualReview(validated.size() < 3); // Require review if very few transactions
            } else {
                result.addWarning("LLM parsing produced no valid transactions");
            }

        } catch (Exception e) {
            result.addWarning("LLM parsing failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Extract JSON content from LLM response (similar to InsightsController.extractContent).
     */
    private String extractJsonContent(String response) {
        // PRIORITY 1: Try to extract from "content" field FIRST (Groq format)
        // This avoids grabbing the outer "choices" array instead of inner transaction array
        String marker = "\"content\":\"";
        int start = response.indexOf(marker);
        
        if (start != -1) {
            start += marker.length();
            
            // Find the closing quote, handling escapes and converting them
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            
            for (int i = start; i < response.length(); i++) {
                char c = response.charAt(i);
                
                if (escaped) {
                    // Convert escape sequences
                    switch (c) {
                        case 'n': result.append('\n'); break;
                        case 't': result.append('\t'); break;
                        case 'r': result.append('\r'); break;
                        case '"': result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        default: result.append(c); break;  // Unknown escape, keep as-is
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    result.append(c);
                }
            }

            String content = result.toString();

            // Extract array from content
            int arrayStart = content.indexOf('[');
            int arrayEnd = content.lastIndexOf(']');
            
            if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                return content.substring(arrayStart, arrayEnd + 1);
            }
        }

        // PRIORITY 2: Look for standalone JSON array (fallback for direct array responses)
        int arrayStart = response.indexOf('[');
        int arrayEnd = response.lastIndexOf(']');
        
        if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
            // Verify this is not the "choices" array by checking if it contains transaction-like structure
            String potentialArray = response.substring(arrayStart, arrayEnd + 1);
            if (potentialArray.contains("\"date\"") || potentialArray.contains("\"amount\"")) {
                return potentialArray;
            }
        }

        return null;
    }

    /**
     * Parse JSON transaction array manually.
     */
    private List<UniversalStatementTransaction> parseJsonTransactions(String json, UniversalStatementParseResult result) {
        List<UniversalStatementTransaction> transactions = new ArrayList<>();

        // Remove array brackets
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        json = json.trim();

        // Split by objects - handle both compact and formatted JSON
        // Pattern: },{  OR  }\n  ,  \n  {  OR  }\r\n,\r\n{
        String[] objects = json.split("\\}\\s*,\\s*\\{");

        for (String obj : objects) {
            try {
                UniversalStatementTransaction txn = parseJsonObject(obj);
                if (txn != null) {
                    transactions.add(txn);
                }
            } catch (Exception e) {
                result.addWarning("Failed to parse JSON object: " + e.getMessage());
            }
        }

        return transactions;
    }

    /**
     * Parse a single JSON object into a transaction.
     */
    private UniversalStatementTransaction parseJsonObject(String json) {
        UniversalStatementTransaction txn = new UniversalStatementTransaction();

        // Remove curly braces if present
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        // Extract fields
        String date = extractJsonField(json, "date");
        String amount = extractJsonField(json, "amount");
        String direction = extractJsonField(json, "direction");
        String narration = extractJsonField(json, "narration");
        String balance = extractJsonField(json, "balance");
        String reference = extractJsonField(json, "reference");

        // Parse date
        if (date != null) {
            try {
                txn.setTransactionDate(LocalDate.parse(date));
            } catch (Exception e) {
                return null; // Invalid date
            }
        } else {
            return null; // Date required
        }

        // Parse amount
        if (amount != null) {
            try {
                txn.setAmount(new BigDecimal(amount.replace(",", "")));
            } catch (Exception e) {
                return null; // Invalid amount
            }
        } else {
            return null; // Amount required
        }

        // Parse direction
        if (direction != null) {
            try {
                txn.setDirection(UniversalStatementTransaction.Direction.valueOf(direction.toUpperCase()));
            } catch (Exception e) {
                txn.setDirection(UniversalStatementTransaction.Direction.UNKNOWN);
            }
        }

        // Optional fields
        txn.setNarration(narration != null ? narration : "");
        txn.setTransactionReference(reference);
        
        if (balance != null) {
            try {
                txn.setAccountBalance(new BigDecimal(balance.replace(",", "")));
            } catch (Exception e) {
                // Ignore invalid balance
            }
        }

        txn.setConfidenceScore(0.75); // LLM confidence
        txn.setSourceText("LLM-parsed");

        return txn;
    }

    /**
     * Extract a field value from JSON string.
     */
    private String extractJsonField(String json, String fieldName) {
        // Matches either "field": "value" or "field": value (unquoted number/boolean/null)
        String pattern = "\"" + fieldName + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\s]+))";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(json);
        
        if (m.find()) {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            if (val != null && !val.equals("null")) {
                return val.trim();
            }
        }
        
        return null;
    }
}
