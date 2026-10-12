package com.onatarslan.deeptodo.auth.exception;

public class EmailAlreadyExistException extends RuntimeException {
    public EmailAlreadyExistException() {
        super("email already exists");
    }
}
