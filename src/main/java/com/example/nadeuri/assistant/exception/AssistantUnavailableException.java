package com.example.nadeuri.assistant.exception;

/** AI 도우미가 답을 줄 수 없을 때 (키 없음, 사용량 초과, 외부 서비스 오류). 메시지는 화면에 그대로 보여 줘도 되는 문장이다. */
public class AssistantUnavailableException extends RuntimeException {
    public AssistantUnavailableException(String message) {
        super(message);
    }
}
