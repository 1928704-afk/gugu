package com.goguma.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartRequest {

    private String name;

    @JsonProperty("userName")
    private String userName;

    private String department;

    public String getEffectiveName() {
        if (userName != null && !userName.trim().isEmpty()) {
            return userName.trim();
        }
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return "";
    }
}
