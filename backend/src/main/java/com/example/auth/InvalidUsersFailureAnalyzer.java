package com.example.auth;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns an {@link InvalidUsersException} into a readable startup error
 * (registered via META-INF/spring.factories), instead of a raw stack trace
 * the first time someone tries to log in.
 */
public class InvalidUsersFailureAnalyzer extends AbstractFailureAnalyzer<InvalidUsersException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidUsersException cause) {
        return new FailureAnalysis(
                "Inloggningen kan inte startas: " + cause.getMessage(),
                "Lägg upp minst ett konto innan du startar backend:\n"
                        + "  1. Skapa en lösenordshash:\n"
                        + "     mvn spring-boot:run -Dspring-boot.run.arguments=--hash-password=ditt_lösenord\n"
                        + "  2. Lägg in kontot i backend/.env (en rad per fält, numrerade från 0):\n"
                        + "     APP_AUTH_USERS_0_USERNAME=ditt_användarnamn\n"
                        + "     APP_AUTH_USERS_0_PASSWORDHASH={bcrypt}$2a$10$...\n"
                        + "Användarnamnet blir också namnet på användarens lagringskatalog, därför\n"
                        + "är bara 3-32 tecken med gemener, siffror, - och _ tillåtna.",
                cause);
    }
}
