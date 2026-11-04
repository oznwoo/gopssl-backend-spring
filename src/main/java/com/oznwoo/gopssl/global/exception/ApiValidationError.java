package com.oznwoo.gopssl.global.exception;

import java.util.List;

/** 요청 검증 실패(422) 응답. v1 OpenAPI의 {@code HTTPValidationError}와 같은 모양이다. */
public record ApiValidationError(List<Item> detail) {

    /**
     * v1 OpenAPI의 {@code ValidationError}에 대응한다.
     *
     * @param loc 위치. {@code ["body", "lines", 0, "price"]}처럼 문자열과 정수가 섞인다.
     * @param msg 프론트가 {@code detail[0].msg}를 사용자에게 그대로 보여준다.
     * @param input 거부된 입력값. v1처럼 요청 값을 그대로 돌려준다.
     */
    public record Item(String type, List<Object> loc, String msg, Object input) {
    }
}
