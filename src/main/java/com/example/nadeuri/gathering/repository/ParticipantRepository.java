package com.example.nadeuri.gathering.repository;

import com.example.nadeuri.gathering.domain.Participant;
import com.example.nadeuri.gathering.dto.ParticipantView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    boolean existsByGatheringIdAndUserId(Long gatheringId, Long userId);

    Optional<Participant> findByGatheringIdAndUserId(Long gatheringId, Long userId);

    /**
     * 참여자 목록: 주최자 먼저, 그다음 참여한 순서.
     * 참여 기록과 회원은 외래키로 묶여 있지 않아서 user_id로 직접 잇는다.
     * (회원이 지워진 기록은 목록에서 빠진다)
     */
    @Query("""
            SELECT new com.example.nadeuri.gathering.dto.ParticipantView(
                       u.id, u.nickname, u.profileImageUrl, p.host, p.joinedAt)
            FROM Participant p JOIN User u ON u.id = p.userId
            WHERE p.gatheringId = :gatheringId
            ORDER BY p.host DESC, p.joinedAt ASC, p.id ASC
            """)
    List<ParticipantView> findViews(@Param("gatheringId") Long gatheringId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Participant p WHERE p.gatheringId = :gatheringId")
    int deleteAllByGatheringId(@Param("gatheringId") Long gatheringId);
}
