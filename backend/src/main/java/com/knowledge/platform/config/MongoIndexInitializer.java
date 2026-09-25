package com.knowledge.platform.config;

import com.knowledge.platform.entity.AudioProgress;
import com.knowledge.platform.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * 启动时确保关键唯一索引存在（init.js 只在全新数据库上执行，已有环境需要靠这里兜底）。
 */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 同一用户对同一商品只留一笔已支付订单：重复提交、并发抢购都靠它兜底
        mongoTemplate.indexOps(Order.class).ensureIndex(
                new Index()
                        .on("userId", Sort.Direction.ASC)
                        .on("type", Sort.Direction.ASC)
                        .on("itemId", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(Criteria.where("status").is("PAID")))
        );
        // 播放进度按 用户+单集 唯一
        mongoTemplate.indexOps(AudioProgress.class).ensureIndex(
                new Index()
                        .on("userId", Sort.Direction.ASC)
                        .on("episodeId", Sort.Direction.ASC)
                        .unique()
        );
        log.info("MongoDB 唯一索引检查完成");
    }
}
