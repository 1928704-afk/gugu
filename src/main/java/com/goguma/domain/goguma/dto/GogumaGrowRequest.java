package com.goguma.domain.goguma.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GogumaGrowRequest {

    @JsonAlias({"id", "gogumaId"})
    private Long id;

    private String actionType;

    public Long getEffectiveId() {
        return id;
    }
}
