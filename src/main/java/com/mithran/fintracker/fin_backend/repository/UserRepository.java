package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
}