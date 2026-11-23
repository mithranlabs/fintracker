package com.mithran.fintracker.fin_backend.dto;

/**
 * DTO for receiving manual direction corrections from the frontend.
 * Used to collect user feedback for future pattern learning (Phase 4).
 */
public class DirectionCorrectionDTO {
    private Integer transactionIndex;  // index in the parsed batch (for in-memory matching)
    private String originalNarration;
    private String correctedDirection; // "DEBIT" or "CREDIT"
    private String merchant;           // optional: extracted merchant name for pattern learning

    public DirectionCorrectionDTO() {}

    public Integer getTransactionIndex() {
        return transactionIndex;
    }

    public void setTransactionIndex(Integer transactionIndex) {
        this.transactionIndex = transactionIndex;
    }

    public String getOriginalNarration() {
        return originalNarration;
    }

    public void setOriginalNarration(String originalNarration) {
        this.originalNarration = originalNarration;
    }

    public String getCorrectedDirection() {
        return correctedDirection;
    }

    public void setCorrectedDirection(String correctedDirection) {
        this.correctedDirection = correctedDirection;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }
}
