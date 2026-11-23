package com.example.nadeuri.gathering.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 없는 모임(지워졌거나 주소가 틀림). 404 Not Found로 응답한다.
 * 브라우저에는 templates/error.html, API 호출에는 JSON 오류가 나간다 (Spring Boot 기본 오류 처리).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class GatheringNotFoundException extends RuntimeException {
    public GatheringNotFoundException(Object id) {
        super("모임을 찾을 수 없습니다: " + id);
    }
}
