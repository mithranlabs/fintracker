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
import jakarta.servlet.http.HttpSession;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;


@RestController
@RequestMapping("/upload")
public class UploadController {

    private final TransactionRepository repo;
    private final CategoryRepository categoryRepository;
    private final MerchantRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final UploadRepository uploadRepository;  // ← ADD THIS


    private static final Map<String, List<String>> categoryKeywords = new HashMap<>();

    static {
        categoryKeywords.put(
                "Food",
                Arrays.asList("hotel", "bakery", "restaurant", "food", "cafe")
        );

        categoryKeywords.put(
                "Travel",
                Arrays.asList("bus", "fuel", "petrol", "train", "auto")
        );

        categoryKeywords.put(
                "Shopping",
                Arrays.asList("store", "shop", "mart", "mall")
        );
    }

    public UploadController(TransactionRepository repo,
                            CategoryRepository categoryRepository, MerchantRuleRepository ruleRepository,UserRepository userRepository,UploadRepository uploadRepository) {
        this.repo = repo;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
        this.userRepository = userRepository;
        this.uploadRepository = uploadRepository;
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
}

