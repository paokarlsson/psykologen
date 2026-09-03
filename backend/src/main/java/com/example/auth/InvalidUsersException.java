package com.example.auth;

/**
 * Kastas vid uppstart när {@code app.auth.users} saknas eller är felaktigt
 * ifyllt. Fångas av {@link InvalidUsersFailureAnalyzer} för att bli ett
 * läsbart startfel i stället för en stack trace - samma mönster som
 * {@link com.example.MissingApiKeyException}.
 */
public class InvalidUsersException extends RuntimeException {

    public InvalidUsersException(String message) {
        super(message);
    }
}
