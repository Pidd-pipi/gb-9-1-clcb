package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.repository.AudioCourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AudioService {
    @Autowired
    private AudioCourseRepository audioCourseRepository;

    public ApiResponse<Page<AudioCourse>> list(Pageable pageable) {
        Page<AudioCourse> courses = audioCourseRepository.findByStatus(AudioCourse.Status.PUBLISHED, pageable);
        return ApiResponse.success(courses);
    }

    public ApiResponse<AudioCourse> getById(String id) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(id);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("音频课程不存在");
        }
        return ApiResponse.success(courseOpt.get());
    }

    public Optional<AudioCourse> findById(String id) {
        return audioCourseRepository.findById(id);
    }

    public List<AudioCourse> getByCreator(String creatorId) {
        return audioCourseRepository.findByCreatorId(creatorId);
    }

    public ApiResponse<AudioCourse> updateStatus(String id, String userId, boolean admin, AudioCourse.Status target) {
        Optional<AudioCourse> courseOpt = audioCourseRepository.findById(id);
        if (courseOpt.isEmpty()) {
            return ApiResponse.error("音频课程不存在");
        }
        AudioCourse course = courseOpt.get();
        if (!admin && !course.getCreatorId().equals(userId)) {
            return ApiResponse.error("无权操作该课程");
        }
        course.setStatus(target);
        course.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.success(audioCourseRepository.save(course));
    }
}
