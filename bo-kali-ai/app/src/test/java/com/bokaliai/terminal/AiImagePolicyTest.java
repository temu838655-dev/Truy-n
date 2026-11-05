package com.bokaliai.terminal;

import org.junit.Test;
import static org.junit.Assert.*;

/** Boundary tests for image inputs before they are shared with an AI provider. */
public class AiImagePolicyTest {
    @Test public void acceptsSupportedImageMimeTypes() {
        assertTrue(AiImagePolicy.isSupportedMime("image/jpeg"));
        assertTrue(AiImagePolicy.isSupportedMime("image/png"));
        assertTrue(AiImagePolicy.isSupportedMime("image/webp"));
        assertTrue(AiImagePolicy.isSupportedMime("image/gif"));
    }

    @Test public void rejectsUnsupportedAndMissingMimeTypes() {
        assertFalse(AiImagePolicy.isSupportedMime("image/heic"));
        assertFalse(AiImagePolicy.isSupportedMime("image/svg+xml"));
        assertFalse(AiImagePolicy.isSupportedMime(null));
        assertNotNull(AiImagePolicy.validationError("image/heic", 100));
    }

    @Test public void acceptsImageExactlyAtSixMegabytes() {
        assertNull(AiImagePolicy.validationError("image/jpeg", AiImagePolicy.MAX_IMAGE_BYTES));
    }

    @Test public void rejectsImageLargerThanSixMegabytes() {
        assertEquals("Ảnh vượt giới hạn 6 MB.",
            AiImagePolicy.validationError("image/png", (long) AiImagePolicy.MAX_IMAGE_BYTES + 1));
    }

    @Test public void rejectsEmptyImageData() {
        assertEquals("Tệp ảnh rỗng hoặc không có dữ liệu.",
            AiImagePolicy.validationError("image/webp", 0));
    }
}
