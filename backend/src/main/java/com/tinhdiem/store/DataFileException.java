package com.tinhdiem.store;

/** File dữ liệu tồn tại nhưng không đọc được hoặc sai cấu trúc; ứng dụng từ chối khởi động. */
public class DataFileException extends RuntimeException {
    public DataFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
