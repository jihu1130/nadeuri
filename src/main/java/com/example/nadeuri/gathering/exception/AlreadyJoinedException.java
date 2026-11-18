package com.example.nadeuri.gathering.exception;

public class AlreadyJoinedException extends RuntimeException {
    public AlreadyJoinedException() {
        super("이미 참여한 모임이에요.");
    }
}
