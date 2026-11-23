package com.example.nadeuri.chat.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 모임 채팅 메시지 한 줄. 고치거나 지우는 기능은 아직 없다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_message",
        indexes = @Index(name = "idx_chat_message_gathering", columnList = "gathering_id, id"))
public class ChatMessage {

    public static final int MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gathering_id", nullable = false)
    private Long gatheringId;

    /** 보낸 사람 users.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ChatMessage(Long gatheringId, Long userId, String content) {
        this.gatheringId = gatheringId;
        this.userId = userId;
        this.content = content;
        this.createdAt = Times.now();
    }
}
