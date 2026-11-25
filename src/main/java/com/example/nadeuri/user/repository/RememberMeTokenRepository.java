package com.example.nadeuri.user.repository;

import com.example.nadeuri.user.domain.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, String> {

    @Modifying
    @Query("delete from RememberMeToken t where t.userId = :userId")
    int deleteByUserId(Long userId);

    long countByUserId(Long userId);
}
