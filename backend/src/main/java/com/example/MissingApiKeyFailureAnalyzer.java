package com.example;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a {@link MissingApiKeyException} into a readable startup error
 * (registered via META-INF/spring.factories), instead of a raw stack
 * trace or a confusing failure the first time someone tries to chat.
 */
public class MissingApiKeyFailureAnalyzer extends AbstractFailureAnalyzer<MissingApiKeyException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingApiKeyException cause) {
        String envVar = cause.getEnvVar();
        String provider = cause.getProvider();
        return new FailureAnalysis(
                envVar + " är inte satt, så Psykologen-backend kan inte prata med " + provider + ".",
                "Sätt din nyckel innan du startar backend, t.ex.:\n"
                        + "  - Skapa backend/.env med raden " + envVar + "=din_nyckel_här, eller\n"
                        + "  - export " + envVar + "=din_nyckel_här i terminalen du kör mvn spring-boot:run från.\n"
                        + "Eller byt leverantör: sätt AI_PROVIDER=openai / AI_PROVIDER=anthropic.",
                cause);
    }
}
