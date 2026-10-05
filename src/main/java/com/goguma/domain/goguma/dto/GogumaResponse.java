package com.goguma.domain.goguma.dto;

import com.goguma.domain.goguma.entity.Goguma;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class GogumaResponse {

    private final Long id;
    private final String name;
    private final String relation;
    private final Integer age;
    private final int hp;
    private final int stage;
    private final String dominantAction;
    private final String stage2ActionLock;
    private final String stage3ActionLock;
    private final String stage4ActionLock;
    private final Map<String, Integer> actionScores;
    private final Map<String, Boolean> todayActions;

    public GogumaResponse(Goguma goguma, Map<String, Integer> actionScores, Map<String, Boolean> todayActions) {
        this.id = goguma.getId();
        this.name = goguma.getName();
        this.relation = goguma.getRelation() != null ? goguma.getRelation() : "";
        this.age = goguma.getAge();
        this.hp = goguma.getHp();
        this.stage = goguma.getStage();
        this.dominantAction = "bible";
        this.stage2ActionLock = null;
        this.stage3ActionLock = null;
        this.stage4ActionLock = null;
        this.actionScores = actionScores != null ? actionScores : new HashMap<>();
        this.todayActions = todayActions != null ? todayActions : new HashMap<>();
    }

    public GogumaResponse(Goguma goguma) {
        this(goguma, new HashMap<>(), new HashMap<>());
    }
}
