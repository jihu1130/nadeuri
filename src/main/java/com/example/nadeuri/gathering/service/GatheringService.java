package com.example.nadeuri.gathering.service;

import com.example.nadeuri.chat.repository.ChatMessageRepository;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.domain.Participant;
import com.example.nadeuri.gathering.dto.GatheringCreateRequest;
import com.example.nadeuri.gathering.dto.ParticipantView;
import com.example.nadeuri.gathering.exception.AlreadyJoinedException;
import com.example.nadeuri.gathering.exception.GatheringNotFoundException;
import com.example.nadeuri.gathering.exception.NotHostException;
import com.example.nadeuri.gathering.repository.GatheringRepository;
import com.example.nadeuri.gathering.repository.ParticipantRepository;
import com.example.nadeuri.global.util.Times;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatheringService {

    public static final double MIN_RADIUS = 100;
    public static final double MAX_RADIUS = 20_000;
    private static final double METERS_PER_DEGREE = 111_320.0;
    /** 주변 검색 한 번에 돌려주는 최대 모임 수 */
    public static final int MAX_NEARBY_RESULTS = 200;

    private final GatheringRepository repository;
    private final ParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;

    public List<Gathering> findNearby(double lat, double lng, double radiusMeters) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("좌표 범위가 올바르지 않습니다.");
        }
        double radius = Math.max(MIN_RADIUS, Math.min(radiusMeters, MAX_RADIUS));

        double latDelta = radius / METERS_PER_DEGREE;
        double minLat = Math.max(-90, lat - latDelta);
        double maxLat = Math.min(90, lat + latDelta);

        double[] lng1;
        double[] lng2;
        double cosLat = Math.cos(Math.toRadians(lat));
        double lngDelta = cosLat < 1e-6 ? 360 : radius / (METERS_PER_DEGREE * cosLat);

        if (minLat <= -90 || maxLat >= 90 || lngDelta >= 180) {
            // 극지방이 반경에 포함되면 모든 경도가 후보
            lng1 = lng2 = new double[]{-180, 180};
        } else {
            double minLng = lng - lngDelta;
            double maxLng = lng + lngDelta;
            if (minLng < -180) {          // 서쪽으로 날짜변경선을 넘음
                lng1 = new double[]{minLng + 360, 180};
                lng2 = new double[]{-180, maxLng};
            } else if (maxLng > 180) {    // 동쪽으로 날짜변경선을 넘음
                lng1 = new double[]{minLng, 180};
                lng2 = new double[]{-180, maxLng - 360};
            } else {
                lng1 = lng2 = new double[]{minLng, maxLng};
            }
        }

        return repository.findNearby(lat, lng, radius, minLat, maxLat,
                lng1[0], lng1[1], lng2[0], lng2[1], Times.now(), MAX_NEARBY_RESULTS);
    }

    public Gathering get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new GatheringNotFoundException(id));
    }

    public boolean hasJoined(Long gatheringId, Long userId) {
        return userId != null
                && participantRepository.existsByGatheringIdAndUserId(gatheringId, userId);
    }

    @Transactional
    public Gathering create(GatheringCreateRequest req, Long hostUserId) {
        Gathering g = repository.save(req.toEntity());
        participantRepository.save(new Participant(g.getId(), hostUserId, true));
        return g;
    }

    /**
     * 참여 처리.
     * 1) 인원을 원자적으로 증가 → 0건이면 마감. 참여 기록은 건드리지 않고 바로 끝낸다
     * 2) 참여 기록 저장 → 같은 사람이 동시에 눌러도 유니크 제약이 막고,
     *    그 예외로 트랜잭션 전체(1의 증가 포함)가 롤백된다
     * 마감 요청이 참여 기록을 넣었다 롤백하지 않도록 이 순서로 한다
     * (H2는 동시에 롤백된 자동 번호를 다시 내줘서 PK 충돌이 나기도 함).
     * 진행 중인 모임에도 늦게 합류할 수 있다 (끝난 모임만 막음).
     *
     * @throws AlreadyJoinedException 이미 참여한 모임
     * @throws IllegalStateException  모집 마감 또는 이미 끝난 모임
     */
    @Transactional
    public void join(Long id, Long userId) {
        Gathering g = get(id);
        if (g.isEndedAt(Times.now())) {
            throw new IllegalStateException("이미 끝난 모임이에요.");
        }
        if (participantRepository.existsByGatheringIdAndUserId(id, userId)) {
            throw new AlreadyJoinedException();
        }
        if (repository.incrementIfAvailable(id) == 0) {
            throw new IllegalStateException("모집 인원이 가득 찼습니다.");
        }
        participantRepository.saveAndFlush(new Participant(id, userId, false));
    }

    /** 참여자 목록 (주최자 먼저) */
    public List<ParticipantView> participants(Long gatheringId) {
        return participantRepository.findViews(gatheringId);
    }

    public boolean isHost(Long gatheringId, Long userId) {
        return userId != null && participantRepository.findByGatheringIdAndUserId(gatheringId, userId)
                .map(Participant::isHost).orElse(false);
    }

    /**
     * 참여 취소. 참여 기록을 지우고 인원을 1 줄인다.
     * 실제로 지운 경우에만 인원을 줄여서, 취소를 동시에 두 번 눌러도 한 번만 빠진다.
     * @throws IllegalStateException 이미 시작된 모임, 참여하지 않은(또는 이미 취소한) 모임, 주최자인 경우
     */
    @Transactional
    public void leave(Long id, Long userId) {
        Gathering g = get(id);
        if (!g.getStartAt().isAfter(Times.now())) {
            throw new IllegalStateException("이미 시작된 모임은 참여를 취소할 수 없어요.");
        }
        Participant p = participantRepository.findByGatheringIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalStateException("참여하지 않은 모임이에요."));
        if (p.isHost()) {
            throw new IllegalStateException("주최자는 참여를 취소할 수 없어요. 모임을 없애려면 모임 삭제를 눌러 주세요.");
        }
        if (participantRepository.deleteMember(id, userId) == 0) {
            throw new IllegalStateException("이미 참여를 취소했어요.");
        }
        repository.decrementIfAbove1(id);
    }

    /**
     * 모임 삭제 (주최자만). 참여 기록과 채팅 메시지도 함께 지운다.
     * @throws NotHostException 주최자가 아님
     */
    @Transactional
    public void delete(Long id, Long userId) {
        Gathering g = get(id);
        if (!isHost(id, userId)) {
            throw new NotHostException();
        }
        chatMessageRepository.deleteAllByGatheringId(id);
        participantRepository.deleteAllByGatheringId(id);
        repository.delete(g);
    }

    /**
     * 주최자가 모임을 고칠 수 있는지 확인 (수정 화면을 열 때와 저장할 때 모두 사용).
     * @throws NotHostException     주최자가 아님
     * @throws IllegalStateException 이미 시작된 모임
     */
    public Gathering getForEdit(Long id, Long userId) {
        Gathering g = get(id);
        if (!isHost(id, userId)) {
            throw new NotHostException();
        }
        if (!g.getStartAt().isAfter(Times.now())) {
            throw new IllegalStateException("이미 시작된 모임은 수정할 수 없어요.");
        }
        return g;
    }

    /**
     * 모임 수정 (주최자만, 시작 전에만).
     * @throws IllegalArgumentException 정원을 지금 참여 인원보다 적게 줄이려는 경우
     */
    @Transactional
    public void update(Long id, Long userId, GatheringCreateRequest req) {
        getForEdit(id, userId);
        int updated = repository.updateIfFits(id, req.title(), req.description(), req.tag(), req.placeName(),
                req.latitude(), req.longitude(), req.startAt(), req.endAt(), req.maxPeople());
        if (updated == 0) {
            throw new IllegalArgumentException(
                    "지금 참여한 사람이 " + get(id).getCurrentPeople() + "명이라 정원을 그보다 적게 줄일 수 없어요.");
        }
    }

    /**
     * 회원 탈퇴할 때 그 회원의 모임 기록을 정리한다.
     * - 만든 모임: 모임 삭제와 같이 지운다 (참여 기록 포함)
     * - 참여한 모임: 참여 기록을 지우고 인원을 1 줄인다 (끝난 모임 포함, 인원 수를 맞추기 위해)
     */
    @Transactional
    public void removeAllFor(Long userId) {
        for (Gathering g : hostedBy(userId)) {
            delete(g.getId(), userId);
        }
        for (Gathering g : joinedBy(userId)) {
            if (participantRepository.deleteMember(g.getId(), userId) > 0) {
                repository.decrementIfAbove1(g.getId());
            }
        }
    }

    /** 내가 만든 모임 */
    public List<Gathering> hostedBy(Long userId) {
        return repository.findByMember(userId, true);
    }

    /** 내가 참여한 모임 (내가 만든 모임은 빼고) */
    public List<Gathering> joinedBy(Long userId) {
        return repository.findByMember(userId, false);
    }
}
