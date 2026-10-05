package com.goguma.domain.goguma.entity;

import com.goguma.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "gogumas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goguma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(length = 30)
    private String relation;

    private Integer age;

    @Column(nullable = false)
    private int hp = 10;

    // 동시성 제어를 위한 낙관적 락(Optimistic Lock)
    @Version
    private Long version;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Goguma(User user, String name, String relation, Integer age) {
        this.user = user;
        this.name = name;
        this.relation = relation;
        this.age = age;
        this.hp = 10;
        this.createdAt = LocalDateTime.now();
    }

    public void addHp(int amount) {
        this.hp += amount;
    }

    public int getStage() {
        if (hp >= 100) return 5;
        if (hp >= 60) return 4;
        if (hp >= 30) return 3;
        if (hp >= 15) return 2;
        return 1;
    }
}
