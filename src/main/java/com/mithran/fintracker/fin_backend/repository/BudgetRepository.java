package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.Budget;
import com.mithran.fintracker.fin_backend.entity.Category;
import com.mithran.fintracker.fin_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Integer> {
    List<Budget> findByUserAndMonth(User user, String month);
    Optional<Budget> findByUserAndCategoryAndMonth(User user, Category category, String month);
}
