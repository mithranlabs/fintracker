package com.mithran.fintracker.fin_backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/transactions-page")
    public String transactionsPage() {
        return "transactions";
    }

    @GetMapping("/upload-page")
    public String uploadPage() {
        return "upload";
    }

    @GetMapping("/summary-page")
    public String summaryPage() {
        return "summary";
    }
    @GetMapping("/add-page")
    public String addPage() {
        return "add";
    }
}