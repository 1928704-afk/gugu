package com.goguma.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Department department = Department.UNASSIGNED;

    @Column(name = "total_visit_days", nullable = false)
    private int totalVisitDays = 1;

    @Column(name = "last_visit_date", nullable = false)
    private LocalDate lastVisitDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public User(String name, Department department) {
        this.name = name;
        this.department = department != null ? department : Department.UNASSIGNED;
        this.lastVisitDate = LocalDate.now();
        this.totalVisitDays = 1;
        this.createdAt = LocalDateTime.now();
    }

    public void recordVisit(LocalDate today) {
        if (lastVisitDate == null || !lastVisitDate.isEqual(today)) {
            this.totalVisitDays += 1;
            this.lastVisitDate = today;
        }
    }
}
