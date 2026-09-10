package com.example.nabom_market.common.response;

import java.time.LocalDateTime;

import com.example.nabom_market.common.exception.ErrorCode;

public record ErrorResponse(String code, String message, LocalDateTime timestamp) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, LocalDateTime.now());
    }
}
