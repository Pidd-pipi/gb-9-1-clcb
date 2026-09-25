package com.knowledge.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AudioCourseCreateRequest {
    @NotBlank(message = "课程标题不能为空")
    private String title;

    @NotBlank(message = "课程简介不能为空")
    private String description;

    private String cover;

    private BigDecimal price = BigDecimal.ZERO;

    private Boolean isSeries = false;
}
