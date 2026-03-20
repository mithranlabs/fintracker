package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    @Query("""
SELECT c.name, SUM(t.amount), t.type
FROM Transaction t
JOIN t.category c
GROUP BY c.name, t.type
ORDER BY SUM(t.amount) DESC
""")
    List<Object[]> getCategorySummary();

    @Query("""
SELECT t.type, SUM(t.amount)
FROM Transaction t
GROUP BY t.type
""")
    List<Object[]> getTypeSummary();
    @Query("""
SELECT 
    FUNCTION('DATE_FORMAT', t.date, '%Y-%m'),
    SUM(t.amount),
    t.type
FROM Transaction t
GROUP BY FUNCTION('DATE_FORMAT', t.date, '%Y-%m'), t.type
ORDER BY 1
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
