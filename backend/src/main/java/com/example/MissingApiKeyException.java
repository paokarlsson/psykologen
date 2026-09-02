package com.example;

/**
 * Thrown at startup when the active AI provider's API key is not
 * configured. Caught by {@link MissingApiKeyFailureAnalyzer} to turn
 * this into a readable startup error instead of a raw stack trace.
 */
public class MissingApiKeyException extends RuntimeException {

    private final String provider;
    private final String envVar;

    public MissingApiKeyException(String provider, String envVar) {
        super(envVar + " är inte satt.");
        this.provider = provider;
        this.envVar = envVar;
    }

    public String getProvider() {
        return provider;
    }

    public String getEnvVar() {
        return envVar;
    }
}
