package com.example;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

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
