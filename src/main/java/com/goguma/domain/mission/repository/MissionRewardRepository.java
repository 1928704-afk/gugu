package com.goguma.domain.mission.repository;

import com.goguma.domain.mission.entity.MissionReward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRewardRepository extends JpaRepository<MissionReward, Long> {
    boolean existsByUserIdAndMissionKeyAndPeriodKey(Long userId, String missionKey, String periodKey);
}
