package com.example.nadeuri.chat.dto;

/**
 * 채팅방 참여자 한 명 (@멘션 자동완성·강조에 쓴다).
 * userId는 회원의 공개 UUID (DB 순번 id는 밖으로 내보내지 않는다).
 */
public record ChatMemberView(String userId, String nickname, String profileImageUrl) {
}
