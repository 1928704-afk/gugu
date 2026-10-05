package com.goguma.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StartRequest {

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 20, message = "이름은 최대 20자까지 가능합니다.")
    private String name;

    private String department;
}
