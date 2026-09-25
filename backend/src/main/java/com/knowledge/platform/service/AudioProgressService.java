package com.knowledge.platform.service;

import com.knowledge.platform.entity.AudioProgress;
import com.knowledge.platform.repository.AudioProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AudioProgressService {

    @Autowired
    private AudioProgressRepository audioProgressRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Optional<AudioProgress> getProgress(String userId, String episodeId) {
        return audioProgressRepository.findByUserIdAndEpisodeId(userId, episodeId);
    }

    public List<AudioProgress> getCourseProgress(String userId, String courseId) {
        return audioProgressRepository.findByUserIdAndCourseId(userId, courseId);
    }

    /**
     * 按单集保存播放进度。使用 upsert 保证同一用户同一集只有一条记录，
     * 并发保存时不会产生重复文档。
     */
    public AudioProgress saveProgress(String userId, String courseId, String episodeId, int position) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("episodeId").is(episodeId));
        Update update = new Update()
                .set("courseId", courseId)
                .set("position", Math.max(0, position))
                .set("updatedAt", LocalDateTime.now());
        mongoTemplate.upsert(query, update, AudioProgress.class);
        return audioProgressRepository.findByUserIdAndEpisodeId(userId, episodeId).orElse(null);
    }
}
