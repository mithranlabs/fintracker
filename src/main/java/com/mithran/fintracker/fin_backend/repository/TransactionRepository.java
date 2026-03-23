package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.Transaction;
import com.mithran.fintracker.fin_backend.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Date;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    @Query("""
SELECT c.name, SUM(t.amount), t.type
FROM Transaction t
JOIN t.category c
WHERE t.user.id = :userId
GROUP BY c.name, t.type
ORDER BY SUM(t.amount) DESC
""")
    List<Object[]> getCategorySummary(int userId);

    @Query("""
SELECT t.type, SUM(t.amount)
FROM Transaction t
WHERE t.user.id = :userId
GROUP BY t.type
""")
    List<Object[]> getTypeSummary(int userId);
    @Query("""
SELECT 
    FUNCTION('DATE_FORMAT', t.date, '%Y-%m'),
    SUM(t.amount),
    t.type
FROM Transaction t
WHERE t.user.id = :userId
GROUP BY FUNCTION('DATE_FORMAT', t.date, '%Y-%m'), t.type
ORDER BY 1
""")
    List<Object[]> getMonthlySummary(int userId);
    @Query("""
SELECT t.note, SUM(t.amount)
FROM Transaction t
WHERE t.user.id = :userId
GROUP BY t.note
ORDER BY SUM(t.amount) DESC
""")
    List<Object[]> getMerchantSummary(int userId);
    List<Transaction> findByUserId(int userId);
    List<Transaction> findByUser(User user);
    @Transactional
    void deleteByUser(User user);
    List<Transaction> findByUserAndDateBetweenOrderByDateDesc(User user, Date start, Date end);


}
