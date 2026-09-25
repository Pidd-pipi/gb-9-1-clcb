package com.knowledge.platform.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "audio_progress")
@CompoundIndex(name = "user_episode_idx", def = "{'userId': 1, 'episodeId': 1}", unique = true)
public class AudioProgress {
    @Id
    private String id;

    private String userId;

    private String courseId;

    private String episodeId;

    private Integer position;

    private Integer duration;

    private Boolean completed = false;

    private LocalDateTime updatedAt;
}
