package com.example.nadeuri.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 없는 회원(탈퇴했거나 주소가 틀림). 404 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Object id) {
        super("회원을 찾을 수 없습니다: " + id);
    }
}
