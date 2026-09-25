package com.knowledge.platform.service;

import com.knowledge.platform.entity.AudioCourse;
import com.knowledge.platform.entity.AudioEpisode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * 音频播放访问控制与字节流转发。
 * 规则：
 * - 已购用户可播放课程全部单集（课程下架后仍可播放）；
 * - 游客/未购用户仅可播放第一集的前 {@link AudioService#TRIAL_SECONDS} 秒，
 *   请求超出试听范围的字节区间一律拒绝；
 * - 未发布课程、非第一集的未购请求一律拒绝。
 */
@Service
public class AudioStreamService {

    private static final int BUFFER_SIZE = 16 * 1024;

    @Autowired
    private AudioService audioService;

    @Autowired
    private AudioEpisodeService audioEpisodeService;

    @Autowired
    private AudioStorageService audioStorageService;

    public void stream(AudioCourse course, AudioEpisode episode, boolean firstEpisode,
                       boolean purchased, boolean owner, boolean isAdmin,
                       String rangeHeader, HttpServletResponse response) {
        boolean fullAccess = purchased || owner || isAdmin;
        if (!fullAccess) {
            if (course.getStatus() != AudioCourse.Status.PUBLISHED) {
                throw new AudioAccessException(403, "课程已下架，购买后仍可学习");
            }
            if (!firstEpisode) {
                throw new AudioAccessException(403, "购买后可播放完整课程");
            }
        }

        boolean useObjectStorage = episode.getObjectKey() != null && !episode.getObjectKey().isBlank();
        try {
            long totalSize = useObjectStorage
                    ? audioStorageService.getObjectSize(episode.getObjectKey())
                    : resolveRemoteSize(episode.getFileUrl());
            if (totalSize <= 0) {
                throw new AudioAccessException(404, "音频文件不可用");
            }

            // 试听允许的文件截止位置（未知时长则无法界定试听范围，拒绝以防取走完整文件）
            Long trialAllowedEnd = null;
            if (!fullAccess) {
                if (episode.getDuration() == null || episode.getDuration() <= 0) {
                    throw new AudioAccessException(404, "试听资源暂不可用");
                }
                trialAllowedEnd = trialEndByte(totalSize, episode.getDuration(),
                        AudioService.TRIAL_SECONDS);
            }

            long start = 0;
            long end = totalSize - 1;
            boolean ranged = false;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String spec = rangeHeader.substring(6).split(",")[0].trim();
                int dash = spec.indexOf('-');
                if (dash < 0) {
                    throw new AudioAccessException(416, "无效的 Range 请求");
                }
                try {
                    String startPart = spec.substring(0, dash).trim();
                    String endPart = spec.substring(dash + 1).trim();
                    if (!startPart.isEmpty()) {
                        start = Long.parseLong(startPart);
                        if (!endPart.isEmpty()) {
                            end = Long.parseLong(endPart);
                        }
                    } else if (!endPart.isEmpty()) {
                        // bytes=-N：最后 N 字节，试听模式下从 0 开始截断防止绕过开头限制
                        long suffix = Long.parseLong(endPart);
                        if (trialAllowedEnd != null) {
                            start = 0;
                            end = Math.min(end, Math.min(suffix - 1, trialAllowedEnd));
                        } else {
                            start = Math.max(0, totalSize - suffix);
                        }
                    }
                    ranged = true;
                } catch (NumberFormatException e) {
                    throw new AudioAccessException(416, "无效的 Range 请求");
                }
            }

            if (trialAllowedEnd != null) {
                if (start > trialAllowedEnd) {
                    throw new AudioAccessException(403, "试听内容到此结束，购买后播放完整音频");
                }
                if (end > trialAllowedEnd) {
                    end = trialAllowedEnd;
                }
            }
            if (start >= totalSize || end >= totalSize || start > end) {
                throw new AudioAccessException(416, "请求范围超出文件大小");
            }
            long contentLength = end - start + 1;

            response.setContentType("audio/mpeg");
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Cache-Control", "no-store");
            if (ranged) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
            }
            response.setContentLengthLong(contentLength);

            try (InputStream input = openSource(episode, useObjectStorage, start, contentLength);
                 OutputStream output = response.getOutputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = contentLength;
                int read;
                while (remaining > 0 && (read = input.read(buffer, 0,
                        (int) Math.min(BUFFER_SIZE, remaining))) != -1) {
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
                output.flush();
            } catch (IOException clientGone) {
                // 浏览器切歌/拖拽时会中断连接，属于正常行为
                response.resetBuffer();
            }
        } catch (AudioAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new AudioAccessException(500, "音频读取失败：" + e.getMessage());
        }
    }

    /**
     * 试听截止字节：按时长等比换算（语音类音频码率基本恒定，误差可忽略）。
     */
    private long trialEndByte(long totalSize, int durationSeconds, int trialSeconds) {
        if (trialSeconds >= durationSeconds) {
            return totalSize - 1;
        }
        long end = Math.floorDiv(totalSize * (long) trialSeconds, (long) durationSeconds) - 1;
        return Math.max(0, end);
    }

    private InputStream openSource(AudioEpisode episode, boolean useObjectStorage,
                                   long offset, long length) throws Exception {
        if (useObjectStorage) {
            return audioStorageService.getObject(episode.getObjectKey(), offset, length);
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(episode.getFileUrl()).toURL().openConnection();
        connection.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        return connection.getInputStream();
    }

    private long resolveRemoteSize(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new AudioAccessException(404, "音频文件尚未上传");
        }
        if (fileUrl.startsWith("/api/")) {
            // 未走对象存储的占位地址，文件不可用
            throw new AudioAccessException(404, "音频文件尚未上传");
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(fileUrl).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            long length = connection.getContentLengthLong();
            connection.disconnect();
            if (length > 0) {
                return length;
            }
        } catch (Exception ignored) {
            // 落到 GET 探测
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(fileUrl).toURL().openConnection();
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            String contentRange = connection.getHeaderField("Content-Range");
            connection.disconnect();
            if (contentRange != null && contentRange.contains("/")) {
                return Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1));
            }
        } catch (Exception ignored) {
            // 无法探测大小时交由调用方报错
        }
        return -1;
    }
}
