package com.knowledge.platform.controller;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.AudioAccessResponse;
import com.knowledge.platform.dto.ProgressSaveRequest;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.AudioEpisode;
import com.knowledge.platform.entity.AudioProgress;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.security.CurrentUserUtil;
import com.knowledge.platform.service.AudioEpisodeService;
import com.knowledge.platform.service.AudioOrderService;
import com.knowledge.platform.service.AudioProgressService;
import com.knowledge.platform.service.AudioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/audio")
public class AudioController {
    @Autowired
    private AudioService audioService;

    @Autowired
    private AudioEpisodeService audioEpisodeService;

    @Autowired
    private AudioOrderService audioOrderService;

    @Autowired
    private AudioProgressService audioProgressService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @GetMapping
    public ApiResponse<Page<AudioCourse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return audioService.list(pageable);
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<AudioCourse>>> myCourses() {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(audioService.getByCreator(userId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AudioCourse> getById(@PathVariable String id) {
        return audioService.getById(id);
    }

    @PostMapping("/{id}/purchase")
    public ResponseEntity<ApiResponse<Order>> purchase(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return unauthorized();
        }
        ApiResponse<Order> result = audioOrderService.purchase(userId, id);
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/purchase-status")
    public ApiResponse<Map<String, Boolean>> purchaseStatus(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        boolean purchased = userId != null && audioOrderService.isPurchased(userId, id);
        return ApiResponse.success(Map.of("purchased", purchased));
    }

    @PostMapping("/{id}/offline")
    public ResponseEntity<ApiResponse<AudioCourse>> offline(@PathVariable String id) {
        return updateStatus(id, AudioCourse.Status.OFFLINE);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<AudioCourse>> publish(@PathVariable String id) {
        return updateStatus(id, AudioCourse.Status.PUBLISHED);
    }

    private ResponseEntity<ApiResponse<AudioCourse>> updateStatus(String id, AudioCourse.Status target) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return unauthorized();
        }
        ApiResponse<AudioCourse> result = audioService.updateStatus(id, userId, currentUserUtil.hasRole("ADMIN"), target);
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{courseId}/episodes")
    public ApiResponse<List<AudioEpisode>> getEpisodes(@PathVariable String courseId) {
        List<AudioEpisode> episodes = audioEpisodeService.getEpisodes(courseId);
        return ApiResponse.success(episodes);
    }

    @GetMapping("/{courseId}/episodes/{episodeId}")
    public ApiResponse<AudioEpisode> getEpisode(
            @PathVariable String courseId,
            @PathVariable String episodeId) {
        Optional<AudioEpisode> episodeOpt = audioEpisodeService.getEpisode(courseId, episodeId);
        if (episodeOpt.isEmpty()) {
            return ApiResponse.error("音频集不存在");
        }
        return ApiResponse.success(episodeOpt.get());
    }

    @GetMapping("/{courseId}/episodes/{episodeId}/access")
    public ApiResponse<AudioAccessResponse> getAccess(
            @PathVariable String courseId,
            @PathVariable String episodeId) {
        Optional<AudioCourse> courseOpt = audioService.findById(courseId);
        Optional<AudioEpisode> episodeOpt = audioEpisodeService.getEpisode(courseId, episodeId);
        if (courseOpt.isEmpty() || episodeOpt.isEmpty()) {
            return ApiResponse.error("课程或单集不存在");
        }
        String userId = currentUserUtil.getCurrentUserId();
        return ApiResponse.success(audioOrderService.evaluateAccess(userId, courseOpt.get(), episodeOpt.get()));
    }

    @GetMapping("/{courseId}/episodes/{episodeId}/progress")
    public ResponseEntity<ApiResponse<AudioProgress>> getProgress(
            @PathVariable String courseId,
            @PathVariable String episodeId) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return unauthorized();
        }
        AudioProgress progress = audioProgressService.getProgress(userId, episodeId).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(progress));
    }

    @PostMapping("/{courseId}/episodes/{episodeId}/progress")
    public ResponseEntity<ApiResponse<AudioProgress>> saveProgress(
            @PathVariable String courseId,
            @PathVariable String episodeId,
            @Valid @RequestBody ProgressSaveRequest request) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return unauthorized();
        }
        if (audioEpisodeService.getEpisode(courseId, episodeId).isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("音频集不存在"));
        }
        AudioProgress progress = audioProgressService.saveProgress(userId, courseId, episodeId, request.getPosition());
        return ResponseEntity.ok(ApiResponse.success("进度已保存", progress));
    }

    @GetMapping("/{courseId}/progress")
    public ResponseEntity<ApiResponse<List<AudioProgress>>> getCourseProgress(@PathVariable String courseId) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(audioProgressService.getCourseProgress(userId, courseId)));
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return (ResponseEntity<ApiResponse<T>>) (ResponseEntity<?>)
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("请先登录"));
    }
}
