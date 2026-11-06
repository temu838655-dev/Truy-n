package com.bokaliai.terminal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Encodes scrcpy v4.0 control-channel messages with bounded, testable payloads. */
final class ScrcpyControlMessage {
    static final int MAX_TEXT_BYTES = 300;
    private ScrcpyControlMessage() { }

    static int mapCoordinate(float coordinate, int displayedSize, int videoSize) {
        if (displayedSize < 1 || videoSize < 1 || Float.isNaN(coordinate)
                || Float.isInfinite(coordinate)) {
            throw new IllegalArgumentException("Tọa độ hiển thị scrcpy không hợp lệ");
        }
        float ratio = Math.max(0f, Math.min(1f, coordinate / displayedSize));
        return Math.min(videoSize - 1, Math.max(0, Math.round(ratio * videoSize)));
    }

    static byte[] touch(int action, long pointerId, int x, int y, int width, int height,
                        int pressure, int actionButton, int buttons) {
        if (width < 1 || width > 65535 || height < 1 || height > 65535
                || x < 0 || x >= width || y < 0 || y >= height
                || pressure < 0 || pressure > 65535) {
            throw new IllegalArgumentException("Thông số chạm scrcpy không hợp lệ");
        }
        return ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
            .put((byte) 2).put((byte) action).putLong(pointerId)
            .putInt(x).putInt(y).putShort((short) width).putShort((short) height)
            .putShort((short) pressure).putInt(actionButton).putInt(buttons).array();
    }

    static byte[] key(int action, int keyCode) {
        return ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN)
            .put((byte) 0).put((byte) action).putInt(keyCode).putInt(0).putInt(0).array();
    }

    static byte[] text(String value) {
        if (value == null) throw new IllegalArgumentException("Văn bản không được để trống");
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 0 || utf8.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Văn bản phải từ 1 đến 300 byte UTF-8");
        }
        return ByteBuffer.allocate(5 + utf8.length).order(ByteOrder.BIG_ENDIAN)
            .put((byte) 1).putInt(utf8.length).put(utf8).array();
    }
}
