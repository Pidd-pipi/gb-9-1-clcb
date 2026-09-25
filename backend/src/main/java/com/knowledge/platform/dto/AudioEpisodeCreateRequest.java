package com.knowledge.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AudioEpisodeCreateRequest {
    @NotBlank(message = "单集标题不能为空")
    private String title;

    private String description;

    private Integer duration;

    private String fileUrl;

    private Integer sequence;
}
