package com.bokaliai.terminal;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * scrcpy v4.0 H.264 mirror surface for an ADB-forwarded localabstract socket.
 * The ADB transport and server lifecycle are managed by Termux's authorized adb client.
 */
public final class ScrcpyMirrorView extends FrameLayout implements SurfaceHolder.Callback {
    private static final int CODEC_H264 = 0x68323634;
    private static final long FLAG_CONFIG = 1L << 62;
    private static final long FLAG_PTS_MASK = (1L << 61) - 1;
    private static final int MAX_PACKET = 16 * 1024 * 1024;

    private final SurfaceView surfaceView;
    private final TextView statusView;
    private final ExecutorService connector = Executors.newSingleThreadExecutor();
    private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Socket videoSocket;
    private volatile Socket controlSocket;
    private volatile OutputStream controlOutput;
    private volatile MediaCodec decoder;
    private volatile int videoWidth;
    private volatile int videoHeight;
    private volatile int localPort = -1;

    public ScrcpyMirrorView(Context context) { this(context, null); }

    public ScrcpyMirrorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.rgb(3, 8, 14));
        surfaceView = new SurfaceView(context);
        surfaceView.getHolder().addCallback(this);
        LayoutParams videoParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER);
        addView(surfaceView, videoParams);

        statusView = new TextView(context);
        statusView.setText("Thiết bị đã kết nối · Nhấn BẮT ĐẦU ĐIỀU KHIỂN TRỰC TIẾP");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(11);
        statusView.setGravity(Gravity.CENTER);
        statusView.setBackgroundColor(0xaa101f2e);
        LayoutParams statusParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
        addView(statusView, statusParams);
        surfaceView.setOnTouchListener((v, event) -> forwardTouch(event));
    }

    public void setStatus(String value) {
        post(() -> statusView.setText(value == null ? "" : value));
    }

    public void start(int port) {
        if (port < 1 || port > 65535) {
            setStatus("Cổng truyền màn hình không hợp lệ");
            return;
        }
        stop();
        localPort = port;
        if (!surfaceView.getHolder().getSurface().isValid()) {
            setStatus("Đang chờ bề mặt hiển thị...");
            return;
        }
        running.set(true);
        setStatus("Đang kết nối luồng video và điều khiển...");
        connector.execute(() -> connectStreams(port));
    }

    private void connectStreams(int port) {
        try {
            Socket video = new Socket();
            video.connect(new InetSocketAddress("127.0.0.1", port), 8000);
            video.setTcpNoDelay(true);
            videoSocket = video;

            Socket control = new Socket();
            control.connect(new InetSocketAddress("127.0.0.1", port), 8000);
            control.setTcpNoDelay(true);
            controlSocket = control;
            controlOutput = control.getOutputStream();

            if (!running.get()) return;
            setStatus("Đã kết nối · Đang truyền màn hình trực tiếp");
            decoderExecutor.execute(() -> decodeVideo(video));
        } catch (Exception error) {
            setStatus("Không mở được luồng scrcpy: " + safeMessage(error));
            stopSockets();
        }
    }

    private void decodeVideo(Socket socket) {
        MediaCodec codec = null;
        try {
            DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 256 * 1024));
            int codecId = input.readInt();
            if (codecId != CODEC_H264) throw new IOException("Codec không hỗ trợ: 0x" + Integer.toHexString(codecId));

            while (running.get()) {
                byte[] header = new byte[12];
                input.readFully(header);
                ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
                int first = headerBuffer.getInt();
                if ((first & 0x80000000) != 0) {
                    int width = headerBuffer.getInt();
                    int height = headerBuffer.getInt();
                    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
                        throw new IOException("Kích thước màn hình không hợp lệ");
                    }
                    if (codec != null) {
                        try { codec.stop(); } catch (Exception ignored) { }
                        codec.release();
                    }
                    videoWidth = width;
                    videoHeight = height;
                    requestLayout();
                    Surface surface = surfaceView.getHolder().getSurface();
                    if (!surface.isValid()) throw new IOException("Bề mặt hiển thị đã đóng");
                    MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
                    if (Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
                    codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    codec.configure(format, surface, null, 0);
                    codec.start();
                    decoder = codec;
                    setStatus("Đang xem trực tiếp · " + width + " × " + height + " · Chạm để điều khiển");
                    continue;
                }

                long ptsAndFlags = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong();
                int packetSize = ByteBuffer.wrap(header, 8, 4).order(ByteOrder.BIG_ENDIAN).getInt();
                if (packetSize < 1 || packetSize > MAX_PACKET) throw new IOException("Gói video quá lớn hoặc không hợp lệ");
                byte[] packet = new byte[packetSize];
                input.readFully(packet);
                if (codec == null) continue;

                int inputIndex = codec.dequeueInputBuffer(10_000);
                if (inputIndex >= 0) {
                    ByteBuffer target = codec.getInputBuffer(inputIndex);
                    if (target != null && target.capacity() >= packet.length) {
                        target.clear();
                        target.put(packet);
                        boolean config = (ptsAndFlags & FLAG_CONFIG) != 0;
                        long pts = config ? 0L : (ptsAndFlags & FLAG_PTS_MASK);
                        codec.queueInputBuffer(inputIndex, 0, packet.length, pts,
                            config ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0);
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, 0);
                    }
                }
                drainDecoder(codec);
            }
        } catch (Exception error) {
            if (running.get()) setStatus("Luồng màn hình đã dừng: " + safeMessage(error));
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                try { codec.release(); } catch (Exception ignored) { }
            }
            if (decoder == codec) decoder = null;
            stopSockets();
        }
    }

    private void drainDecoder(MediaCodec codec) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int index = codec.dequeueOutputBuffer(info, 0);
            if (index < 0) return;
            codec.releaseOutputBuffer(index, true);
        }
    }

    private boolean forwardTouch(MotionEvent event) {
        OutputStream output = controlOutput;
        int width = videoWidth;
        int height = videoHeight;
        if (!running.get() || output == null || width <= 0 || height <= 0) return false;

        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) action = MotionEvent.ACTION_UP;

        try {
            if (action == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    writeTouch(output, MotionEvent.ACTION_MOVE, event, i, width, height);
                }
            } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
                int scrcpyAction = action == MotionEvent.ACTION_POINTER_DOWN ? 5
                    : action == MotionEvent.ACTION_POINTER_UP ? 6
                    : action == MotionEvent.ACTION_DOWN ? 0 : 1;
                writeTouch(output, scrcpyAction, event, actionIndex, width, height);
            }
            return true;
        } catch (IOException error) {
            setStatus("Mất kênh điều khiển: " + safeMessage(error));
            return false;
        }
    }

    private void writeTouch(OutputStream output, int action, MotionEvent event, int index, int width, int height)
        throws IOException {
        float xRatio = Math.max(0f, Math.min(1f, event.getX(index) / Math.max(1f, surfaceView.getWidth())));
        float yRatio = Math.max(0f, Math.min(1f, event.getY(index) / Math.max(1f, surfaceView.getHeight())));
        int x = Math.min(width - 1, Math.max(0, Math.round(xRatio * width)));
        int y = Math.min(height - 1, Math.max(0, Math.round(yRatio * height)));
        int pressure = Math.round(Math.max(0f, Math.min(1f, event.getPressure(index))) * 65535f);
        ByteBuffer message = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        message.put((byte) 2).put((byte) action).putLong(event.getPointerId(index));
        message.putInt(x).putInt(y).putShort((short) Math.min(width, 65535));
        message.putShort((short) Math.min(height, 65535)).putShort((short) pressure);
        message.putInt(0).putInt(0);
        synchronized (output) {
            output.write(message.array());
            output.flush();
        }
    }

    public void sendKey(int androidKeyCode) {
        OutputStream output = controlOutput;
        if (!running.get() || output == null) return;
        try {
            writeKey(output, 0, androidKeyCode);
            writeKey(output, 1, androidKeyCode);
        } catch (IOException error) {
            setStatus("Không gửi được phím: " + safeMessage(error));
        }
    }

    private void writeKey(OutputStream output, int action, int keyCode) throws IOException {
        ByteBuffer message = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN);
        message.put((byte) 0).put((byte) action).putInt(keyCode).putInt(0).putInt(0);
        synchronized (output) {
            output.write(message.array());
            output.flush();
        }
    }

    public void stop() {
        running.set(false);
        stopSockets();
        MediaCodec active = decoder;
        decoder = null;
        if (active != null) {
            try { active.stop(); } catch (Exception ignored) { }
            try { active.release(); } catch (Exception ignored) { }
        }
        setStatus("Đã dừng truyền màn hình");
    }

    private void stopSockets() {
        closeQuietly(videoSocket);
        closeQuietly(controlSocket);
        videoSocket = null;
        controlSocket = null;
        controlOutput = null;
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (localPort > 0 && !running.get()) start(localPort);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) { stop(); }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
