package com.example.nadeuri.user.repository;

import com.example.nadeuri.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    /**
     * 회원 행을 쓰기 잠금으로 읽는다 (SELECT … FOR UPDATE).
     * 같은 회원의 요청을 한 줄로 세울 때 쓴다 (예: 모임 개수 제한 확인 후 만들기).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockById(@Param("id") Long id);
}
