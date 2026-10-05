package com.goguma.domain.goguma.entity;

import lombok.Getter;

@Getter
public enum ActionType {
    POST_WRITE("postWrite", 1, "게시판 작성"),
    BIBLE("bible", 1, "말씀읽기"),
    PRAYER("prayer", 1, "기도(부탁)하기"),
    CONTACT("contact", 2, "연락&만남"),
    INVITE("invite", 8, "권유하기");

    private final String key;
    private final int expValue;
    private final String label;

    ActionType(String key, int expValue, String label) {
        this.key = key;
        this.expValue = expValue;
        this.label = label;
    }

    public static ActionType fromKey(String key) {
        for (ActionType type : values()) {
            if (type.key.equalsIgnoreCase(key) || type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("유효하지 않은 액션 타입입니다: " + key);
    }
}
