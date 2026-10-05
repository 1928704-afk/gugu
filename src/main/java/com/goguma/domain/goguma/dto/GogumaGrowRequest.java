package com.goguma.domain.goguma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GogumaGrowRequest {

    @NotNull(message = "고구마 ID는 필수입니다.")
    private Long gogumaId;

    @NotBlank(message = "액션 타입은 필수입니다.")
    private String actionType;
}
