package com.example.nadeuri.review.repository;

import com.example.nadeuri.review.domain.MeetAgain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MeetAgainRepository extends JpaRepository<MeetAgain, Long> {

    /** 받은 "또 만나요" 수. 한 줄 = [받은 사람 users.id, 개수] */
    @Query("""
            SELECT m.toUserId, COUNT(m) FROM MeetAgain m
            WHERE m.toUserId IN :userIds GROUP BY m.toUserId
            """)
    List<Object[]> countReceived(@Param("userIds") Collection<Long> userIds);

    /** 이 모임에서 내가 "또 만나요"를 보낸 사람들 */
    @Query("SELECT m.toUserId FROM MeetAgain m WHERE m.gatheringId = :gatheringId AND m.fromUserId = :fromUserId")
    List<Long> sentTo(@Param("gatheringId") Long gatheringId, @Param("fromUserId") Long fromUserId);

    /** 보낸 것을 거두기. 지운 줄 수 (0이면 보낸 적 없음) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM MeetAgain m
            WHERE m.gatheringId = :gatheringId AND m.fromUserId = :fromUserId AND m.toUserId = :toUserId
            """)
    int deleteOne(@Param("gatheringId") Long gatheringId, @Param("fromUserId") Long fromUserId,
                  @Param("toUserId") Long toUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MeetAgain m WHERE m.gatheringId = :gatheringId")
    void deleteAllByGatheringId(@Param("gatheringId") Long gatheringId);

    /** 탈퇴: 보낸 것과 받은 것 모두 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MeetAgain m WHERE m.fromUserId = :userId OR m.toUserId = :userId")
    void deleteAllInvolving(@Param("userId") Long userId);
}
