package com.goguma.domain.goguma.entity;

import com.goguma.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "actions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_goguma_action_date",
            columnNames = {"user_id", "goguma_id", "action_type", "action_date"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GogumaAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goguma_id", nullable = false)
    private Goguma goguma;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ActionType actionType;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public GogumaAction(User user, Goguma goguma, ActionType actionType, LocalDate actionDate) {
        this.user = user;
        this.goguma = goguma;
        this.actionType = actionType;
        this.actionDate = actionDate;
        this.createdAt = LocalDateTime.now();
    }
}
