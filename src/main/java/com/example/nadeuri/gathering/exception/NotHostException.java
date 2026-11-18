package com.example.nadeuri.gathering.exception;

/** 주최자만 할 수 있는 일(모임 삭제 등)을 다른 사람이 하려고 할 때 */
public class NotHostException extends RuntimeException {
    public NotHostException() {
        super("모임을 만든 사람만 할 수 있어요.");
    }
}
