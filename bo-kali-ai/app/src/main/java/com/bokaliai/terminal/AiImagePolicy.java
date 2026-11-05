package com.bokaliai.terminal;

/** Shared, unit-testable validation for images sent to supported AI providers. */
public final class AiImagePolicy {
    public static final int MAX_IMAGE_BYTES = 6 * 1024 * 1024;

    private AiImagePolicy() { }

    public static boolean isSupportedMime(String mime) {
        return "image/jpeg".equals(mime) || "image/png".equals(mime)
            || "image/webp".equals(mime) || "image/gif".equals(mime);
    }

    public static String unsupportedMimeMessage() {
        return "Chỉ hỗ trợ ảnh JPEG, PNG, WebP hoặc GIF để tương thích API của các AI.";
    }

    /** Returns null when the MIME type and encoded file size are acceptable. */
    public static String validationError(String mime, long byteCount) {
        if (!isSupportedMime(mime)) return unsupportedMimeMessage();
        if (byteCount <= 0) return "Tệp ảnh rỗng hoặc không có dữ liệu.";
        if (byteCount > MAX_IMAGE_BYTES) return "Ảnh vượt giới hạn 6 MB.";
        return null;
    }
}
