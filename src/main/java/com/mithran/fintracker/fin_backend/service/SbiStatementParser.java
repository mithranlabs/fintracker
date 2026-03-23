package com.mithran.fintracker.fin_backend.service;

import com.mithran.fintracker.fin_backend.entity.Category;
import com.mithran.fintracker.fin_backend.entity.MerchantRule;
import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.CategoryRepository;
import com.mithran.fintracker.fin_backend.repository.MerchantRuleRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SbiStatementParser {

    private final CategoryRepository categoryRepository;
    private final MerchantRuleRepository ruleRepository;

    // Matches: 01/12/2025 01/12/2025  (date only line)
    private static final Pattern DATE_LINE = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})$"
    );

    // Matches: 19/03/2026 19/03/2026 CSH DEP (CDM) 7348822135 8991
    private static final Pattern DATE_INLINE_LINE = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})\\s+(.+)$"
    );

    // Matches amount line: - 94.00 - 38,261.05  OR  - - 47.00 38,308.05
    private static final Pattern AMOUNT_LINE = Pattern.compile(
            "^-\\s+([\\d,]+\\.\\d{2}|-)\\s+([\\d,]+\\.\\d{2}|-)\\s+([\\d,]+\\.\\d{2})"
    );

    // Extracts merchant name from UPI narration
    private static final Pattern UPI_NAME = Pattern.compile(
            "UPI/(?:DR|CR|REVERSAL)/\\d+/([^/]+)"
    );

    private static final Map<String, List<String>> categoryKeywords = new HashMap<>();
    static {
        categoryKeywords.put("Food",     Arrays.asList("hotel", "bakery", "restaurant", "food", "cafe", "swiggy", "zomato", "dominos", "dunzo"));
        categoryKeywords.put("Travel",   Arrays.asList("bus", "fuel", "petrol", "train", "auto", "irctc", "uber", "ola", "bmtc", "rapido"));
        categoryKeywords.put("Shopping", Arrays.asList("store", "shop", "mart", "mall", "amazon", "flipkart", "myntra"));
    }

    private boolean isTransactionTypeLine(String line) {
        return line.matches("(WDL|DEP|CSH|NEFT|RTGS|INT|TRF|ATM|CLG|ECS|ACH|SI|CEMTEX|CASH)\\s?(TFR|CR|DR|DEP|WDL|DEPOSIT)?.*");
    }

    public SbiStatementParser(CategoryRepository categoryRepository,
                              MerchantRuleRepository ruleRepository) {
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
    }

    public List<Transaction> parse(MultipartFile file, String password, User user) throws Exception {
        List<Transaction> transactions = new ArrayList<>();

        PDDocument document = PDDocument.load(file.getInputStream(), password);
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        document.close();

        String[] lines = text.split("\\r?\\n");

        String txnDate = null;
        String txnType = null;
        StringBuilder narrationBuf = new StringBuilder();
        boolean collectingNarration = false;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Skip page headers/footers
            if (line.startsWith("Page no.") || line.startsWith("Balance") ||
                    line.startsWith("Statement From") || line.startsWith("STATEMENT OF ACCOUNT") ||
                    line.startsWith("State Bank") || line.startsWith("Branch Name") ||
                    line.startsWith("Statement Summary") || line.startsWith("Brought Forward") ||
                    line.startsWith("Please do not") || line.startsWith("If your account") ||
                    line.startsWith("This is a computer")) {
                continue;
            }

            // 1a. Detect plain date line: "01/12/2025 01/12/2025"
            Matcher dateMatcher = DATE_LINE.matcher(line);
            if (dateMatcher.matches()) {
                txnDate = dateMatcher.group(1);
                txnType = null;
                narrationBuf.setLength(0);
                collectingNarration = false;
                continue;
            }

            // 1b. Detect inline date+type: "19/03/2026 19/03/2026 CSH DEP (CDM) ..."
            Matcher inlineMatcher = DATE_INLINE_LINE.matcher(line);
            if (inlineMatcher.matches()) {
                txnDate = inlineMatcher.group(1);
                txnType = inlineMatcher.group(3).trim();
                narrationBuf.setLength(0);
                narrationBuf.append(txnType).append(" ");
                collectingNarration = true;
                continue;
            }

            // 2. Detect transaction type line: "WDL TFR", "DEP TFR", "CSH DEP", etc.
            if (txnDate != null && txnType == null && isTransactionTypeLine(line)) {
                txnType = line;
                collectingNarration = true;
                continue;
            }

            // 3. Detect amount line → save transaction
            if (txnDate != null && txnType != null) {
                Matcher amtMatcher = AMOUNT_LINE.matcher(line);
                if (amtMatcher.find()) {
                    collectingNarration = false;

                    String debitStr  = amtMatcher.group(1);
                    String creditStr = amtMatcher.group(2);

                    double amount;
                    String type;

                    if (!debitStr.equals("-")) {
                        amount = Double.parseDouble(debitStr.replaceAll(",", ""));
                        type = "expense";
                    } else {
                        amount = Double.parseDouble(creditStr.replaceAll(",", ""));
                        type = "income";
                    }

                    String rawNarration = narrationBuf.toString().replace("\n", " ").trim();
                    String merchantName = extractMerchantName(rawNarration);

                    Date date;
                    try {
                        date = sdf.parse(txnDate);
                        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
                        cal.setTime(date);
                        cal.set(Calendar.HOUR_OF_DAY, 12);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        date = cal.getTime();
                    } catch (Exception e) {
                        date = new Date();
                    }

                    Category category = resolveCategory(merchantName.toLowerCase(), type);

                    Transaction t = new Transaction();
                    t.setAmount(amount);
                    t.setType(type);
                    t.setNote(merchantName);
                    t.setDate(date);
                    t.setUser(user);
                    t.setCategory(category);
                    transactions.add(t);

                    // Reset state
                    txnDate = null;
                    txnType = null;
                    narrationBuf.setLength(0);
                    continue;
                }
            }

            // 4. Collect narration lines
            if (collectingNarration) {
                if (line.matches("\\d{13} AT \\d+")) continue; // skip ref numbers
                if (line.equals("CHANNAPATNA")) continue;       // skip branch name
                narrationBuf.append(line).append(" ");
            }
        }

        return transactions;
    }

    private String extractMerchantName(String narration) {
        Matcher m = UPI_NAME.matcher(narration);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Fallback for CSH DEP, CASH DEPOSIT, CEMTEX etc.
        return narration.length() > 60 ? narration.substring(0, 60) : narration;
    }

    private Category resolveCategory(String noteText, String type) {
        Optional<MerchantRule> ruleOpt = ruleRepository.findByKeyword(noteText);
        if (ruleOpt.isPresent()) return ruleOpt.get().getCategory();

        for (String catName : categoryKeywords.keySet()) {
            for (String w : categoryKeywords.get(catName)) {
                if (noteText.contains(w)) {
                    Category cat = categoryRepository.findByName(catName);
                    if (cat == null) {
                        cat = new Category();
                        cat.setName(catName);
                        cat.setType(type);
                        categoryRepository.save(cat);
                    }
                    return cat;
                }
            }
        }

        String fallback = type.equals("income") ? "Salary" : "Personal";
        return categoryRepository.findByName(fallback);
    }
}
