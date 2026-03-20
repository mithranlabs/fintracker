package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.repository.TransactionRepository;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/summary")
public class SummaryController {

    private final TransactionRepository repo;

    public SummaryController(TransactionRepository repo) {
        this.repo = repo;
    }




    @GetMapping("/category")
    public List<Map<String, Object>> categorySummary() {

        List<Object[]> data = repo.getCategorySummary();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : data) {

            Map<String, Object> map = new HashMap<>();

            map.put("category", row[0]);
            map.put("total", row[1]);
            map.put("type", row[2]);

            result.add(map);
        }

        return result;
    }
    @GetMapping("/type")
    public List<Map<String, Object>> typeSummary() {

        List<Object[]> data = repo.getTypeSummary();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : data) {

            Map<String, Object> map = new HashMap<>();

            map.put("type", row[0]);
            map.put("total", row[1]);

            result.add(map);
        }

        return result;
    }
    @GetMapping("/month")
    public List<Map<String, Object>> monthlySummary() {

        List<Object[]> data = repo.getMonthlySummary();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : data) {

            Map<String, Object> map = new HashMap<>();

            map.put("month", row[0]);
            map.put("total", row[1]);
            map.put("type", row[2]);

            result.add(map);
        }

        return result;
    }
    @GetMapping("/merchant")
    public List<Map<String, Object>> merchantSummary() {

        List<Object[]> data = repo.getMerchantSummary();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : data) {

            Map<String, Object> map = new HashMap<>();

            map.put("merchant", row[0]);
            map.put("total", row[1]);

            result.add(map);
        }

        return result;
    }
}