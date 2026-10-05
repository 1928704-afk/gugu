package com.goguma.domain.goguma.dto;

import com.goguma.domain.goguma.entity.Goguma;
import lombok.Getter;

@Getter
public class GogumaResponse {

    private final Long id;
    private final String name;
    private final String relation;
    private final Integer age;
    private final int hp;
    private final int stage;

    public GogumaResponse(Goguma goguma) {
        this.id = goguma.getId();
        this.name = goguma.getName();
        this.relation = goguma.getRelation();
        this.age = goguma.getAge();
        this.hp = goguma.getHp();
        this.stage = goguma.getStage();
    }
}
