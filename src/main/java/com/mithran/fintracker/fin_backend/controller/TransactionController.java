package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.Transaction;
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

    public TransactionController(TransactionRepository repo, MerchantRuleRepository ruleRepo) {
        this.repo = repo;
        this.ruleRepo = ruleRepo;
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
        tx.setDate(newTx.getDate());
        tx.setCategory(newTx.getCategory());

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
