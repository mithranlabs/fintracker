package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.*;
import com.mithran.fintracker.fin_backend.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/rules")
public class RuleController {

    private final MerchantRuleRepository ruleRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository transactionRepo;
    private final UserRepository userRepository;

    public RuleController(MerchantRuleRepository ruleRepo,
                          CategoryRepository categoryRepo,
                          TransactionRepository transactionRepo,
                          UserRepository userRepository) {
        this.ruleRepo = ruleRepo;
        this.categoryRepo = categoryRepo;
        this.transactionRepo = transactionRepo;
        this.userRepository = userRepository;
    }

    @PostMapping
    public String addRule(
            @RequestParam String keyword,
            @RequestParam String categoryName,
            HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return "Not logged in";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "User not found";
        }

        keyword = keyword.toLowerCase();

        Category cat = categoryRepo.findByName(categoryName);

        // save rule
        MerchantRule rule = new MerchantRule();
        rule.setKeyword(keyword);
        rule.setCategory(cat);

        ruleRepo.save(rule);

        // update old transactions for logged-in user only
        List<Transaction> list = transactionRepo.findByUser(user);

        for (Transaction t : list) {
            if (t.getNote() != null &&
                    t.getNote().toLowerCase().contains(keyword)) {
                t.setCategory(cat);
                transactionRepo.save(t);
            }
        }

        return "Rule saved + updated old transactions";
    }
}
