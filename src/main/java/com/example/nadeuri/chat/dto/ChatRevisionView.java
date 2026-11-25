package com.example.nadeuri.chat.dto;

import java.time.LocalDateTime;

/** "수정됨"을 눌렀을 때 보이는 내용 한 벌. current면 지금 보이는 내용 */
public record ChatRevisionView(String content, LocalDateTime writtenAt, boolean current) {
}
