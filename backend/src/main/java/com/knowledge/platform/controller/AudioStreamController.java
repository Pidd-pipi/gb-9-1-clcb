package com.knowledge.platform.controller;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.AudioAccessResponse;
import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.AudioEpisode;
import com.knowledge.platform.security.CurrentUserUtil;
import com.knowledge.platform.service.AudioEpisodeService;
import com.knowledge.platform.service.AudioOrderService;
import com.knowledge.platform.service.AudioService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/audio")
public class AudioStreamController {

    @Autowired
    private AudioService audioService;

    @Autowired
    private AudioEpisodeService audioEpisodeService;

    @Autowired
    private AudioOrderService audioOrderService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 音频流接口。已购用户可播放全部单集（课程下架后仍可收听）；
     * 未购用户仅可流式访问已上架课程的第一集（试听时长由客户端按 access 接口限制）。
     */
    @GetMapping("/{courseId}/episodes/{episodeId}/stream")
    public ResponseEntity<?> stream(
            @PathVariable String courseId,
            @PathVariable String episodeId,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        Optional<AudioCourse> courseOpt = audioService.findById(courseId);
        Optional<AudioEpisode> episodeOpt = audioEpisodeService.getEpisode(courseId, episodeId);
        if (courseOpt.isEmpty() || episodeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("音频不存在"));
        }

        String userId = currentUserUtil.getCurrentUserId();
        AudioAccessResponse access = audioOrderService.evaluateAccess(userId, courseOpt.get(), episodeOpt.get());
        if (!access.isCanPlay()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(access.getReason() != null ? access.getReason() : "无权播放该音频"));
        }

        AudioEpisode episode = episodeOpt.get();
        if (episode.getObjectKey() != null && !episode.getObjectKey().isBlank()) {
            return streamFromMinio(episode.getObjectKey(), rangeHeader);
        }
        if (episode.getFileUrl() != null && episode.getFileUrl().startsWith("http")) {
            // 外部音频源直接重定向，由源站处理 Range 请求
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(episode.getFileUrl()))
                    .build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("音频文件不存在"));
    }

    private ResponseEntity<?> streamFromMinio(String objectKey, String rangeHeader) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectKey).build());
            long total = stat.size();

            long start = 0;
            long end = total - 1;
            boolean partial = false;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] parts = rangeHeader.substring(6).split("-");
                try {
                    if (!parts[0].isEmpty()) {
                        start = Long.parseLong(parts[0]);
                        if (parts.length > 1 && !parts[1].isEmpty()) {
                            end = Math.min(Long.parseLong(parts[1]), total - 1);
                        }
                    } else if (parts.length > 1 && !parts[1].isEmpty()) {
                        // 后缀范围：最后 N 个字节
                        long suffix = Long.parseLong(parts[1]);
                        start = Math.max(0, total - suffix);
                    }
                    partial = true;
                } catch (NumberFormatException ignored) {
                    start = 0;
                    end = total - 1;
                }
            }
            if (start > end || start >= total) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                        .build();
            }

            long length = end - start + 1;
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(objectKey)
                            .offset(start).length(length).build());
            // InputStreamResource 默认 contentLength() 会消费流，必须覆写
            InputStreamResource resource = new InputStreamResource(stream) {
                @Override
                public long contentLength() {
                    return length;
                }
            };

            MediaType mediaType = guessMediaType(objectKey, stat.contentType());
            ResponseEntity.BodyBuilder builder = ResponseEntity
                    .status(partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(mediaType)
                    .contentLength(length);
            if (partial) {
                builder.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
            }
            return builder.body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("音频文件不存在"));
        }
    }

    private MediaType guessMediaType(String objectKey, String storedContentType) {
        String key = objectKey.toLowerCase();
        if (key.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (key.endsWith(".m4a") || key.endsWith(".mp4")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        if (key.endsWith(".ogg")) {
            return MediaType.parseMediaType("audio/ogg");
        }
        if (key.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        if (storedContentType != null && !storedContentType.isBlank()
                && !storedContentType.equals("application/octet-stream")) {
            return MediaType.parseMediaType(storedContentType);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
