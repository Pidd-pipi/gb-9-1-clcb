package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.AudioEpisodeCreateRequest;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.AudioEpisode;
import com.knowledge.platform.repository.AudioCourseRepository;
import com.knowledge.platform.repository.AudioEpisodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AudioEpisodeService {

    @Autowired
    private AudioEpisodeRepository audioEpisodeRepository;

    @Autowired
    private AudioCourseRepository audioCourseRepository;

    @Autowired
    private AudioStorageService audioStorageService;

    public List<AudioEpisode> getEpisodes(String courseId) {
        return audioEpisodeRepository.findByCourseIdOrderBySequenceAsc(courseId);
    }

    public Optional<AudioEpisode> getEpisode(String courseId, String episodeId) {
        return audioEpisodeRepository.findByCourseIdAndId(courseId, episodeId);
    }

    public Optional<AudioEpisode> getFirstEpisode(String courseId) {
        return audioEpisodeRepository.findFirstByCourseIdOrderBySequenceAsc(courseId);
    }

    public ApiResponse<AudioEpisode> createEpisode(String userId, boolean isAdmin, String courseId,
                                                   AudioEpisodeCreateRequest request) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("音频课程不存在");
        }
        AudioCourse course = courseOpt.get();
        if (!userId.equals(course.getCreatorId()) && !isAdmin) {
            return ApiResponse.error("无权为该课程添加单集");
        }

        List<AudioEpisode> existing = audioEpisodeRepository.findByCourseIdOrderBySequenceAsc(courseId);
        Integer sequence = request.getSequence();
        if (sequence == null) {
            sequence = existing.stream()
                    .map(AudioEpisode::getSequence)
                    .filter(s -> s != null)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
        }

        LocalDateTime now = LocalDateTime.now();
        AudioEpisode episode = new AudioEpisode();
        episode.setCourseId(courseId);
        episode.setTitle(request.getTitle());
        episode.setDescription(request.getDescription());
        episode.setDuration(request.getDuration() != null ? request.getDuration() : 0);
        episode.setFileUrl(request.getFileUrl());
        episode.setSequence(sequence);
        episode.setCreatedAt(now);
        episode.setUpdatedAt(now);

        episode = audioEpisodeRepository.save(episode);
        refreshCourseStats(course);
        return ApiResponse.success("单集创建成功", episode);
    }

    public ApiResponse<AudioEpisode> uploadEpisode(String userId, boolean isAdmin, String courseId,
                                                   String title, String description,
                                                   MultipartFile file) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("音频课程不存在");
        }
        AudioCourse course = courseOpt.get();
        if (!userId.equals(course.getCreatorId()) && !isAdmin) {
            return ApiResponse.error("无权为该课程上传单集");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("请选择要上传的音频文件");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio.mp3";
        String suffix = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".mp3";
        String objectKey = "audio/" + courseId + "/" + java.util.UUID.randomUUID() + suffix;

        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            return ApiResponse.error("音频读取失败：" + e.getMessage());
        }
        try (InputStream inputStream = new java.io.ByteArrayInputStream(content)) {
            audioStorageService.uploadObject(objectKey, inputStream, content.length,
                    file.getContentType());
        } catch (Exception e) {
            return ApiResponse.error("音频上传失败：" + e.getMessage());
        }

        int duration;
        if (suffix.toLowerCase().endsWith(".mp3")) {
            duration = Mp3DurationParser.parseSeconds(
                    new java.io.ByteArrayInputStream(content), content.length);
        } else {
            duration = 0;
        }

        List<AudioEpisode> existing = audioEpisodeRepository.findByCourseIdOrderBySequenceAsc(courseId);
        LocalDateTime now = LocalDateTime.now();
        AudioEpisode episode = new AudioEpisode();
        episode.setCourseId(courseId);
        episode.setTitle(title != null && !title.isBlank() ? title : originalName);
        episode.setDescription(description);
        episode.setDuration(duration);
        episode.setObjectKey(objectKey);
        episode.setSequence(existing.stream()
                .map(AudioEpisode::getSequence)
                .filter(s -> s != null)
                .max(Integer::compareTo)
                .orElse(0) + 1);
        episode.setCreatedAt(now);
        episode.setUpdatedAt(now);

        episode = audioEpisodeRepository.save(episode);
        // 播放地址带真实单集 ID，同时保留一份便于直接识别
        episode.setFileUrl("/api/audio/" + courseId + "/episodes/" + episode.getId() + "/stream");
        episode = audioEpisodeRepository.save(episode);

        refreshCourseStats(course);
        return ApiResponse.success("音频上传成功", episode);
    }

    private void refreshCourseStats(AudioCourse course) {
        List<AudioEpisode> all = audioEpisodeRepository.findByCourseIdOrderBySequenceAsc(course.getId());
        course.setEpisodeCount(all.size());
        course.setTotalDuration(all.stream()
                .map(AudioEpisode::getDuration)
                .filter(d -> d != null)
                .mapToInt(Integer::intValue)
                .sum());
        course.setUpdatedAt(LocalDateTime.now());
        audioCourseRepository.save(course);
    }
}
