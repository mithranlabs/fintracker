package com.mithran.fintracker.fin_backend.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/transactions-page")
    public String transactionsPage(HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login-page";
        }
        return "transactions";
    }

    @GetMapping("/upload-page")
    public String uploadPage(HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login-page";
        }
        return "upload";
    }

    @GetMapping("/summary-page")
    public String summaryPage(HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login-page";
        }
        return "summary";
    }
    @GetMapping("/add-page")
    public String addPage(HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login-page";
        }
        return "add";
    }
    @GetMapping("/login-page")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register-page")
    public String registerPage() {
        return "register";
    }
}