package com.example.nadeuri.chat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 채팅 메시지를 고치기 전 내용 한 벌 ("수정됨"을 누르면 보이는 이전 내용) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_message_revision",
        indexes = @Index(name = "idx_chat_message_revision_message", columnList = "message_id, id"))
public class ChatMessageRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(nullable = false, length = ChatMessage.MAX_LENGTH)
    private String content;

    /** 이 내용이 쓰인 시각 (처음 보낸 시각 또는 앞선 수정 시각) */
    @Column(name = "written_at", nullable = false)
    private LocalDateTime writtenAt;

    public ChatMessageRevision(Long messageId, String content, LocalDateTime writtenAt) {
        this.messageId = messageId;
        this.content = content;
        this.writtenAt = writtenAt;
    }
}
