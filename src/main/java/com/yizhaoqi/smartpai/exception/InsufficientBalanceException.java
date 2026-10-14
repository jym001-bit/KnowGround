package com.yizhaoqi.smartpai.exception;

public class InsufficientBalanceException extends RateLimitExceededException {
    public InsufficientBalanceException(String message) {
        super(message, 0);
    }
}
