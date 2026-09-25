package com.knowledge.platform.dto;

import lombok.Data;

@Data
public class AudioProgressRequest {
    private Integer position;

    private Integer duration;

    private Boolean completed;
}
