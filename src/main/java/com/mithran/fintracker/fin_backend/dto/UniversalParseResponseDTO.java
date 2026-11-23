package com.mithran.fintracker.fin_backend.dto;

import java.util.List;

/**
 * Response wrapper for universal parser endpoint.
 * Contains parsed transactions and summary metadata.
 */
public class UniversalParseResponseDTO {
    private boolean success;
    private String message;
    private int totalTransactions;
    private int needsReviewCount;    // count of UNKNOWN directions
    private int heuristicCount;      // parsed by heuristics
    private int llmCount;            // parsed by LLM fallback
    private List<UniversalTransactionDTO> transactions;

    public UniversalParseResponseDTO() {}

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(int totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public int getNeedsReviewCount() {
        return needsReviewCount;
    }

    public void setNeedsReviewCount(int needsReviewCount) {
        this.needsReviewCount = needsReviewCount;
    }

    public int getHeuristicCount() {
        return heuristicCount;
    }

    public void setHeuristicCount(int heuristicCount) {
        this.heuristicCount = heuristicCount;
    }

    public int getLlmCount() {
        return llmCount;
    }

    public void setLlmCount(int llmCount) {
        this.llmCount = llmCount;
    }

    public List<UniversalTransactionDTO> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<UniversalTransactionDTO> transactions) {
        this.transactions = transactions;
    }
}
