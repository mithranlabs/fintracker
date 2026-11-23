package com.mithran.fintracker.fin_backend.service.universal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a single parsed transaction from a universal bank statement.
 * Independent of JPA Transaction entity.
 */
public class UniversalStatementTransaction {

    public enum Direction {
        DEBIT,
        CREDIT,
        UNKNOWN
    }

    private LocalDate transactionDate;
    private String narration;
    private BigDecimal amount;
    private Direction direction;
    private BigDecimal accountBalance;
    private String transactionReference;
    private double confidenceScore;
    private String sourceText;

    public UniversalStatementTransaction() {
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniversalStatementTransaction that = (UniversalStatementTransaction) o;
        return Objects.equals(transactionDate, that.transactionDate) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(narration, that.narration) &&
                direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionDate, narration, amount, direction);
    }

    @Override
    public String toString() {
        return "UniversalStatementTransaction{" +
                "date=" + transactionDate +
                ", amount=" + amount +
                ", direction=" + direction +
                ", narration='" + narration + '\'' +
                ", confidence=" + confidenceScore +
                '}';
    }
}
