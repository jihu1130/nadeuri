package com.example.nadeuri.chat.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 모임 채팅 메시지 한 줄. 보낸 사람은 고칠 수 있고(이전 내용은 ChatMessageRevision에 남음),
 * 지울 수 있다 (줄은 남기고 내용만 비움).
 */
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

    /** 지운 시각. null이면 지우지 않은 메시지 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 마지막으로 고친 시각. null이면 고친 적 없음 */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    public ChatMessage(Long gatheringId, Long userId, String content) {
        this.gatheringId = gatheringId;
        this.userId = userId;
        this.content = content;
        this.createdAt = Times.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isEdited() {
        return editedAt != null;
    }

    /** 지금 내용이 쓰인 시각 (고쳤으면 마지막으로 고친 시각) */
    public LocalDateTime currentWrittenAt() {
        return editedAt != null ? editedAt : createdAt;
    }

    /** 내용 고치기. 고치기 전 내용을 돌려준다 (부른 쪽이 수정 기록으로 저장) */
    public ChatMessageRevision edit(String newContent) {
        ChatMessageRevision previous = new ChatMessageRevision(id, content, currentWrittenAt());
        this.content = newContent;
        this.editedAt = Times.now();
        return previous;
    }

    /** 삭제: 내용은 DB에서도 비운다 (다시 볼 수 없음) */
    public void delete() {
        if (isDeleted()) return;
        this.content = "";
        this.deletedAt = Times.now();
    }
}
