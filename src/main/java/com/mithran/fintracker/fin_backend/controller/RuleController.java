package com.mithran.fintracker.fin_backend.controller;


import com.mithran.fintracker.fin_backend.entity.*;
import com.mithran.fintracker.fin_backend.repository.*;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/rules")
public class RuleController {

    private final MerchantRuleRepository ruleRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository transactionRepo;

    public RuleController(MerchantRuleRepository ruleRepo,
                          CategoryRepository categoryRepo,TransactionRepository transactionRepo) {

        this.ruleRepo = ruleRepo;
        this.categoryRepo = categoryRepo;
        this.transactionRepo = transactionRepo;
    }


    @PostMapping
    public String addRule(
            @RequestParam String keyword,
            @RequestParam String categoryName) {

        keyword = keyword.toLowerCase();

        Category cat = categoryRepo.findByName(categoryName);

        // save rule
        MerchantRule rule = new MerchantRule();
        rule.setKeyword(keyword);
        rule.setCategory(cat);

        ruleRepo.save(rule);


        // update old transactions

        List<Transaction> list = transactionRepo.findAll();

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