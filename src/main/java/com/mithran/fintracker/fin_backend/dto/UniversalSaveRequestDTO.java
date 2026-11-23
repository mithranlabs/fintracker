package com.mithran.fintracker.fin_backend.dto;

import java.util.List;

/**
 * Request DTO for saving confirmed universal parser transactions to database.
 * Includes manual corrections applied by user during preview.
 */
public class UniversalSaveRequestDTO {
    private List<UniversalTransactionDTO> transactions;
    private boolean replaceExisting;  // replace all user transactions if true
    private String fileName;          // original uploaded file name

    public UniversalSaveRequestDTO() {}

    public List<UniversalTransactionDTO> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<UniversalTransactionDTO> transactions) {
        this.transactions = transactions;
    }

    public boolean isReplaceExisting() {
        return replaceExisting;
    }

    public void setReplaceExisting(boolean replaceExisting) {
        this.replaceExisting = replaceExisting;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
