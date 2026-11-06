package com.bokaliai.terminal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Strict parser for the scrcpy v4.0 H.264 video stream headers. */
final class ScrcpyProtocol {
    static final int CODEC_H264 = 0x68323634;
    static final int HEADER_SIZE = 12;
    static final int MAX_DIMENSION = 8192;
    static final int MAX_PACKET_SIZE = 16 * 1024 * 1024;
    static final long FLAG_SESSION = 1L << 63;
    static final long FLAG_CONFIG = 1L << 62;
    static final long FLAG_KEY_FRAME = 1L << 61;
    static final long FLAG_PTS_MASK = (1L << 61) - 1;

    private ScrcpyProtocol() { }

    static VideoSize parseInitialSession(byte[] bytes) {
        if (bytes == null || bytes.length != HEADER_SIZE) {
            throw new IllegalArgumentException("Gói khởi tạo scrcpy phải có đúng 12 byte");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int flags = buffer.getInt();
        if ((flags & 0x80000000) == 0) {
            throw new IllegalArgumentException("Thiếu cờ gói khởi tạo phiên video scrcpy");
        }
        return new VideoSize(readDimension(buffer.getInt()), readDimension(buffer.getInt()));
    }

    static FrameHeader parseFrameHeader(byte[] bytes) {
        if (bytes == null || bytes.length != HEADER_SIZE) {
            throw new IllegalArgumentException("Header khung hình scrcpy phải có đúng 12 byte");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        long ptsAndFlags = buffer.getLong();
        int packetSize = buffer.getInt();
        if ((ptsAndFlags & FLAG_SESSION) != 0) {
            int width = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            int height = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            return FrameHeader.session(new VideoSize(readDimension(width), readDimension(height)));
        }
        if (packetSize < 1 || packetSize > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Kích thước gói video scrcpy không hợp lệ");
        }
        return FrameHeader.media(ptsAndFlags, packetSize);
    }

    private static int readDimension(int value) {
        if (value < 1 || value > MAX_DIMENSION) {
            throw new IllegalArgumentException("Kích thước video scrcpy không hợp lệ: " + value);
        }
        return value;
    }

    static final class VideoSize {
        final int width;
        final int height;
        VideoSize(int width, int height) { this.width = width; this.height = height; }
    }

    static final class FrameHeader {
        final boolean session;
        final VideoSize size;
        final long ptsAndFlags;
        final int packetSize;

        private FrameHeader(boolean session, VideoSize size, long ptsAndFlags, int packetSize) {
            this.session = session;
            this.size = size;
            this.ptsAndFlags = ptsAndFlags;
            this.packetSize = packetSize;
        }
        static FrameHeader session(VideoSize size) { return new FrameHeader(true, size, 0L, 0); }
        static FrameHeader media(long ptsAndFlags, int packetSize) {
            return new FrameHeader(false, null, ptsAndFlags, packetSize);
        }
    }
}
