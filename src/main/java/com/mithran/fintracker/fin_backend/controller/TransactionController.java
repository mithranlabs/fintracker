package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.Category;
import com.mithran.fintracker.fin_backend.repository.CategoryRepository;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import com.mithran.fintracker.fin_backend.entity.MerchantRule;
import com.mithran.fintracker.fin_backend.repository.MerchantRuleRepository;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.text.SimpleDateFormat;
import java.util.*;
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionRepository repo;
    private final MerchantRuleRepository ruleRepo;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public TransactionController(TransactionRepository repo, MerchantRuleRepository ruleRepo,CategoryRepository categoryRepository,UserRepository userRepository) {
        this.repo = repo;
        this.ruleRepo = ruleRepo;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;

    }

    @GetMapping
    public List<Transaction> getAll(HttpSession session) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) return List.of();

        return repo.findByUserId(userId);
    }


    @PostMapping
    public Transaction add(
            @RequestBody Transaction t,
            HttpSession session
    ) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) {
            return null;
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElse(null);

        t.setUser(user);

        return repo.save(t);
    }
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable int id,
            HttpSession session
    ) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) return;

        Transaction t =
                repo.findById(id).orElse(null);

        if (t == null) return;

        if (t.getUser() == null) return;

        if (t.getUser().getId() != userId) {
            return;
        }

        repo.deleteById(id);
    }
    @DeleteMapping("/clear")
    public void clearAll(HttpSession session) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) return;

        List<Transaction> list = repo.findAll();

        for (Transaction t : list) {

            if (t.getUser() != null &&
                    t.getUser().getId() == userId) {

                repo.delete(t);
            }
        }
    }
    @PutMapping("/{id}")
    public Transaction update(
            @PathVariable int id,
            @RequestBody Transaction newTx,
            @RequestParam(defaultValue = "false") boolean applyAll,
            @RequestParam(defaultValue = "false") boolean updateRule,
            HttpSession session
    ) {

        Transaction tx = repo.findById(id).orElse(null);

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (tx == null || userId == null) {
            return null;
        }

        if (tx.getUser() == null ||
                tx.getUser().getId() != userId) {

            return null;
        }

        tx.setAmount(newTx.getAmount());
        tx.setType(newTx.getType());
        tx.setNote(newTx.getNote());
        if (newTx.getDate() != null) {
            tx.setDate(newTx.getDate());
        }
        if (newTx.getCategory() != null) {

            String name = newTx.getCategory().getName();

            Category c = categoryRepository.findByName(name);

            if (c == null) {

                c = new Category();
                c.setName(name);
                c.setType(newTx.getType());

                categoryRepository.save(c);
            }

            tx.setCategory(c);
        }

        repo.save(tx);

        String note = tx.getNote();

        // APPLY ALL

        if (applyAll) {



            var list = repo.findAll();

            for (Transaction t : list) {

                if (
                        t.getUser() != null &&
                                t.getUser().getId() == userId &&
                                t.getNote() != null &&
                                t.getNote().equals(note)
                ) {

                    Category c = categoryRepository
                            .findByName(newTx.getCategory().getName());

                    t.setCategory(c);

                    repo.save(t);
                }
            }
        }

        //  UPDATE RULE
        if (updateRule && note != null) {

            String keyword = note;

            if (keyword.startsWith("Paid to ")) {
                keyword = keyword.substring(8);
            }

            if (keyword.startsWith("Received from ")) {
                keyword = keyword.substring(14);
            }

            keyword = keyword.trim().toLowerCase();

            Optional<MerchantRule> existing =
                    ruleRepo.findByKeyword(keyword);

            MerchantRule rule;

            if (existing.isPresent()) {

                rule = existing.get();

            } else {

                rule = new MerchantRule();
                rule.setKeyword(keyword);

            }

            Category c =
                    categoryRepository.findByName(
                            newTx.getCategory().getName()
                    );

            rule.setCategory(c);

            ruleRepo.save(rule);

        }

        return tx;
    }
    @GetMapping("/filter")
    public ResponseEntity<?> filterByDateRange(
            @RequestParam String start,
            @RequestParam String end,
            HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("Not logged in");

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("User not found");

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            // Start = beginning of day, End = end of day
            Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            startCal.setTime(sdf.parse(start));
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);

            Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            endCal.setTime(sdf.parse(end));
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);

            List<Transaction> txns = repo
                    .findByUserAndDateBetweenOrderByDateDesc(user, startCal.getTime(), endCal.getTime());


            List<Map<String, Object>> result = new ArrayList<>();
            for (Transaction t : txns) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", t.getId());
                map.put("amount", t.getAmount());
                map.put("type", t.getType());
                map.put("category", t.getCategory() != null ? t.getCategory().getName() : "Uncategorized");
                map.put("note", t.getNote());
                map.put("date", t.getDate());
                result.add(map);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Filter failed: " + e.getMessage());
        }
    }

}
