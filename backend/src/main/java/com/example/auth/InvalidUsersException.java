package com.example.auth;

public class InvalidUsersException extends RuntimeException {

    public InvalidUsersException(String message) {
        super(message);
    }
}
