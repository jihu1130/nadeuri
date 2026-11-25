package com.example.nadeuri.chat.dto;

/**
 * "입력 중" 표시에 보내는 사람 정보. userId는 회원 공개 UUID (DB 순번 id는 밖으로 내보내지 않는다).
 * 저장하지 않고 같은 방의 다른 사람에게만 실시간으로 보낸다.
 */
public record ChatTypist(String userId, String nickname) {
}
