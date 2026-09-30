package com.kyumin.dotoree.exception;

public class InvalidCategoryHierarchyException extends BadRequestException {

    public InvalidCategoryHierarchyException(String message) {
        super(message);
    }
}
