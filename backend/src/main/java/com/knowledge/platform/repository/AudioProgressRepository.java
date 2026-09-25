package com.knowledge.platform.repository;

import com.knowledge.platform.entity.AudioProgress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AudioProgressRepository extends MongoRepository<AudioProgress, String> {
    Optional<AudioProgress> findByUserIdAndEpisodeId(String userId, String episodeId);
    List<AudioProgress> findByUserIdAndCourseId(String userId, String courseId);
    Optional<AudioProgress> findFirstByUserIdAndCourseIdOrderByUpdatedAtDesc(String userId, String courseId);
}
