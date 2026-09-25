package com.knowledge.platform.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AudioCourseDetailVO {
    private String id;
    private String creatorId;
    private CreatorVO creator;
    private String title;
    private String description;
    private String cover;
    private BigDecimal price;
    private Integer episodeCount;
    private Integer totalDuration;
    private Boolean isSeries;
    private String status;
    private Boolean purchased;
    private Boolean owner;
    private Integer trialSeconds;
    private String continueEpisodeId;
    private List<AudioEpisodeVO> episodes;
    private LocalDateTime createdAt;
}
