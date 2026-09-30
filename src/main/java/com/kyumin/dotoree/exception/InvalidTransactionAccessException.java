package com.kyumin.dotoree.exception;

public class InvalidTransactionAccessException extends ForbiddenException {
	
	public InvalidTransactionAccessException(String message) {
		super(message);
	}
}
