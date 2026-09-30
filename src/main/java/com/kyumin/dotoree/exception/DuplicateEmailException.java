package com.kyumin.dotoree.exception;

public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}