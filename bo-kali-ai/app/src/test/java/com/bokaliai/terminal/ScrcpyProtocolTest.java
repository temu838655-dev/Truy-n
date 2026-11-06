package com.bokaliai.terminal;

import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class ScrcpyProtocolTest {
    private static byte[] session(int flags, int width, int height) {
        return ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putInt(flags).putInt(width).putInt(height).array();
    }

    @Test public void parsesInitialSessionDimensions() {
        ScrcpyProtocol.VideoSize size =
            ScrcpyProtocol.parseInitialSession(session(0x80000000, 1920, 1080));
        assertEquals(1920, size.width);
        assertEquals(1080, size.height);
    }

    @Test public void rejectsInitialPacketWithoutSessionFlag() {
        try {
            ScrcpyProtocol.parseInitialSession(session(0, 1920, 1080));
            fail("Expected invalid session flag");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsInvalidInitialDimensions() {
        try {
            ScrcpyProtocol.parseInitialSession(session(0x80000000, 0, 1080));
            fail("Expected invalid dimensions");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void parsesMediaFrameHeaderAndFlags() {
        long flags = ScrcpyProtocol.FLAG_CONFIG | ScrcpyProtocol.FLAG_KEY_FRAME | 123456L;
        byte[] bytes = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putLong(flags).putInt(4096).array();
        ScrcpyProtocol.FrameHeader header = ScrcpyProtocol.parseFrameHeader(bytes);
        assertFalse(header.session);
        assertEquals(4096, header.packetSize);
        assertEquals(flags, header.ptsAndFlags);
    }

    @Test public void parsesMidStreamResizeSession() {
        byte[] bytes = session(0x80000000, 1280, 720);
        ScrcpyProtocol.FrameHeader header = ScrcpyProtocol.parseFrameHeader(bytes);
        assertTrue(header.session);
        assertEquals(1280, header.size.width);
        assertEquals(720, header.size.height);
    }

    @Test public void rejectsOversizedMediaPacket() {
        byte[] bytes = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putLong(1L).putInt(ScrcpyProtocol.MAX_PACKET_SIZE + 1).array();
        try {
            ScrcpyProtocol.parseFrameHeader(bytes);
            fail("Expected oversized packet rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsWrongHeaderLength() {
        try {
            ScrcpyProtocol.parseFrameHeader(new byte[11]);
            fail("Expected header length rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void acceptsMaximumSupportedDimensions() {
        ScrcpyProtocol.VideoSize size =
            ScrcpyProtocol.parseInitialSession(session(0x80000000, 8192, 8192));
        assertEquals(8192, size.width);
        assertEquals(8192, size.height);
    }

    @Test public void rejectsDimensionsAboveMaximumInResizePacket() {
        try {
            ScrcpyProtocol.parseFrameHeader(session(0x80000000, 8193, 720));
            fail("Expected oversized resize dimensions rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsZeroLengthMediaPacket() {
        byte[] bytes = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putLong(1L).putInt(0).array();
        try {
            ScrcpyProtocol.parseFrameHeader(bytes);
            fail("Expected zero-length packet rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsNegativeMediaPacketLength() {
        byte[] bytes = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putLong(1L).putInt(-1).array();
        try {
            ScrcpyProtocol.parseFrameHeader(bytes);
            fail("Expected negative packet length rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void acceptsMaximumMediaPacketLength() {
        byte[] bytes = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putLong(1L).putInt(ScrcpyProtocol.MAX_PACKET_SIZE).array();
        ScrcpyProtocol.FrameHeader header = ScrcpyProtocol.parseFrameHeader(bytes);
        assertFalse(header.session);
        assertEquals(ScrcpyProtocol.MAX_PACKET_SIZE, header.packetSize);
    }

}
