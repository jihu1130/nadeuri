package com.example.nadeuri.chat.service;

import com.example.nadeuri.chat.domain.ChatMessage;
import com.example.nadeuri.chat.dto.ChatMessageView;
import com.example.nadeuri.chat.exception.NotParticipantException;
import com.example.nadeuri.chat.repository.ChatMessageRepository;
import com.example.nadeuri.gathering.repository.ParticipantRepository;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 모임 채팅. 그 모임의 참여 기록(주최자 포함)이 있는 회원만 읽고 쓸 수 있다.
 * 참여를 취소하면 그때부터 읽지도 쓰지도 못한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    public static final int PAGE_SIZE = 50;

    private final ChatMessageRepository repository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public boolean canChat(Long gatheringId, Long userId) {
        return userId != null && participantRepository.existsByGatheringIdAndUserId(gatheringId, userId);
    }

    /** 지금 채팅할 수 있는 회원 id들. 참여를 취소한 사람에게 새 메시지가 가지 않게 보낼 때마다 확인한다 */
    public Set<Long> members(Long gatheringId) {
        return new HashSet<>(participantRepository.findUserIdsByGatheringId(gatheringId));
    }

    private void checkCanChat(Long gatheringId, Long userId) {
        if (!canChat(gatheringId, userId)) {
            throw new NotParticipantException();
        }
    }

    /**
     * 최근 메시지 (beforeId가 없으면 가장 최근부터), 화면에 그리기 좋게 오래된 순으로 돌려준다.
     * @throws NotParticipantException 참여자가 아님
     */
    public List<ChatMessageView> history(Long gatheringId, Long userId, Long beforeId) {
        checkCanChat(gatheringId, userId);
        List<ChatMessageView> latestFirst = repository.findBefore(gatheringId,
                beforeId == null ? Long.MAX_VALUE : beforeId, PageRequest.of(0, PAGE_SIZE));
        List<ChatMessageView> result = new ArrayList<>(latestFirst);
        Collections.reverse(result);
        return result;
    }

    /** afterId 다음 메시지들 (다시 연결됐을 때 빠진 것 채우기) */
    public List<ChatMessageView> since(Long gatheringId, Long userId, long afterId) {
        checkCanChat(gatheringId, userId);
        return repository.findAfter(gatheringId, afterId, PageRequest.of(0, PAGE_SIZE * 4));
    }

    /**
     * 메시지 저장. 앞뒤 공백을 지우고 1~500자만 받는다.
     * @throws NotParticipantException  참여자가 아님
     * @throws IllegalArgumentException 빈 메시지 또는 너무 긴 메시지
     */
    @Transactional
    public ChatMessageView send(Long gatheringId, Long userId, String content) {
        checkCanChat(gatheringId, userId);
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
        if (text.length() > ChatMessage.MAX_LENGTH) {
            throw new IllegalArgumentException("메시지는 " + ChatMessage.MAX_LENGTH + "자까지 보낼 수 있어요.");
        }
        ChatMessage m = repository.save(new ChatMessage(gatheringId, userId, text));
        User u = userRepository.findById(userId).orElse(null);
        return new ChatMessageView(m.getId(), userId,
                u == null ? null : u.getNickname(), u == null ? null : u.getProfileImageUrl(),
                m.getContent(), m.getCreatedAt());
    }
}
