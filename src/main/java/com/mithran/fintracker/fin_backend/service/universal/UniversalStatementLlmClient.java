package com.mithran.fintracker.fin_backend.service.universal;

/**
 * Abstraction for LLM API calls to enable testing without live API calls.
 * Implementations should handle HTTP communication, error handling, and JSON parsing.
 */
public interface UniversalStatementLlmClient {

    /**
     * Request structured transaction parsing from an LLM.
     * 
     * @param statementText The extracted bank statement text
     * @return JSON response from the LLM, or null if unavailable/failed
     * @throws Exception if the API call fails in an unrecoverable way
     */
    String requestStructuredParse(String statementText) throws Exception;

    /**
     * Check if the LLM client is available (API key configured, etc.)
     * 
     * @return true if the client can attempt API calls
     */
    boolean isAvailable();
}
