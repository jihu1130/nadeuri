package com.example.nadeuri.assistant.dto;

/** AI 도우미의 답 (글자만. 화면에서 textContent로 넣고, /gatherings/{uuid} 주소만 링크로 바꾼다). */
public record AssistantReply(String reply) {
}
