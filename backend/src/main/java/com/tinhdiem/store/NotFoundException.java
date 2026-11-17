package com.tinhdiem.store;

/** Không có bàn hoặc ván với id đã cho (HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
