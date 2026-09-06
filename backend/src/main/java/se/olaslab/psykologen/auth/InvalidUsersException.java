package se.olaslab.psykologen.auth;

public class InvalidUsersException extends RuntimeException {

    public InvalidUsersException(String message) {
        super(message);
    }
}
