package com.example.nadeuri.chat.repository;

import com.example.nadeuri.chat.domain.ChatMessageRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRevisionRepository extends JpaRepository<ChatMessageRevision, Long> {

    /** 오래된 내용부터 */
    List<ChatMessageRevision> findByMessageIdOrderByIdAsc(Long messageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessageRevision r WHERE r.messageId = :messageId")
    int deleteAllByMessageId(@Param("messageId") Long messageId);
}
