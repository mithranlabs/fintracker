package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import com.mithran.fintracker.fin_backend.repository.MerchantRuleRepository;
import com.mithran.fintracker.fin_backend.entity.MerchantRule;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import com.mithran.fintracker.fin_backend.repository.CategoryRepository;
import com.mithran.fintracker.fin_backend.entity.Category;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;



@RestController
@RequestMapping("/upload")
public class UploadController {

    private final TransactionRepository repo;
    private final CategoryRepository categoryRepository;
    private final MerchantRuleRepository ruleRepository;

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
                            CategoryRepository categoryRepository,MerchantRuleRepository ruleRepository) {
        this.repo = repo;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file) {

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
                                        break;
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

                        System.out.println("Saved => " + note + " | " + amount + " | " + type);
                        note = null;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }


            return "Parsed and saved";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }
}