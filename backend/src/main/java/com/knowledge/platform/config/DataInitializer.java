package com.knowledge.platform.config;

import com.knowledge.platform.service.AudioStorageService;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.domain.Sort.Direction.ASC;

/**
 * 应用启动后补齐集合级索引：
 * - orders 上针对 (userId, type, itemId) 且状态属于 PENDING/PAID 的部分唯一索引，
 *   从数据库层面保证同一用户对同一商品只存在一笔有效购买（并发抢购兜底）；
 * - audio_progress 上 (userId, episodeId) 唯一索引，保证单集进度只有一份。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AudioStorageService audioStorageService;

    @Override
    public void run(ApplicationArguments args) {
        ensureOrderIdempotencyIndex();
        ensureAudioProgressIndex();
        audioStorageService.init();
    }

    private void ensureOrderIdempotencyIndex() {
        Document indexKeys = new Document()
                .append("userId", 1)
                .append("type", 1)
                .append("itemId", 1);
        IndexOptions options = new IndexOptions()
                .name("uniq_valid_purchase_idx")
                .unique(true)
                .partialFilterExpression(new Document(
                        "status", new Document("$in", List.of("PENDING", "PAID"))));
        mongoTemplate.getCollection("orders").createIndex(indexKeys, options);
    }

    private void ensureAudioProgressIndex() {
        boolean exists = mongoTemplate.getCollection("audio_progress")
                .listIndexes()
                .into(new ArrayList<>())
                .stream()
                .map(idx -> (Document) idx)
                .anyMatch(idx -> "user_episode_idx".equals(idx.getString("name")));
        if (!exists) {
            mongoTemplate.indexOps("audio_progress").ensureIndex(
                    new Index()
                            .on("userId", ASC)
                            .on("episodeId", ASC)
                            .named("user_episode_idx")
                            .unique());
        }
    }
}
