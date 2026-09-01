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
        return new FailureAnalysis(
                "OPENAI_API_KEY är inte satt, så Psykologen-backend kan inte prata med OpenAI.",
                "Sätt din nyckel innan du startar backend, t.ex.:\n"
                        + "  - Skapa backend/.env med raden OPENAI_API_KEY=din_nyckel_här, eller\n"
                        + "  - export OPENAI_API_KEY=din_nyckel_här i terminalen du kör mvn spring-boot:run från.",
                cause);
    }
}
