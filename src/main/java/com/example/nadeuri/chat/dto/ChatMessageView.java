package com.example.nadeuri.chat.dto;

import java.time.LocalDateTime;

/** 화면·WebSocket으로 보내는 메시지 한 줄 (메시지 + 보낸 사람 닉네임·사진) */
public record ChatMessageView(
        Long id,
        Long userId,
        String nickname,
        String profileImageUrl,
        String content,
        LocalDateTime createdAt
) {
}
