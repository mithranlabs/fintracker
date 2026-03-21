package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository repo;

    public AuthController(UserRepository repo) {
        this.repo = repo;
    }


    @PostMapping("/register")
    public String register(@RequestBody User user) {

        if (repo.findByEmail(user.getEmail()) != null) {
            return "Email exists";
        }

        repo.save(user);

        return "Registered";
    }


    @PostMapping("/login")
    public String login(
            @RequestBody User user,
            HttpSession session
    ) {

        User u = repo.findByUsername(user.getUsername());

        if (u == null) {
            u = repo.findByEmail(user.getUsername());
        }

        if (u == null) {
            return "No user";
        }

        if (!u.getPassword().equals(user.getPassword())) {
            return "Wrong password";
        }

        session.setAttribute("userId", u.getId());

        return "OK";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {

        session.invalidate();

        return "Logged out";
    }

}