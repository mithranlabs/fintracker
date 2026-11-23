package com.mithran.fintracker.fin_backend.service.universal;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of parsing a bank statement using the universal parser.
 */
public class UniversalStatementParseResult {

    public enum DetectionMethod {
        HEURISTIC,
        LLM,
        FAILED
    }

    private List<UniversalStatementTransaction> transactions;
    private DetectionMethod detectionMethod;
    private double overallConfidence;
    private List<String> warnings;
    private boolean requiresManualReview;

    public UniversalStatementParseResult() {
        this.transactions = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.detectionMethod = DetectionMethod.FAILED;
        this.overallConfidence = 0.0;
        this.requiresManualReview = true;
    }

    public List<UniversalStatementTransaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<UniversalStatementTransaction> transactions) {
        this.transactions = transactions;
    }

    public void addTransaction(UniversalStatementTransaction transaction) {
        this.transactions.add(transaction);
    }

    public DetectionMethod getDetectionMethod() {
        return detectionMethod;
    }

    public void setDetectionMethod(DetectionMethod detectionMethod) {
        this.detectionMethod = detectionMethod;
    }

    public double getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(double overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }

    public void setRequiresManualReview(boolean requiresManualReview) {
        this.requiresManualReview = requiresManualReview;
    }

    public boolean isSuccessful() {
        return !transactions.isEmpty() && 
               detectionMethod != DetectionMethod.FAILED &&
               overallConfidence > 0.5;
    }

    @Override
    public String toString() {
        return "UniversalStatementParseResult{" +
                "transactions=" + transactions.size() +
                ", method=" + detectionMethod +
                ", confidence=" + overallConfidence +
                ", warnings=" + warnings.size() +
                ", requiresReview=" + requiresManualReview +
                '}';
    }
}
