package com.knowledge.platform.dto;

import lombok.Data;

@Data
public class AudioEpisodeVO {
    private String id;
    private String courseId;
    private String title;
    private String description;
    private Integer duration;
    private Integer sequence;
    private Boolean playable;
    private Integer trialSeconds;
    private Integer progressPosition;
    private Boolean completed;

    public AudioEpisodeVO() {
    }

    public AudioEpisodeVO(
            String id,
            String courseId,
            String title,
            String description,
            Integer duration,
            Integer sequence,
            Boolean playable,
            Integer trialSeconds,
            Integer progressPosition,
            Boolean completed) {
        this.id = id;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.sequence = sequence;
        this.playable = playable;
        this.trialSeconds = trialSeconds;
        this.progressPosition = progressPosition;
        this.completed = completed;
    }
}
