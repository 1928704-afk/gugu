package com.goguma.domain.community.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PostAddRequest {
    private String category;
    private String title;
    private String content;
    private String imageData;
}
