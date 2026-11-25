package com.example.nadeuri.chat.dto;

import java.time.LocalDateTime;

/**
 * 화면·WebSocket으로 보내는 메시지 한 줄 (메시지 + 보낸 사람 닉네임·사진).
 * userId는 회원의 공개 UUID다 (DB 순번 id는 밖으로 내보내지 않는다). 탈퇴한 회원이면 null.
 * id는 메시지 순번으로, "이전 메시지 더 보기"·다시 연결·삭제 때 기준점으로 쓴다.
 * deleted면 content는 빈 문자열이고 화면은 "삭제된 메시지예요"로 그린다.
 * edited면 시간 옆에 "수정됨"이 붙고, 누르면 이전 내용을 볼 수 있다.
 */
public record ChatMessageView(
        Long id,
        String userId,
        String nickname,
        String profileImageUrl,
        String content,
        LocalDateTime createdAt,
        boolean deleted,
        boolean edited
) {
}
