package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.AudioAccessResponse;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.AudioEpisode;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.repository.AudioCourseRepository;
import com.knowledge.platform.repository.AudioEpisodeRepository;
import com.knowledge.platform.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AudioOrderService {

    /** 未购买用户可试听第一集的时长（秒） */
    public static final int TRIAL_SECONDS = 60;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AudioCourseRepository audioCourseRepository;

    @Autowired
    private AudioEpisodeRepository audioEpisodeRepository;

    public boolean isPurchased(String userId, String courseId) {
        if (userId == null) {
            return false;
        }
        return orderRepository.findByUserIdAndTypeAndItemIdAndStatus(
                userId, Order.OrderType.AUDIO_PURCHASE, courseId, Order.Status.PAID
        ).isPresent();
    }

    /**
     * 评估某用户对某一集的播放权限：
     * 已购（即使课程已下架）可播放全部单集；
     * 未购仅可试听已上架课程的第一集。
     */
    public AudioAccessResponse evaluateAccess(String userId, AudioCourse course, AudioEpisode episode) {
        if (isPurchased(userId, course.getId())) {
            return AudioAccessResponse.full();
        }
        if (course.getStatus() == AudioCourse.Status.OFFLINE) {
            return AudioAccessResponse.denied("课程已下架");
        }
        if (course.getStatus() != AudioCourse.Status.PUBLISHED) {
            return AudioAccessResponse.denied("课程未发布");
        }
        Optional<AudioEpisode> firstEpisode =
                audioEpisodeRepository.findFirstByCourseIdOrderBySequenceAsc(course.getId());
        if (firstEpisode.isPresent() && firstEpisode.get().getId().equals(episode.getId())) {
            return AudioAccessResponse.trial(TRIAL_SECONDS);
        }
        return AudioAccessResponse.denied("购买后可收听完整单集");
    }

    /**
     * 购买音频课程（模拟支付，直接生成已支付订单）。
     * 幂等：同一账号重复提交返回已存在的订单；
     * 并发安全：依赖 orders 集合 (userId, type, itemId) 已支付状态的局部唯一索引，
     * 多人同时抢购时同一账号只会留下一笔有效购买。
     */
    public ApiResponse<Order> purchase(String userId, String courseId) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("课程不存在");
        }
        AudioCourse course = courseOpt.get();
        if (course.getStatus() == AudioCourse.Status.OFFLINE) {
            return ApiResponse.error("课程已下架，无法购买");
        }
        if (course.getStatus() != AudioCourse.Status.PUBLISHED) {
            return ApiResponse.error("课程未发布，无法购买");
        }

        Optional<Order> existing = findPaidOrder(userId, courseId);
        if (existing.isPresent()) {
            return ApiResponse.success("您已购买过该课程", existing.get());
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            Order order = new Order();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setType(Order.OrderType.AUDIO_PURCHASE);
            order.setItemId(courseId);
            order.setItemTitle(course.getTitle());
            order.setAmount(course.getPrice());
            order.setStatus(Order.Status.PAID);
            order.setPaymentMethod(Order.PaymentMethod.ALIPAY);
            order.setPaymentId("MOCK-" + UUID.randomUUID());
            LocalDateTime now = LocalDateTime.now();
            order.setPaidAt(now);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            try {
                return ApiResponse.success("购买成功", orderRepository.save(order));
            } catch (DuplicateKeyException e) {
                Optional<Order> existed = findPaidOrder(userId, courseId);
                if (existed.isPresent()) {
                    // 并发重复提交：唯一索引拦截，返回已存在的那一笔有效订单
                    return ApiResponse.success("您已购买过该课程", existed.get());
                }
                // 订单号撞单（极少发生），换单号重试
            }
        }
        return ApiResponse.error("系统繁忙，请稍后重试");
    }

    private Optional<Order> findPaidOrder(String userId, String courseId) {
        return orderRepository.findByUserIdAndTypeAndItemIdAndStatus(
                userId, Order.OrderType.AUDIO_PURCHASE, courseId, Order.Status.PAID);
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return "A" + timestamp + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
