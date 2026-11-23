package com.mithran.fintracker.fin_backend.service.universal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for GroqLlmClient.
 * Note: These tests do NOT make live API calls.
 */
class GroqLlmClientTest {

    private GroqLlmClient client;

    @BeforeEach
    void setUp() {
        client = new GroqLlmClient();
    }

    @Test
    void testIsAvailableWithoutApiKey() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        
        assertFalse(client.isAvailable(), "Should not be available without API key");
    }

    @Test
    void testIsAvailableWithNullApiKey() {
        ReflectionTestUtils.setField(client, "apiKey", null);
        
        assertFalse(client.isAvailable(), "Should not be available with null API key");
    }

    @Test
    void testIsAvailableWithValidApiKey() {
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key-123");
        
        assertTrue(client.isAvailable(), "Should be available with valid API key");
    }

    @Test
    void testRequestWithoutApiKeyThrowsException() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        
        assertThrows(IllegalStateException.class, () -> {
            client.requestStructuredParse("test statement");
        }, "Should throw exception when API key not configured");
    }

    @Test
    void testApiKeyTrimming() {
        ReflectionTestUtils.setField(client, "apiKey", "   ");
        
        assertFalse(client.isAvailable(), "Should not be available with whitespace-only API key");
    }
}
