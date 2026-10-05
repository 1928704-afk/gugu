package com.goguma.domain.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RankingResponse {
    private String department;
    private double avgHp;
    private long gogumaCount;
    private long userCount;
}
