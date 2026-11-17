package com.tinhdiem.web;

import com.tinhdiem.store.NotFoundException;
import com.tinhdiem.store.ValidationException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Mọi lỗi API trả về dạng {"error": "..."}. Content-Type JSON đặt tường minh vì SSE gửi Accept: text/event-stream. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<Map<String, String>> validation(ValidationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đọc được (JSON không hợp lệ).");
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    ResponseEntity<Map<String, String>> ioFailure(UncheckedIOException e) {
        log.error("Không ghi được file dữ liệu", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Không ghi được dữ liệu, thử lại sau.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Map.of("error", message));
    }
}
