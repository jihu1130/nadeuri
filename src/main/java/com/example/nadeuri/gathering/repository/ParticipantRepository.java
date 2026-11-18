package com.example.nadeuri.gathering.repository;

import com.example.nadeuri.gathering.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    boolean existsByGatheringIdAndUserId(Long gatheringId, Long userId);
}
