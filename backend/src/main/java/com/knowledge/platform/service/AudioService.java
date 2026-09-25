package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.AudioCourseCreateRequest;
import com.knowledge.platform.dto.AudioCourseDetailVO;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.AudioEpisode;
import com.knowledge.platform.entity.AudioProgress;
import com.knowledge.platform.entity.Creator;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.repository.AudioCourseRepository;
import com.knowledge.platform.repository.AudioEpisodeRepository;
import com.knowledge.platform.repository.AudioProgressRepository;
import com.knowledge.platform.repository.CreatorRepository;
import com.knowledge.platform.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class AudioService {

    public static final int TRIAL_SECONDS = 60;

    private static final EnumSet<Order.Status> VALID_ORDER_STATUS =
            EnumSet.of(Order.Status.PENDING, Order.Status.PAID);

    @Autowired
    private AudioCourseRepository audioCourseRepository;

    @Autowired
    private AudioEpisodeRepository audioEpisodeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CreatorRepository creatorRepository;

    @Autowired
    private AudioProgressRepository audioProgressRepository;

    public ApiResponse<Page<AudioCourse>> list(Pageable pageable) {
        Page<AudioCourse> courses = audioCourseRepository.findByStatus(AudioCourse.Status.PUBLISHED, pageable);
        return ApiResponse.success(courses);
    }

    public ApiResponse<AudioCourseDetailVO> getDetail(String id, String userId, boolean isAdmin) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(id);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("音频课程不存在");
        }
        AudioCourse course = courseOpt.get();

        boolean owner = userId != null && userId.equals(course.getCreatorId());
        if (course.getStatus() == AudioCourse.Status.DRAFT && !owner && !isAdmin) {
            return ApiResponse.error("音频课程不存在");
        }

        boolean purchased = userId != null && hasPurchased(userId, id);
        List<AudioEpisode> episodes = audioEpisodeRepository.findByCourseIdOrderBySequenceAsc(id);
        java.util.Map<String, AudioProgress> progressMap = userId == null
                ? java.util.Map.of()
                : audioProgressRepository.findByUserIdAndCourseId(userId, id).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                AudioProgress::getEpisodeId, p -> p, (a, b) -> a));

        AudioCourseDetailVO vo = new AudioCourseDetailVO();
        vo.setId(course.getId());
        vo.setCreatorId(course.getCreatorId());
        vo.setCreator(buildCreatorVO(course.getCreatorId()));
        vo.setTitle(course.getTitle());
        vo.setDescription(course.getDescription());
        vo.setCover(course.getCover());
        vo.setPrice(course.getPrice());
        vo.setEpisodeCount(course.getEpisodeCount());
        vo.setTotalDuration(course.getTotalDuration());
        vo.setIsSeries(course.getIsSeries());
        vo.setStatus(course.getStatus().name());
        vo.setPurchased(purchased);
        vo.setOwner(owner);
        vo.setTrialSeconds(TRIAL_SECONDS);
        vo.setEpisodes(episodes.stream().map(episode -> {
            var episodeVO = buildEpisodeVO(episode, purchased);
            AudioProgress progress = progressMap.get(episode.getId());
            if (progress != null) {
                episodeVO.setProgressPosition(progress.getPosition());
                episodeVO.setCompleted(progress.getCompleted());
            }
            return episodeVO;
        }).toList());
        vo.setCreatedAt(course.getCreatedAt());

        if (userId != null) {
            audioProgressRepository.findFirstByUserIdAndCourseIdOrderByUpdatedAtDesc(userId, id)
                    .map(AudioProgress::getEpisodeId)
                    .ifPresent(vo::setContinueEpisodeId);
        }
        return ApiResponse.success(vo);
    }

    public boolean hasPurchased(String userId, String courseId) {
        return orderRepository.existsByUserIdAndTypeAndItemIdAndStatusIn(
                userId, Order.OrderType.AUDIO_PURCHASE, courseId, VALID_ORDER_STATUS);
    }

    public Optional<Order> findValidOrder(String userId, String courseId) {
        return orderRepository.findFirstByUserIdAndTypeAndItemIdAndStatusInOrderByCreatedAtDesc(
                userId, Order.OrderType.AUDIO_PURCHASE, courseId, VALID_ORDER_STATUS);
    }

    public ApiResponse<AudioCourse> create(String userId, AudioCourseCreateRequest request) {
        Optional<Creator> creatorOpt = creatorRepository.findByUserId(userId);
        if (creatorOpt.isEmpty() || creatorOpt.get().getStatus() != Creator.Status.APPROVED) {
            return ApiResponse.error("只有审核通过的创作者可以发布音频课程");
        }

        LocalDateTime now = LocalDateTime.now();
        AudioCourse course = new AudioCourse();
        course.setCreatorId(userId);
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setCover(request.getCover());
        course.setPrice(request.getPrice() != null ? request.getPrice() : java.math.BigDecimal.ZERO);
        course.setIsSeries(Boolean.TRUE.equals(request.getIsSeries()));
        course.setEpisodeCount(0);
        course.setTotalDuration(0);
        course.setStatus(AudioCourse.Status.PUBLISHED);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);

        course = audioCourseRepository.save(course);
        return ApiResponse.success("音频课程创建成功", course);
    }

    public ApiResponse<AudioCourse> changeStatus(String userId, String courseId,
                                                 AudioCourse.Status targetStatus, boolean isAdmin) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("音频课程不存在");
        }
        AudioCourse course = courseOpt.get();
        if (!userId.equals(course.getCreatorId()) && !isAdmin) {
            return ApiResponse.error("无权操作该课程");
        }
        course.setStatus(targetStatus);
        course.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.success(audioCourseRepository.save(course));
    }

    private com.knowledge.platform.dto.CreatorVO buildCreatorVO(String creatorUserId) {
        return creatorRepository.findByUserId(creatorUserId)
                .map(creator -> new com.knowledge.platform.dto.CreatorVO(
                        creator.getId(), creator.getUsername(), creator.getAvatar()))
                .orElse(null);
    }

    public static com.knowledge.platform.dto.AudioEpisodeVO buildEpisodeVO(
            AudioEpisode episode, boolean purchased) {
        return new com.knowledge.platform.dto.AudioEpisodeVO(
                episode.getId(),
                episode.getCourseId(),
                episode.getTitle(),
                episode.getDescription(),
                episode.getDuration(),
                episode.getSequence(),
                purchased,
                purchased ? null : TRIAL_SECONDS,
                null,
                null);
    }
}
