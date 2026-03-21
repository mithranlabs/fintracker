package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.repository.TransactionRepository;

import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.*;

@RestController
@RequestMapping("/summary")
public class SummaryController {

    private final TransactionRepository repo;

    public SummaryController(TransactionRepository repo) {
        this.repo = repo;
    }




    @GetMapping("/category")
    public List<Map<String,Object>> categorySummary(
            HttpSession session
    ) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) return List.of();

        List<Object[]> data =
                repo.getCategorySummary(userId);

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
    public List<Map<String,Object>> typeSummary(
            HttpSession session
    ) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) return List.of();

        List<Object[]> data =
                repo.getTypeSummary(userId);

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
    public List<Map<String,Object>> monthlySummary(
            HttpSession session
    ) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) return List.of();

        List<Object[]> data =
                repo.getMonthlySummary(userId);

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
    public List<Map<String,Object>> merchantSummary(
            HttpSession session
    ) {

        Integer userId =
                (Integer) session.getAttribute("userId");

        if (userId == null) return List.of();

        List<Object[]> data =
                repo.getMerchantSummary(userId);

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