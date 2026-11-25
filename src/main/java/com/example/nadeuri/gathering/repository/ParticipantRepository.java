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

    /** 이 모임에 참여 기록이 있는 회원 id들 (주최자 포함) */
    @Query("SELECT p.userId FROM Participant p WHERE p.gatheringId = :gatheringId")
    List<Long> findUserIdsByGatheringId(@Param("gatheringId") Long gatheringId);

    Optional<Participant> findByGatheringIdAndUserId(Long gatheringId, Long userId);

    /**
     * 참여자 목록: 주최자 먼저, 그다음 참여한 순서.
     * 참여 기록과 회원은 외래키로 묶여 있지 않아서 user_id로 직접 잇는다.
     * (회원이 지워진 기록은 목록에서 빠진다)
     */
    @Query("""
            SELECT new com.example.nadeuri.gathering.dto.ParticipantView(
                       u.id, u.publicId, u.nickname, u.profileImageUrl, p.host, p.joinedAt)
            FROM Participant p JOIN User u ON u.id = p.userId
            WHERE p.gatheringId = :gatheringId
            ORDER BY p.host DESC, p.joinedAt ASC, p.id ASC
            """)
    List<ParticipantView> findViews(@Param("gatheringId") Long gatheringId);

    /**
     * 참여 취소: 주최자가 아닌 참여 기록 하나를 지운다.
     * DELETE 한 문장이라 같은 요청이 동시에 두 번 와도 하나만 1건을 지우고 나머지는 0건이 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Participant p WHERE p.gatheringId = :gatheringId AND p.userId = :userId AND p.host = false")
    int deleteMember(@Param("gatheringId") Long gatheringId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Participant p WHERE p.gatheringId = :gatheringId")
    int deleteAllByGatheringId(@Param("gatheringId") Long gatheringId);

    /** 이 모임의 참여 기록 수 (주최자 포함) */
    long countByGatheringId(Long gatheringId);

    /**
     * 모임장 넘기기 1단계: 지금 모임장을 일반 참여자로 바꾼다.
     * @return 1이면 성공, 0이면 이 사람이 (더 이상) 모임장이 아님
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Participant p SET p.host = false WHERE p.gatheringId = :gatheringId AND p.userId = :userId AND p.host = true")
    int demoteHost(@Param("gatheringId") Long gatheringId, @Param("userId") Long userId);

    /**
     * 모임장 넘기기 2단계: 참여자 한 명을 모임장으로 바꾼다.
     * @return 1이면 성공, 0이면 이 모임의 참여자가 아님 (그새 참여를 취소한 경우 등)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Participant p SET p.host = true WHERE p.gatheringId = :gatheringId AND p.userId = :userId AND p.host = false")
    int promoteMember(@Param("gatheringId") Long gatheringId, @Param("userId") Long userId);
}
