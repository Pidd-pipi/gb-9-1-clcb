package com.knowledge.platform.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 轻量 MP3 时长解析（无第三方音频库依赖）：
 * 解析 ID3v2 标签大小与第一帧 MPEG 头，按 CBR/VBR(Xing/Info TOC) 估算时长。
 * 无法识别时返回 0，由上层决定是否允许试听。
 */
public final class Mp3DurationParser {

    private static final int[] BITRATE_V1_L1 = {
            0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448
    };
    private static final int[] BITRATE_V1_L2 = {
            0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384
    };
    private static final int[] BITRATE_V1_L3 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
    };
    private static final int[] BITRATE_V2_L1 = {
            0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256
    };
    private static final int[] BITRATE_V2_L23 = {
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160
    };
    private static final int[][] SAMPLE_RATES = {
            {44100, 48000, 32000}, // MPEG1
            {22050, 24000, 16000}, // MPEG2
            {11025, 12000, 8000}   // MPEG2.5
    };

    private Mp3DurationParser() {
    }

    public static int parseSeconds(InputStream inputStream, long fileSize) {
        try {
            byte[] data = readAll(inputStream);
            return parseSeconds(data, fileSize);
        } catch (Exception e) {
            return 0;
        }
    }

    public static int parseSeconds(byte[] data, long fileSize) {
        try {
            int offset = skipId3v2(data);

            while (offset + 4 <= data.length) {
                if ((data[offset] & 0xFF) == 0xFF && (data[offset + 1] & 0xE0) == 0xE0) {
                    return parseFrame(data, offset, fileSize);
                }
                offset++;
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private static int skipId3v2(byte[] data) {
        int offset = 0;
        if (data.length > 10
                && data[0] == 'I' && data[1] == 'D' && data[2] == '3'
                && data[3] != 0xFF && data[4] != 0xFF) {
            int size = ((data[6] & 0x7F) << 21)
                    | ((data[7] & 0x7F) << 14)
                    | ((data[8] & 0x7F) << 7)
                    | (data[9] & 0x7F);
            offset = 10 + size;
        }
        return offset;
    }

    private static int parseFrame(byte[] data, int offset, long fileSize) {
        int header2 = data[offset + 1] & 0xFF;
        int header3 = data[offset + 2] & 0xFF;
        int header4 = data[offset + 3] & 0xFF;

        int versionBits = (header2 >> 3) & 0x03;
        int layerBits = (header2 >> 1) & 0x03;
        int bitrateIdx = (header3 >> 4) & 0x0F;
        int sampleIdx = (header3 >> 2) & 0x03;
        int padding = (header3 >> 1) & 0x01;

        int mpegVersion;
        switch (versionBits) {
            case 3 -> mpegVersion = 1;
            case 2 -> mpegVersion = 2;
            case 0 -> mpegVersion = 3; // 2.5
            default -> {
                return 0;
            }
        }
        int layer;
        switch (layerBits) {
            case 3 -> layer = 1;
            case 2 -> layer = 2;
            case 1 -> layer = 3;
            default -> {
                return 0;
            }
        }
        if (bitrateIdx == 0 || bitrateIdx == 15 || sampleIdx == 3) {
            return 0;
        }

        int bitrate = lookupBitrate(mpegVersion, layer, bitrateIdx);
        if (bitrate <= 0) {
            return 0;
        }
        int versionRow = mpegVersion == 1 ? 0 : (mpegVersion == 2 ? 1 : 2);
        int sampleRate = SAMPLE_RATES[versionRow][sampleIdx];

        // 帧大小（字节）
        long frameLength;
        if (layer == 1) {
            frameLength = (long) ((12 * bitrate * 1000.0 / sampleRate) + padding) * 4;
        } else if (mpegVersion == 1 && layer == 3) {
            frameLength = (long) (144 * bitrate * 1000.0 / sampleRate) + padding;
        } else if (mpegVersion == 2 && layer == 3) {
            frameLength = (long) (72 * bitrate * 1000.0 / sampleRate) + padding;
        } else {
            frameLength = (long) (144 * bitrate * 1000.0 / sampleRate) + padding;
        }
        if (frameLength <= 0 || frameLength > 10_000_000) {
            return 0;
        }

        // VBR：Xing/Info 头中可能直接带帧数
        long vbrFrames = readXingFrameCount(data, offset, (int) frameLength, mpegVersion, layer);
        if (vbrFrames > 0) {
            double samplesPerFrame = layer == 1 ? 384.0 : 1152.0;
            if (mpegVersion != 1 && layer == 3) {
                samplesPerFrame = 576.0;
            }
            return (int) Math.round(vbrFrames * samplesPerFrame / sampleRate);
        }

        // CBR：音频字节总量 / 码率
        long audioBytes = fileSize > 0 ? fileSize - offset : data.length - offset;
        return (int) Math.round(audioBytes * 8.0 / (bitrate * 1000.0));
    }

    private static int lookupBitrate(int mpegVersion, int layer, int idx) {
        if (mpegVersion == 1) {
            return switch (layer) {
                case 1 -> BITRATE_V1_L1[idx];
                case 2 -> BITRATE_V1_L2[idx];
                default -> BITRATE_V1_L3[idx];
            };
        }
        return switch (layer) {
            case 1 -> BITRATE_V2_L1[idx];
            default -> BITRATE_V2_L23[idx];
        };
    }

    private static long readXingFrameCount(byte[] data, int frameOffset, int frameLength,
                                           int mpegVersion, int layer) {
        try {
            int xingOffset;
            if (mpegVersion == 1) {
                xingOffset = layer == 3 ? frameOffset + 36 : frameOffset + 36;
            } else {
                xingOffset = layer == 3 ? frameOffset + 21 : frameOffset + 21;
            }
            if (xingOffset + 8 > data.length) {
                return -1;
            }
            boolean xing = (data[xingOffset] == 'X' && data[xingOffset + 1] == 'i'
                    && data[xingOffset + 2] == 'n' && data[xingOffset + 3] == 'g')
                    || (data[xingOffset] == 'I' && data[xingOffset + 1] == 'n'
                    && data[xingOffset + 2] == 'f' && data[xingOffset + 3] == 'o');
            if (!xing) {
                return -1;
            }
            int flags = ((data[xingOffset + 4] & 0xFF) << 24)
                    | ((data[xingOffset + 5] & 0xFF) << 16)
                    | ((data[xingOffset + 6] & 0xFF) << 8)
                    | (data[xingOffset + 7] & 0xFF);
            int pos = xingOffset + 8;
            if ((flags & 0x01) != 0) {
                return ((long) (data[pos] & 0xFF) << 24)
                        | ((data[pos + 1] & 0xFF) << 16)
                        | ((data[pos + 2] & 0xFF) << 8)
                        | (data[pos + 3] & 0xFF);
            }
        } catch (Exception ignored) {
            return -1;
        }
        return -1;
    }

    private static byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        // 时长解析只需要文件头部，最多读取 512KB
        int total = 0;
        int limit = 512 * 1024;
        while ((read = inputStream.read(chunk)) != -1 && total < limit) {
            buffer.write(chunk, 0, read);
            total += read;
        }
        return buffer.toByteArray();
    }
}
