package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Category;
import com.mithran.fintracker.fin_backend.entity.MerchantRule;
import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.Upload;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.CategoryRepository;
import com.mithran.fintracker.fin_backend.repository.MerchantRuleRepository;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import com.mithran.fintracker.fin_backend.repository.UploadRepository;
import com.mithran.fintracker.fin_backend.repository.UserRepository;
import com.mithran.fintracker.fin_backend.service.SbiStatementParser;
import jakarta.servlet.http.HttpSession;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


@RestController
@RequestMapping("/upload")
public class UploadController {

    private final TransactionRepository repo;
    private final CategoryRepository categoryRepository;
    private final MerchantRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final UploadRepository uploadRepository;
    private final SbiStatementParser sbiStatementParser;


    private static final Map<String, List<String>> categoryKeywords = new HashMap<>();

    static {
        categoryKeywords.put("Food", Arrays.asList(
                "hotel", "bakery", "restaurant", "food", "cafe", "swiggy", "zomato",
                "dominos", "dominoes", "pizza", "burger", "kfc", "mcdonalds", "mcd",
                "dunzo", "blinkit", "instamart", "grofers", "bigbasket", "mess",
                "canteen", "juice", "tea", "coffee", "starbucks", "subway", "biryani"
        ));

        categoryKeywords.put("Travel", Arrays.asList(
                "bus", "fuel", "petrol", "train", "auto", "irctc", "uber", "ola",
                "bmtc", "rapido", "metro", "cab", "taxi", "flight", "airline",
                "indigo", "spicejet", "airasia", "redbus", "makemytrip", "toll",
                "parking", "diesel", "hp petrol", "indian oil", "bpcl"
        ));

        categoryKeywords.put("Shopping", Arrays.asList(
                "store", "shop", "mart", "mall", "amazon", "flipkart", "myntra",
                "meesho", "nykaa", "ajio", "snapdeal", "reliance", "dmart", "decathlon",
                "croma", "vijay sales", "jiomart", "zepto", "clothing", "fashion"
        ));

        categoryKeywords.put("Entertainment", Arrays.asList(
                "netflix", "spotify", "prime", "hotstar", "youtube", "disney",
                "bookmyshow", "pvr", "inox", "cinema", "movie", "game", "steam",
                "playstation", "xbox", "apple music", "jiosaavn", "gaana"
        ));

        categoryKeywords.put("Health", Arrays.asList(
                "pharmacy", "medical", "hospital", "clinic", "doctor", "apollo",
                "medplus", "1mg", "pharmeasy", "netmeds", "gym", "fitness",
                "cult", "healthify", "dentist", "lab", "diagnostic"
        ));

        categoryKeywords.put("Education", Arrays.asList(
                "college", "university", "school", "tuition", "course", "udemy",
                "coursera", "nptel", "books", "stationery", "xerox", "printing",
                "exam", "fee", "principal", "hostel"
        ));

        categoryKeywords.put("Utilities", Arrays.asList(
                "electricity", "bescom", "water", "gas", "lpg", "broadband",
                "wifi", "internet", "airtel", "jio", "bsnl", "vi ", "vodafone",
                "recharge", "mobile", "postpaid", "prepaid", "dth", "tatasky"
        ));

        categoryKeywords.put("Finance", Arrays.asList(
                "insurance", "lic", "emi", "loan", "bank", "interest", "mutual fund",
                "zerodha", "groww", "paytm money", "sip", "fd", "rd", "nps", "tax"
        ));
    }


    public UploadController(TransactionRepository repo,
                            CategoryRepository categoryRepository, MerchantRuleRepository ruleRepository,UserRepository userRepository,UploadRepository uploadRepository,SbiStatementParser sbiStatementParser) {
        this.repo = repo;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
        this.userRepository = userRepository;
        this.uploadRepository = uploadRepository;
        this.sbiStatementParser = sbiStatementParser;
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file,@RequestParam(defaultValue = "false") boolean replace,HttpSession session){
        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) {
            return "Not logged in";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (replace) {
            repo.deleteAll();
        }

        try {

            InputStream inputStream = file.getInputStream();

            PDDocument document = PDDocument.load(inputStream);

            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);

            document.close();

            String[] lines = text.split("\\r?\\n");

            String note = null;
            String type = "expense";
            Date date = new Date();
            String dateStr = null;
            String timeStr = null;
            int savedCount = 0;

            for (int i = 0; i < lines.length; i++) {

                String line = lines[i].trim();

                if (line.isEmpty()) continue;

                // detect date
                if (line.matches("\\d{2} .*\\d{4}")) {
                    dateStr = line;
                }
                if (line.matches("\\d{2}:\\d{2} .*")) {
                    timeStr = line;
                }

                // detect paid
                if (line.startsWith("Paid to") && note == null) {
                    note = line;
                    type = "expense";
                }

                if (line.startsWith("Received from") && note == null) {
                    note = line;
                    type = "income";
                }

                // detect amount
                if (line.contains("₹")&& note != null) {
                    System.out.println("DATE STR = " + dateStr);
                    System.out.println("TIME STR = " + timeStr);

                    try {

                        String num = line.replace("₹", "").replace(",", "").trim();
                        double amount = Double.parseDouble(num);

                        try {

                            if (dateStr != null && timeStr != null) {

                                SimpleDateFormat sdf =
                                        new SimpleDateFormat("dd MMM, yyyy hh:mm a");

                                date = sdf.parse(dateStr + " " + timeStr);
                                System.out.println("PARSED DATE = " + date);
                            }

                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                        Transaction t = new Transaction();
                        t.setAmount(amount);
                        t.setType(type);
                        t.setNote(note);
                        t.setDate(date);
                        t.setUser(user);
                        Category category = null;

                        String noteText = note;

                        if (noteText.startsWith("Paid to ")) {
                            noteText = noteText.substring(8);
                        }

                        if (noteText.startsWith("Received from ")) {
                            noteText = noteText.substring(14);
                        }

                        noteText = noteText.trim().toLowerCase();

                        Optional<MerchantRule> ruleOpt =
                                ruleRepository.findByKeyword(noteText);

                        if (ruleOpt.isPresent()) {

                            MerchantRule rule = ruleOpt.get();

                            category = rule.getCategory();

                        } else {

                            // CHECK KEYWORD MAP

                            for (String catName : categoryKeywords.keySet()) {

                                List<String> words = categoryKeywords.get(catName);

                                for (String w : words) {

                                    if (noteText.contains(w)) {

                                        category = categoryRepository.findByName(catName);

                                        if (category == null) {

                                            category = new Category();
                                            category.setName(catName);
                                            category.setType(type);

                                            categoryRepository.save(category);
                                        }
                                    }
                                }

                                if (category != null) {
                                    break;
                                }
                            }
                        }


                        // FALLBACK

                        if (category == null) {

                            if (type.equals("income")) {
                                category = categoryRepository.findByName("Salary");
                            } else {
                                category = categoryRepository.findByName("Personal");
                            }
                        }

                        t.setCategory(category);

                        repo.save(t);
                        savedCount++;

                        System.out.println("Saved => " + note + " | " + amount + " | " + type);
                        note = null;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            Upload upload = new Upload();
            upload.setFileName(file.getOriginalFilename());
            upload.setUploadDate(new Date());
            upload.setTransactionCount(savedCount);
            upload.setUser(user);
            uploadRepository.save(upload);


            return "Parsed and saved";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }
    @GetMapping("/history")
    public ResponseEntity<?> getUploadHistory(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("User not found");

        List<Upload> uploads = uploadRepository.findByUserOrderByUploadDateDesc(user);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Upload u : uploads) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("fileName", u.getFileName());
            map.put("date", u.getUploadDate().toString());
            map.put("transactionCount", u.getTransactionCount());
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /upload/delete/{id}  —  removes an upload record from history
    // ─────────────────────────────────────────────────────────────────────────
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUploadRecord(@PathVariable Integer id, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        Upload upload = uploadRepository.findById(id).orElse(null);
        if (upload == null) return ResponseEntity.status(404).body("Upload not found");

        if (upload.getUser().getId() != userId) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        uploadRepository.delete(upload);
        return ResponseEntity.ok("Deleted");
    }
    @PostMapping("/sbi")
    public ResponseEntity<?> uploadSbi(
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password,
            @RequestParam(defaultValue = "false") boolean replace,
            HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("User not found");

        if (replace) {
            repo.deleteByUser(user); // scoped to user, same as your pattern
        }

        try {
            List<Transaction> transactions = sbiStatementParser.parse(file, password, user);

            if (transactions.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("No transactions found. Check if the PDF format matches SBI Credit Card statement.");
            }

            repo.saveAll(transactions);

            // Save upload history — same as GPay flow
            Upload upload = new Upload();
            upload.setFileName(file.getOriginalFilename());
            upload.setUploadDate(new Date());
            upload.setTransactionCount(transactions.size());
            upload.setUser(user);
            uploadRepository.save(upload);


            return ResponseEntity.ok("Parsed and saved " + transactions.size() + " transactions");

        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            return ResponseEntity.status(400).body("Wrong password. Format: NNNNNDDMMYYYY");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Parsing failed: " + e.getMessage());
        }
    }
    @GetMapping("/sbi/test")
    public String testSbiParse() throws Exception {
        File f = new File("C:\\Users\\mithr\\Downloads\\AccountStatement_23032026_212526.pdf");
        PDDocument doc = PDDocument.load(f, "MITHR31122004");
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(doc);
        doc.close();
        return "<pre>" + text.replace("<", "&lt;") + "</pre>";
    }


}

