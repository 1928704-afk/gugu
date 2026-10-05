package com.goguma.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartRequest {

    @JsonAlias({"name", "userName"})
    private String name;

    private String department;

    public String getEffectiveName() {
        return name != null ? name.trim() : "사용자";
    }
}
