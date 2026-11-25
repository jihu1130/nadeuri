package com.example.nadeuri.review.repository;

import com.example.nadeuri.review.domain.GatheringReview;
import com.example.nadeuri.review.dto.ReviewView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GatheringReviewRepository extends JpaRepository<GatheringReview, Long> {

    Optional<GatheringReview> findByGatheringIdAndUserId(Long gatheringId, Long userId);

    /** 모임 후기 목록 (최근에 쓰거나 고친 것부터) */
    @Query("""
            SELECT new com.example.nadeuri.review.dto.ReviewView(
                       u.id, u.publicId, u.nickname, u.profileImageUrl, r.rating, r.content, r.updatedAt)
            FROM GatheringReview r JOIN User u ON u.id = r.userId
            WHERE r.gatheringId = :gatheringId
            ORDER BY r.updatedAt DESC, r.id DESC
            """)
    List<ReviewView> findViews(@Param("gatheringId") Long gatheringId);

    /**
     * 모임장 평점: 지금 모임장인 모임에 다른 참여자들이 준 별점. 한 줄 = [모임장 users.id, 평균, 개수]
     * 모임장 본인이 자기 모임에 준 별점은 넣지 않는다.
     */
    @Query("""
            SELECT p.userId, AVG(r.rating), COUNT(r)
            FROM GatheringReview r, Participant p
            WHERE p.gatheringId = r.gatheringId AND p.host = true AND r.userId <> p.userId
              AND p.userId IN :userIds
            GROUP BY p.userId
            """)
    List<Object[]> hostRatings(@Param("userIds") Collection<Long> userIds);

    /** 이 사람이 후기를 쓴 모임들 (내 모임 화면에서 "후기 남기기"를 가릴 때) */
    @Query("SELECT r.gatheringId FROM GatheringReview r WHERE r.userId = :userId AND r.gatheringId IN :gatheringIds")
    List<Long> reviewedGatheringIds(@Param("userId") Long userId, @Param("gatheringIds") Collection<Long> gatheringIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GatheringReview r WHERE r.gatheringId = :gatheringId AND r.userId = :userId")
    int deleteMine(@Param("gatheringId") Long gatheringId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GatheringReview r WHERE r.gatheringId = :gatheringId")
    void deleteAllByGatheringId(@Param("gatheringId") Long gatheringId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GatheringReview r WHERE r.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
