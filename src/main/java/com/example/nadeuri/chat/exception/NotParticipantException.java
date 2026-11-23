package com.example.nadeuri.chat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 모임에 참여하지 않은 사람이 채팅을 보거나 보내려고 할 때 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class NotParticipantException extends RuntimeException {
    public NotParticipantException() {
        super("모임에 참여한 사람만 채팅할 수 있어요.");
    }
}
