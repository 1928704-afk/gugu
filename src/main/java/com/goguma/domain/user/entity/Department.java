package com.goguma.domain.user.entity;

import lombok.Getter;

@Getter
public enum Department {
    COVENANT("언약부"),
    MILAL("밀알부"),
    JIREH("이레부"),
    UNASSIGNED("미지정");

    private final String description;

    Department(String description) {
        this.description = description;
    }

    public static Department from(String text) {
        if (text == null) return UNASSIGNED;
        for (Department dept : values()) {
            if (dept.description.equals(text) || dept.name().equalsIgnoreCase(text)) {
                return dept;
            }
        }
        return UNASSIGNED;
    }
}
