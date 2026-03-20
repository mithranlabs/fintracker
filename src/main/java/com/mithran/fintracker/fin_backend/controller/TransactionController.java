package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.Category;
import com.mithran.fintracker.fin_backend.repository.CategoryRepository;
import com.mithran.fintracker.fin_backend.repository.TransactionRepository;
import org.springframework.web.bind.annotation.*;
import com.mithran.fintracker.fin_backend.entity.MerchantRule;
import com.mithran.fintracker.fin_backend.repository.MerchantRuleRepository;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionRepository repo;
    private final MerchantRuleRepository ruleRepo;
    private final CategoryRepository categoryRepository;

    public TransactionController(TransactionRepository repo, MerchantRuleRepository ruleRepo,CategoryRepository categoryRepository) {
        this.repo = repo;
        this.ruleRepo = ruleRepo;
        this.categoryRepository = categoryRepository;

    }

    @GetMapping
    public List<Transaction> getAll() {
        return repo.findAll();
    }

    @PostMapping
    public Transaction add(@RequestBody Transaction t) {
        return repo.save(t);
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id) {
        repo.deleteById(id);
    }
    @DeleteMapping("/clear")
    public void clearAll() {
        repo.deleteAll();
    }
    @PutMapping("/{id}")
    public Transaction update(
            @PathVariable int id,
            @RequestBody Transaction newTx,
            @RequestParam(defaultValue = "false") boolean applyAll,
            @RequestParam(defaultValue = "false") boolean updateRule
    ) {

        Transaction tx = repo.findById(id).orElseThrow();

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

                if (t.getNote() != null &&
                        note != null &&
                        t.getNote().equals(note)) {

                    t.setCategory(newTx.getCategory());
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

            rule.setCategory(newTx.getCategory());

            ruleRepo.save(rule);

        }

        return tx;
    }
}
