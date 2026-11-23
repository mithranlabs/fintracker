package com.mithran.fintracker.fin_backend.dto;

import java.util.Date;

/**
 * DTO for displaying parsed transactions from universal parser with confidence metadata.
 * Used in preview UI before saving to database.
 */
public class UniversalTransactionDTO {
    private String date;           // formatted date string
    private Double amount;
    private String direction;      // "DEBIT", "CREDIT", or "UNKNOWN"
    private String narration;
    private String parsingMethod;  // "HEURISTIC" or "LLM"
    private Double confidence;     // 0.0 to 1.0
    private boolean needsReview;   // true if direction is UNKNOWN

    public UniversalTransactionDTO() {}

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public String getParsingMethod() {
        return parsingMethod;
    }

    public void setParsingMethod(String parsingMethod) {
        this.parsingMethod = parsingMethod;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }
}
