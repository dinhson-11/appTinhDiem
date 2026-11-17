package com.tinhdiem.store;

/** Dữ liệu người dùng gửi lên không hợp lệ (HTTP 400). */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
