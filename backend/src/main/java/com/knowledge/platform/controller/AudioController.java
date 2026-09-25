package com.knowledge.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.AudioCourseCreateRequest;
import com.knowledge.platform.dto.AudioCourseDetailVO;
import com.knowledge.platform.dto.AudioEpisodeCreateRequest;
import com.knowledge.platform.dto.AudioEpisodeVO;
import com.knowledge.platform.dto.AudioProgressRequest;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.AudioEpisode;
import com.knowledge.platform.entity.Order;
import com.knowledge.platform.security.CurrentUserUtil;
import com.knowledge.platform.service.AudioEpisodeService;
import com.knowledge.platform.service.AudioProgressService;
import com.knowledge.platform.service.AudioPurchaseService;
import com.knowledge.platform.service.AudioService;
import com.knowledge.platform.service.AudioStreamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/audio")
public class AudioController {

    @Autowired
    private AudioService audioService;

    @Autowired
    private AudioEpisodeService audioEpisodeService;

    @Autowired
    private AudioPurchaseService audioPurchaseService;

    @Autowired
    private AudioProgressService audioProgressService;

    @Autowired
    private AudioStreamService audioStreamService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Autowired
    private com.knowledge.platform.repository.AudioCourseRepository audioCourseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ApiResponse<Page<AudioCourse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return audioService.list(pageable);
    }

    @GetMapping("/{id}")
    public ApiResponse<AudioCourseDetailVO> getById(@PathVariable String id,
                                                    HttpServletRequest request) {
        String userId = currentUserUtil.getCurrentUserId(request);
        return audioService.getDetail(id, userId, currentUserUtil.isAdmin());
    }

    @PostMapping
    public ApiResponse<AudioCourse> create(@Valid @RequestBody AudioCourseCreateRequest request) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioService.create(userId, request);
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<AudioCourse> offline(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioService.changeStatus(userId, id, AudioCourse.Status.OFFLINE,
                currentUserUtil.isAdmin());
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<AudioCourse> publish(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioService.changeStatus(userId, id, AudioCourse.Status.PUBLISHED,
                currentUserUtil.isAdmin());
    }

    @PostMapping("/{id}/purchase")
    public ApiResponse<Order> purchase(@PathVariable String id) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioPurchaseService.purchase(userId, id);
    }

    @GetMapping("/{courseId}/episodes")
    public ApiResponse<List<AudioEpisodeVO>> getEpisodes(@PathVariable String courseId,
                                                         HttpServletRequest request) {
        String userId = currentUserUtil.getCurrentUserId(request);
        boolean purchased = userId != null && audioService.hasPurchased(userId, courseId);
        List<AudioEpisodeVO> episodes = audioEpisodeService.getEpisodes(courseId).stream()
                .map(episode -> AudioService.buildEpisodeVO(episode, purchased))
                .toList();
        return ApiResponse.success(episodes);
    }

    @GetMapping("/{courseId}/episodes/{episodeId}")
    public ApiResponse<AudioEpisodeVO> getEpisode(@PathVariable String courseId,
                                                  @PathVariable String episodeId,
                                                  HttpServletRequest request) {
        Optional<AudioEpisode> episodeOpt = audioEpisodeService.getEpisode(courseId, episodeId);
        if (episodeOpt.isEmpty()) {
            return ApiResponse.error("音频集不存在");
        }
        String userId = currentUserUtil.getCurrentUserId(request);
        boolean purchased = userId != null && audioService.hasPurchased(userId, courseId);
        AudioEpisodeVO vo = AudioService.buildEpisodeVO(episodeOpt.get(), purchased);
        if (userId != null) {
            ApiResponse<com.knowledge.platform.entity.AudioProgress> progressResp =
                    audioProgressService.getProgress(userId, courseId, episodeId);
            if (progressResp.getData() != null) {
                vo.setProgressPosition(progressResp.getData().getPosition());
                vo.setCompleted(progressResp.getData().getCompleted());
            }
        }
        return ApiResponse.success(vo);
    }

    @PostMapping("/{courseId}/episodes")
    public ApiResponse<AudioEpisode> createEpisode(@PathVariable String courseId,
                                                   @Valid @RequestBody AudioEpisodeCreateRequest request) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioEpisodeService.createEpisode(userId, currentUserUtil.isAdmin(), courseId, request);
    }

    @PostMapping(value = "/{courseId}/episodes/upload", consumes = "multipart/form-data")
    public ApiResponse<AudioEpisode> uploadEpisode(
            @PathVariable String courseId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioEpisodeService.uploadEpisode(userId, currentUserUtil.isAdmin(),
                courseId, title, description, file);
    }

    @GetMapping("/{courseId}/episodes/{episodeId}/stream")
    public void stream(@PathVariable String courseId,
                       @PathVariable String episodeId,
                       @RequestHeader(value = "Range", required = false) String rangeHeader,
                       HttpServletRequest request,
                       HttpServletResponse response) {
        try {
            streamWithCourse(courseId, episodeId, rangeHeader, request, response);
        } catch (com.knowledge.platform.service.AudioAccessException e) {
            writeError(response, e.getStatus(), e.getMessage());
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "音频加载失败");
        }
    }

    private void streamWithCourse(String courseId, String episodeId, String rangeHeader,
                                  HttpServletRequest request, HttpServletResponse response) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            throw new com.knowledge.platform.service.AudioAccessException(404, "音频课程不存在");
        }
        AudioCourse course = courseOpt.get();
        Optional<AudioEpisode> episodeOpt = audioEpisodeService.getEpisode(courseId, episodeId);
        if (episodeOpt.isEmpty()) {
            throw new com.knowledge.platform.service.AudioAccessException(404, "音频集不存在");
        }

        String userId = currentUserUtil.getCurrentUserId(request);
        boolean owner = userId != null && userId.equals(course.getCreatorId());
        boolean isAdmin = currentUserUtil.isAdmin();
        boolean purchased = userId != null && audioService.hasPurchased(userId, courseId);
        boolean firstEpisode = audioEpisodeService.getFirstEpisode(courseId)
                .map(first -> first.getId().equals(episodeId))
                .orElse(false);

        audioStreamService.stream(course, episodeOpt.get(), firstEpisode, purchased, owner,
                isAdmin, rangeHeader, response);
    }

    @GetMapping("/{courseId}/episodes/{episodeId}/progress")
    public ApiResponse<com.knowledge.platform.entity.AudioProgress> getProgress(
            @PathVariable String courseId,
            @PathVariable String episodeId) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioProgressService.getProgress(userId, courseId, episodeId);
    }

    @PostMapping("/{courseId}/episodes/{episodeId}/progress")
    public ApiResponse<com.knowledge.platform.entity.AudioProgress> saveProgress(
            @PathVariable String courseId,
            @PathVariable String episodeId,
            @RequestBody AudioProgressRequest request) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioProgressService.saveProgress(userId, courseId, episodeId,
                request.getPosition(), request.getDuration(), request.getCompleted());
    }

    @GetMapping("/{courseId}/continue")
    public ApiResponse<String> continueEpisode(@PathVariable String courseId) {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return audioProgressService.getContinueEpisodeId(userId, courseId);
    }

    private void writeError(HttpServletResponse response, int status, String message) {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message)));
        } catch (Exception ignored) {
            // 响应已无法写入时放弃
        }
    }
}
