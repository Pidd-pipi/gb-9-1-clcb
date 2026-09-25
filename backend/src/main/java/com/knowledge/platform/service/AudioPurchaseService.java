package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.repository.AudioCourseRepository;
import com.knowledge.platform.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

@Service
public class AudioPurchaseService {

    private static final EnumSet<Order.Status> VALID_ORDER_STATUS =
            EnumSet.of(Order.Status.PENDING, Order.Status.PAID);

    @Autowired
    private AudioCourseRepository audioCourseRepository;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * 购买音频课程。同一用户重复提交、并发提交只会生成一笔有效订单：
     * 先查有效订单，再依赖 orders 上的 (userId, type, itemId, status in PENDING/PAID)
     * 唯一索引兜底，命中冲突时回查并返回已有订单。
     */
    public ApiResponse<Order> purchase(String userId, String courseId) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("音频课程不存在");
        }
        AudioCourse course = courseOpt.get();
        if (course.getStatus() == AudioCourse.Status.OFFLINE) {
            return ApiResponse.error("课程已下架，无法购买");
        }
        if (course.getStatus() == AudioCourse.Status.DRAFT) {
            return ApiResponse.error("课程尚未发布，无法购买");
        }

        Optional<Order> existing = orderRepository
                .findFirstByUserIdAndTypeAndItemIdAndStatusInOrderByCreatedAtDesc(
                        userId, Order.OrderType.AUDIO_PURCHASE, courseId, VALID_ORDER_STATUS);
        if (existing.isPresent()) {
            Order order = existing.get();
            return ApiResponse.success("您已购买该课程", order);
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setType(Order.OrderType.AUDIO_PURCHASE);
        order.setItemId(courseId);
        order.setItemTitle(course.getTitle());
        order.setAmount(course.getPrice());
        order.setStatus(Order.Status.PAID);
        order.setPaymentMethod(Order.PaymentMethod.ALIPAY);
        order.setPaymentId("MOCK-" + UUID.randomUUID().toString().replace("-", ""));
        order.setPaidAt(now);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        try {
            order = orderRepository.save(order);
        } catch (DuplicateKeyException e) {
            // 并发抢购：唯一索引兜底，回查已存在的有效订单
            Order concurrent = orderRepository
                    .findFirstByUserIdAndTypeAndItemIdAndStatusInOrderByCreatedAtDesc(
                            userId, Order.OrderType.AUDIO_PURCHASE, courseId, VALID_ORDER_STATUS)
                    .orElseThrow(() -> e);
            return ApiResponse.success("您已购买该课程", concurrent);
        }
        return ApiResponse.success("购买成功", order);
    }

    private String generateOrderNo() {
        return "AUD" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }
}
