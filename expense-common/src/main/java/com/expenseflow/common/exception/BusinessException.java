package com.expenseflow.common.exception;

public class BusinessException extends RuntimeException {

    private final int code;

    public int getCode() {
        return code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
