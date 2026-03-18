package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    @Query("""
SELECT t.category.name, SUM(t.amount)
FROM Transaction t
GROUP BY t.category.name
""")
    List<Object[]> getCategorySummary();

    @Query("""
SELECT t.type, SUM(t.amount)
FROM Transaction t
GROUP BY t.type
""")
    List<Object[]> getTypeSummary();
    @Query("""
SELECT FUNCTION('DATE_FORMAT', t.date, '%Y-%m'),
       SUM(t.amount)
FROM Transaction t
GROUP BY FUNCTION('DATE_FORMAT', t.date, '%Y-%m')
ORDER BY FUNCTION('DATE_FORMAT', t.date, '%Y-%m')
""")
    List<Object[]> getMonthlySummary();
    @Query("""
SELECT t.note, SUM(t.amount)
FROM Transaction t
GROUP BY t.note
ORDER BY SUM(t.amount) DESC
""")
    List<Object[]> getMerchantSummary();
}
