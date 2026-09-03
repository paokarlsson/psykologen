package com.example.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Ger varje inloggad användare en egen {@link PsykologenService}, och därmed
 * ett eget samtal, en egen profil/plan och egna promptinställningar.
 *
 * {@link PsykologenService} innehöll redan allt tillstånd som hör till en
 * enskild användare (samtalshistorik, artefaktlagring, promptar) - det som
 * saknades var att sluta dela på instansen. Registret är den delen: en
 * {@link ConcurrentHashMap} från användarnamn till service, med en fabrik som
 * bygger en ny den första gången en användare dyker upp.
 *
 * Instanserna lever så länge processen gör. Vid omstart byggs de om vid nästa
 * inloggning, och eftersom {@link PsykologenService}-konstruktorn tömmer
 * artefaktlagringen börjar användaren då med ett blankt samtal - samma
 * beteende som appen alltid haft, nu bara per konto.
 */
public class UserSessionRegistry {

    private final Map<String, PsykologenService> services = new ConcurrentHashMap<>();
    private final Function<String, PsykologenService> factory;

    public UserSessionRegistry(Function<String, PsykologenService> factory) {
        this.factory = factory;
    }

    /** Användarens service, skapad vid första anropet. */
    public PsykologenService forUser(String username) {
        return services.computeIfAbsent(username, factory);
    }
}
