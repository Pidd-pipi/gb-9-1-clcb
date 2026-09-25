package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.AudioProgress;
import com.knowledge.platform.repository.AudioProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AudioProgressService {

    @Autowired
    private AudioProgressRepository audioProgressRepository;

    public ApiResponse<AudioProgress> getProgress(String userId, String courseId, String episodeId) {
        return audioProgressRepository.findByUserIdAndEpisodeId(userId, episodeId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.success(null));
    }

    public ApiResponse<String> getContinueEpisodeId(String userId, String courseId) {
        String episodeId = audioProgressRepository
                .findFirstByUserIdAndCourseIdOrderByUpdatedAtDesc(userId, courseId)
                .map(AudioProgress::getEpisodeId)
                .orElse(null);
        return ApiResponse.success(episodeId);
    }

    public ApiResponse<AudioProgress> saveProgress(String userId, String courseId, String episodeId,
                                                   Integer position, Integer duration, Boolean completed) {
        AudioProgress progress = audioProgressRepository
                .findByUserIdAndEpisodeId(userId, episodeId)
                .orElseGet(() -> {
                    AudioProgress p = new AudioProgress();
                    p.setUserId(userId);
                    p.setCourseId(courseId);
                    p.setEpisodeId(episodeId);
                    return p;
                });

        if (position != null && position >= 0) {
            progress.setPosition(position);
        }
        if (duration != null && duration >= 0) {
            progress.setDuration(duration);
        }
        if (completed != null) {
            progress.setCompleted(completed);
        }
        progress.setUpdatedAt(LocalDateTime.now());

        progress = audioProgressRepository.save(progress);
        return ApiResponse.success(progress);
    }
}
