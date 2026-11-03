package com.oznwoo.gopssl.global.exception;

/** 기존 FastAPI와 같은 에러 응답 형식: {@code { "detail": "메시지" }}. */
public record ApiError(String detail) {
}
