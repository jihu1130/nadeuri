package com.example.nadeuri.assistant.controller;

import com.example.nadeuri.assistant.dto.AssistantReply;
import com.example.nadeuri.assistant.dto.AssistantRequest;
import com.example.nadeuri.assistant.exception.AssistantRateLimitedException;
import com.example.nadeuri.assistant.exception.AssistantUnavailableException;
import com.example.nadeuri.assistant.service.AssistantService;
import com.example.nadeuri.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 도우미 API (로그인 회원만). POST /api/assistant/chat
 * 요청 {"messages":[{"role":"user","text":"..."}], "lat":37.5, "lng":126.9} → 응답 {"reply":"..."}
 * 오류는 {"message":"화면에 보여 줄 문장"} 과 함께 400 / 401 / 429 / 503.
 */
@RestController
@RequiredArgsConstructor
public class AssistantApiController {

    private final AssistantService assistantService;

    @PostMapping("/api/assistant/chat")
    public ResponseEntity<?> chat(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody AssistantRequest req) {
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인하면 AI 도우미를 쓸 수 있어요.");
        }
        return ResponseEntity.ok(new AssistantReply(assistantService.ask(user.getId(), req)));
    }

    @ExceptionHandler(AssistantRateLimitedException.class)
    ResponseEntity<Map<String, String>> rateLimited(AssistantRateLimitedException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(AssistantUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "질문은 " + AssistantRequest.MAX_TEXT + "자 이내로 적어 주세요.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
