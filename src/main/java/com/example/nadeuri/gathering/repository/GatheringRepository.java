package com.example.nadeuri.gathering.repository;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GatheringRepository extends JpaRepository<Gathering, Long> {

    /** 주소에 쓰는 공개 식별자(UUID)로 찾기 */
    Optional<Gathering> findByPublicId(String publicId);

    /**
     * 반경 검색 (하버사인 공식). MySQL과 H2 모두에서 동작한다.
     * 아직 끝나지 않은 모임(모집 중 + 진행 중)만 찾는다. 진행 중인 모임은 시작 시간이 앞서므로 먼저 나온다.
     * 바운딩 박스로 먼저 후보를 거른 뒤(인덱스 사용) 정확한 거리를 계산한다.
     * 경도 범위는 날짜변경선(±180°)을 넘을 수 있어 두 구간(lng1, lng2)으로 나눠 받는다.
     * 넘지 않는 경우 두 구간은 같은 값이다.
     * 결과는 시작 시간이 빠른 순으로 최대 limit개까지만 돌려준다 (넓은 반경에서 한꺼번에 너무 많이 내려가지 않게).
     */
    @Query(value = """
            SELECT * FROM gathering g
            WHERE g.latitude BETWEEN :minLat AND :maxLat
              AND (g.longitude BETWEEN :minLng1 AND :maxLng1
                   OR g.longitude BETWEEN :minLng2 AND :maxLng2)
              AND g.end_at > :now
              AND 6371000 * ACOS(GREATEST(-1.0, LEAST(1.0,
                    COS(RADIANS(:lat)) * COS(RADIANS(g.latitude)) * COS(RADIANS(g.longitude) - RADIANS(:lng))
                  + SIN(RADIANS(:lat)) * SIN(RADIANS(g.latitude))))) <= :meters
            ORDER BY g.start_at, g.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Gathering> findNearby(@Param("lat") double lat, @Param("lng") double lng,
                               @Param("meters") double meters,
                               @Param("minLat") double minLat, @Param("maxLat") double maxLat,
                               @Param("minLng1") double minLng1, @Param("maxLng1") double maxLng1,
                               @Param("minLng2") double minLng2, @Param("maxLng2") double maxLng2,
                               @Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 자리가 남아 있을 때만 인원을 1 늘린다. 단일 UPDATE 문이라 동시 요청에도
     * DB가 행 잠금으로 직렬화하므로 maxPeople을 넘지 않는다.
     * @return 1이면 성공, 0이면 모집 마감(또는 없는 모임)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Gathering g SET g.currentPeople = g.currentPeople + 1
            WHERE g.id = :id AND g.currentPeople < g.maxPeople
            """)
    int incrementIfAvailable(@Param("id") Long id);

    /**
     * 모임 수정. 정원이 지금 참여 인원보다 적어지면 바꾸지 않는다 (0건 반환).
     * 조건과 변경을 UPDATE 문 하나로 처리해서, 수정하는 순간 누가 참여해도 정원을 넘지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Gathering g SET g.title = :title, g.description = :description, g.tag = :tag,
                   g.placeName = :placeName, g.latitude = :latitude, g.longitude = :longitude,
                   g.startAt = :startAt, g.endAt = :endAt, g.maxPeople = :maxPeople
            WHERE g.id = :id AND g.currentPeople <= :maxPeople
            """)
    int updateIfFits(@Param("id") Long id, @Param("title") String title, @Param("description") String description,
                     @Param("tag") ActivityTag tag, @Param("placeName") String placeName,
                     @Param("latitude") double latitude, @Param("longitude") double longitude,
                     @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt,
                     @Param("maxPeople") int maxPeople);

    /** 참여 취소: 주최자 1명 아래로는 내려가지 않게 원자적으로 1 줄인다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Gathering g SET g.currentPeople = g.currentPeople - 1
            WHERE g.id = :id AND g.currentPeople > 1
            """)
    int decrementIfAbove1(@Param("id") Long id);

    /** 이 회원이 만든(host=true) 또는 참여한(host=false) 모임, 시작 시각 순 */
    @Query("""
            SELECT g FROM Gathering g
            WHERE g.id IN (SELECT p.gatheringId FROM Participant p WHERE p.userId = :userId AND p.host = :host)
            ORDER BY g.startAt
            """)
    List<Gathering> findByMember(@Param("userId") Long userId, @Param("host") boolean host);
}
