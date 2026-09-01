package com.example;

/**
 * Thrown at startup when OPENAI_API_KEY is not configured. Caught by
 * {@link MissingApiKeyFailureAnalyzer} to turn this into a readable
 * startup error instead of a raw stack trace.
 */
public class MissingApiKeyException extends RuntimeException {
    public MissingApiKeyException() {
        super("OPENAI_API_KEY är inte satt.");
    }
}
