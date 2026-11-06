package com.bokaliai.terminal;

import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class ScrcpyControlMessageTest {
    @Test public void mapsTouchCoordinatesToVideoResolution() {
        assertEquals(0, ScrcpyControlMessage.mapCoordinate(0f, 500, 1920));
        assertEquals(960, ScrcpyControlMessage.mapCoordinate(250f, 500, 1920));
        assertEquals(1919, ScrcpyControlMessage.mapCoordinate(500f, 500, 1920));
    }

    @Test public void clampsTouchCoordinatesOutsideSurface() {
        assertEquals(0, ScrcpyControlMessage.mapCoordinate(-20f, 500, 1920));
        assertEquals(1919, ScrcpyControlMessage.mapCoordinate(700f, 500, 1920));
    }

    @Test public void rejectsInvalidCoordinateMapping() {
        try {
            ScrcpyControlMessage.mapCoordinate(Float.NaN, 500, 1920);
            fail("Expected invalid coordinate rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void encodesTouchAs32ByteBigEndianMessage() {
        byte[] bytes = ScrcpyControlMessage.touch(0, 7L, 100, 200, 1080, 1920, 65535, 0, 1);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        assertEquals(32, bytes.length);
        assertEquals(2, b.get() & 0xff);
        assertEquals(0, b.get() & 0xff);
        assertEquals(7L, b.getLong());
        assertEquals(100, b.getInt());
        assertEquals(200, b.getInt());
        assertEquals(1080, b.getShort() & 0xffff);
        assertEquals(1920, b.getShort() & 0xffff);
        assertEquals(65535, b.getShort() & 0xffff);
        assertEquals(0, b.getInt());
        assertEquals(1, b.getInt());
    }

    @Test public void rejectsTouchOutsideDisplayBounds() {
        try {
            ScrcpyControlMessage.touch(0, 0, 1080, 0, 1080, 1920, 1, 0, 0);
            fail("Expected out-of-bounds touch rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void encodesKeyAs14ByteMessage() {
        byte[] bytes = ScrcpyControlMessage.key(1, 4);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        assertEquals(14, bytes.length);
        assertEquals(0, b.get() & 0xff);
        assertEquals(1, b.get() & 0xff);
        assertEquals(4, b.getInt());
    }

    @Test public void encodesTextLengthInUtf8Bytes() {
        byte[] bytes = ScrcpyControlMessage.text("Xin chào");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        assertEquals(1, b.get() & 0xff);
        int length = b.getInt();
        byte[] payload = new byte[length];
        b.get(payload);
        assertArrayEquals("Xin chào".getBytes(StandardCharsets.UTF_8), payload);
    }

    @Test public void rejectsTextOver300Utf8Bytes() {
        try {
            ScrcpyControlMessage.text(repeat('a', 301));
            fail("Expected oversized text rejection");
        } catch (IllegalArgumentException expected) { }
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }
}
