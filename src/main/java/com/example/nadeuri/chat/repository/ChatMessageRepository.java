package com.example.nadeuri.chat.repository;

import com.example.nadeuri.chat.domain.ChatMessage;
import com.example.nadeuri.chat.dto.ChatMessageView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * id가 beforeId보다 작은 메시지를 최신 순으로 (이전 메시지 불러오기).
     * 회원과는 외래키가 없어서 user_id로 직접 잇는다. 회원이 없으면 닉네임은 null.
     */
    @Query("""
            SELECT new com.example.nadeuri.chat.dto.ChatMessageView(
                       m.id, m.userId, u.nickname, u.profileImageUrl, m.content, m.createdAt)
            FROM ChatMessage m LEFT JOIN User u ON u.id = m.userId
            WHERE m.gatheringId = :gatheringId AND m.id < :beforeId
            ORDER BY m.id DESC
            """)
    List<ChatMessageView> findBefore(@Param("gatheringId") Long gatheringId, @Param("beforeId") long beforeId,
                                     Pageable limit);

    /** id가 afterId보다 큰 메시지를 오래된 순으로 (연결이 끊겼다 다시 붙었을 때 빠진 것 채우기) */
    @Query("""
            SELECT new com.example.nadeuri.chat.dto.ChatMessageView(
                       m.id, m.userId, u.nickname, u.profileImageUrl, m.content, m.createdAt)
            FROM ChatMessage m LEFT JOIN User u ON u.id = m.userId
            WHERE m.gatheringId = :gatheringId AND m.id > :afterId
            ORDER BY m.id ASC
            """)
    List<ChatMessageView> findAfter(@Param("gatheringId") Long gatheringId, @Param("afterId") long afterId,
                                    Pageable limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessage m WHERE m.gatheringId = :gatheringId")
    int deleteAllByGatheringId(@Param("gatheringId") Long gatheringId);
}
