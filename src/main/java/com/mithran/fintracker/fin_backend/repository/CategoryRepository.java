package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    Category findByName(String name);
}