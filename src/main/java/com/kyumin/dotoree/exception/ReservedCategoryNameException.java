package com.kyumin.dotoree.exception;

public class ReservedCategoryNameException extends BadRequestException {
	
	public ReservedCategoryNameException(String message) {
		super(message);
	}
}
