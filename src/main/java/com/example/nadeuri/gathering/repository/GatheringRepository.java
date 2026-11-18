package com.example.nadeuri.gathering.repository;

import com.example.nadeuri.gathering.domain.Gathering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GatheringRepository extends JpaRepository<Gathering, Long> {

    /**
     * 반경 검색 (하버사인 공식). MySQL과 H2 모두에서 동작한다.
     * 바운딩 박스로 먼저 후보를 거른 뒤(인덱스 사용) 정확한 거리를 계산한다.
     * 경도 범위는 날짜변경선(±180°)을 넘을 수 있어 두 구간(lng1, lng2)으로 나눠 받는다.
     * 넘지 않는 경우 두 구간은 같은 값이다.
     */
    @Query(value = """
            SELECT * FROM gathering g
            WHERE g.latitude BETWEEN :minLat AND :maxLat
              AND (g.longitude BETWEEN :minLng1 AND :maxLng1
                   OR g.longitude BETWEEN :minLng2 AND :maxLng2)
              AND g.start_at > :now
              AND 6371000 * ACOS(GREATEST(-1.0, LEAST(1.0,
                    COS(RADIANS(:lat)) * COS(RADIANS(g.latitude)) * COS(RADIANS(g.longitude) - RADIANS(:lng))
                  + SIN(RADIANS(:lat)) * SIN(RADIANS(g.latitude))))) <= :meters
            ORDER BY g.start_at
            """, nativeQuery = true)
    List<Gathering> findNearby(@Param("lat") double lat, @Param("lng") double lng,
                               @Param("meters") double meters,
                               @Param("minLat") double minLat, @Param("maxLat") double maxLat,
                               @Param("minLng1") double minLng1, @Param("maxLng1") double maxLng1,
                               @Param("minLng2") double minLng2, @Param("maxLng2") double maxLng2,
                               @Param("now") LocalDateTime now);

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
}
