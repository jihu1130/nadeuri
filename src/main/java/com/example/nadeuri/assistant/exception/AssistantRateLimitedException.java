package com.example.nadeuri.assistant.exception;

/** 한 사람이 너무 자주 물어봤을 때 (무료 사용량을 한 사람이 다 쓰지 않게). */
public class AssistantRateLimitedException extends RuntimeException {
    public AssistantRateLimitedException(String message) {
        super(message);
    }
}
