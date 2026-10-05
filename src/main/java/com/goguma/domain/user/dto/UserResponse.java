package com.goguma.domain.user.dto;

import com.goguma.domain.user.entity.User;
import lombok.Getter;

@Getter
public class UserResponse {

    private final Long id;
    private final String name;
    private final String department;
    private final int totalVisitDays;

    public UserResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.department = user.getDepartment().getDescription();
        this.totalVisitDays = user.getTotalVisitDays();
    }
}
