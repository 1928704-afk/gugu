package com.goguma.domain.mission.entity;

import com.goguma.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "mission_rewards",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_mission_period",
            columnNames = {"user_id", "mission_key", "period_key"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "mission_key", nullable = false, length = 50)
    private String missionKey;

    @Column(name = "period_key", nullable = false, length = 30)
    private String periodKey;

    @Column(name = "reward_hp", nullable = false)
    private int rewardHp;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MissionReward(User user, String missionKey, String periodKey, int rewardHp) {
        this.user = user;
        this.missionKey = missionKey;
        this.periodKey = periodKey;
        this.rewardHp = rewardHp;
        this.createdAt = LocalDateTime.now();
    }
}
