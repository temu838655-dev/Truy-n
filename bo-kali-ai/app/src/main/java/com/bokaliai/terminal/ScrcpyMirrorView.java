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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * scrcpy v4.0 H.264 mirror surface for an ADB-forwarded localabstract socket.
 * The ADB transport and server lifecycle are managed by Termux's authorized adb client.
 */
public final class ScrcpyMirrorView extends FrameLayout implements SurfaceHolder.Callback {
    private static final int CODEC_H264 = 0x68323634;
    // scrcpy v4.0 frame header: bit 63 distinguishes session packets;
    // for media packets bit 62 is CONFIG, bit 61 is KEY_FRAME, low 61 bits are PTS.
    private static final long FLAG_SESSION = 1L << 63;
    private static final long FLAG_CONFIG = 1L << 62;
    private static final long FLAG_KEY_FRAME = 1L << 61;
    private static final long FLAG_PTS_MASK = (1L << 61) - 1;
    private static final int MAX_PACKET = 16 * 1024 * 1024;

    private final SurfaceView surfaceView;
    private final TextView statusView;
    private final ExecutorService connector = Executors.newSingleThreadExecutor();
    private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    // Invalidates pending socket connects and decoder callbacks from older sessions.
    private final AtomicInteger sessionGeneration = new AtomicInteger(0);
    private volatile Socket videoSocket;
    private volatile Socket controlSocket;
    private volatile OutputStream controlOutput;
    private volatile MediaCodec decoder;
    private volatile int videoWidth;
    private volatile int videoHeight;
    private volatile int localPort = -1;
    // Only auto-start when the user explicitly requested a start before the Surface existed.
    // A Surface recreated after lock/unlock must wait for MainActivity's ADB/server handshake.
    private volatile boolean pendingSurfaceStart = false;

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

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Close only the local video/control sockets before a fresh server handshake.
     * The ADB connection and foreground keep-alive service are intentionally untouched.
     */
    public void prepareForReconnect() {
        pendingSurfaceStart = false;
        stop();
        localPort = -1;
        setStatus("Đang chờ khôi phục luồng scrcpy sau khi mở khóa...");
    }

    public void start(int port) {
        if (port < 1 || port > 65535) {
            setStatus("Cổng truyền màn hình không hợp lệ");
            return;
        }
        stop();
        localPort = port;
        if (!surfaceView.getHolder().getSurface().isValid()) {
            pendingSurfaceStart = true;
            setStatus("Đang chờ bề mặt hiển thị...");
            return;
        }
        pendingSurfaceStart = false;
        running.set(true);
        final int generation = sessionGeneration.get();
        setStatus("Đang kết nối luồng video và điều khiển...");
        connector.execute(() -> connectStreams(port, generation));
    }

    private boolean isCurrentSession(int generation) {
        return running.get() && sessionGeneration.get() == generation;
    }

    private void connectStreams(int port, int generation) {
        Socket video = null;
        Socket control = null;
        try {
            video = new Socket();
            // Publish connecting sockets so stop()/a new generation can close them
            // immediately instead of waiting for the connect timeout.
            videoSocket = video;
            if (!isCurrentSession(generation)) {
                closeQuietly(video);
                if (videoSocket == video) videoSocket = null;
                return;
            }
            video.connect(new InetSocketAddress("127.0.0.1", port), 8000);
            video.setTcpNoDelay(true);
            if (!isCurrentSession(generation)) {
                closeQuietly(video);
                if (videoSocket == video) videoSocket = null;
                return;
            }

            control = new Socket();
            controlSocket = control;
            if (!isCurrentSession(generation)) {
                closeQuietly(video);
                closeQuietly(control);
                if (videoSocket == video) videoSocket = null;
                if (controlSocket == control) controlSocket = null;
                return;
            }
            control.connect(new InetSocketAddress("127.0.0.1", port), 8000);
            control.setTcpNoDelay(true);
            if (!isCurrentSession(generation)) {
                closeQuietly(video);
                closeQuietly(control);
                if (videoSocket == video) videoSocket = null;
                if (controlSocket == control) controlSocket = null;
                return;
            }
            controlOutput = control.getOutputStream();

            if (!isCurrentSession(generation)) {
                closeQuietly(video);
                closeQuietly(control);
                if (videoSocket == video) videoSocket = null;
                if (controlSocket == control) { controlSocket = null; controlOutput = null; }
                return;
            }
            setStatus("Đã kết nối · Đang truyền màn hình trực tiếp");
            Socket videoStream = video;
            Socket controlStream = control;
            decoderExecutor.execute(() -> decodeVideo(videoStream, controlStream, generation));
        } catch (Exception error) {
            closeQuietly(video);
            closeQuietly(control);
            if (videoSocket == video) videoSocket = null;
            if (controlSocket == control) controlSocket = null;
            if (controlSocket == null) controlOutput = null;
            if (isCurrentSession(generation)) setStatus("Không mở được luồng scrcpy: " + safeMessage(error));
        }
    }

    private void decodeVideo(Socket socket, Socket sessionControlSocket, int generation) {
        MediaCodec codec = null;
        try {
            DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 256 * 1024));
            int codecId = input.readInt();
            if (codecId != CODEC_H264) throw new IOException("Codec không hỗ trợ: 0x" + Integer.toHexString(codecId));

            // scrcpy v4.0 sends a 12-byte SESSION packet immediately after
            // the codec id, including on the first capture session.
            byte[] initialSession = new byte[12];
            input.readFully(initialSession);
            ByteBuffer initialHeader = ByteBuffer.wrap(initialSession).order(ByteOrder.BIG_ENDIAN);
            // scrcpy v4.0 session header is exactly 12 bytes:
            // 4-byte flags/padding, 4-byte width, then 4-byte height.
            int initialFlags = initialHeader.getInt();
            if ((initialFlags & 0x80000000) == 0) {
                throw new IOException("Thiếu gói khởi tạo phiên video scrcpy");
            }
            int width = initialHeader.getInt();
            int height = initialHeader.getInt();
            codec = createDecoder(width, height);
            decoder = codec;

            while (isCurrentSession(generation)) {
                byte[] header = new byte[12];
                input.readFully(header);
                ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
                long ptsAndFlags = headerBuffer.getLong();
                int packetSize = headerBuffer.getInt();

                if ((ptsAndFlags & FLAG_SESSION) != 0) {
                    int nextWidth = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
                    int nextHeight = ByteBuffer.wrap(header, 8, 4).order(ByteOrder.BIG_ENDIAN).getInt();
                    if (nextWidth <= 0 || nextHeight <= 0 || nextWidth > 8192 || nextHeight > 8192) {
                        throw new IOException("Kích thước phiên video không hợp lệ");
                    }
                    try { codec.stop(); } catch (Exception ignored) { }
                    codec.release();
                    codec = createDecoder(nextWidth, nextHeight);
                    decoder = codec;
                    continue;
                }

                if (packetSize < 1 || packetSize > MAX_PACKET) throw new IOException("Gói video quá lớn hoặc không hợp lệ");
                byte[] packet = new byte[packetSize];
                input.readFully(packet);
                if (codec == null) continue;

                int inputIndex = codec.dequeueInputBuffer(10_000);
                long inputDeadline = System.nanoTime() + 500_000_000L;
                while (inputIndex < 0 && isCurrentSession(generation) && System.nanoTime() < inputDeadline) {
                    // Drain decoded output while waiting for an input buffer. This applies
                    // backpressure instead of immediately discarding a video frame.
                    drainDecoder(codec);
                    inputIndex = codec.dequeueInputBuffer(10_000);
                }
                if (inputIndex >= 0) {
                    ByteBuffer target = codec.getInputBuffer(inputIndex);
                    if (target == null || target.capacity() < packet.length) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, 0);
                        throw new IOException("Gói video vượt dung lượng bộ đệm giải mã");
                    }
                    target.clear();
                    target.put(packet);
                    boolean config = (ptsAndFlags & FLAG_CONFIG) != 0;
                    long pts = ptsAndFlags & FLAG_PTS_MASK;
                    int codecFlags = config ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0;
                    if ((ptsAndFlags & FLAG_KEY_FRAME) != 0) codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    codec.queueInputBuffer(inputIndex, 0, packet.length, pts, codecFlags);
                } else if (isCurrentSession(generation)) {
                    throw new IOException("Bộ giải mã video không nhận buffer sau 500 ms");
                }
                drainDecoder(codec);
            }
        } catch (Exception error) {
            if (isCurrentSession(generation)) setStatus("Luồng màn hình đã dừng: " + safeMessage(error));
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                try { codec.release(); } catch (Exception ignored) { }
            }
            if (decoder == codec) decoder = null;
            closeQuietly(socket);
            closeQuietly(sessionControlSocket);
            if (videoSocket == socket) videoSocket = null;
            if (controlSocket == sessionControlSocket) {
                controlSocket = null;
                controlOutput = null;
            }
        }
    }

    private MediaCodec createDecoder(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new IOException("Kích thước video không hợp lệ: " + width + "x" + height);
        }
        videoWidth = width;
        videoHeight = height;
        post(this::updateVideoSurfaceLayout);
        Surface surface = surfaceView.getHolder().getSurface();
        if (!surface.isValid()) throw new IOException("Bề mặt hiển thị đã đóng");
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            if (Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            MediaCodec result = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            result.configure(format, surface, null, 0);
            result.start();
            setStatus("Đang xem trực tiếp · " + width + " × " + height + " · Chạm để điều khiển");
            return result;
        } catch (Exception error) {
            throw new IOException("Không khởi tạo được bộ giải mã H.264: " + safeMessage(error), error);
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

    /**
     * Keep the SurfaceView at the video's aspect ratio. Touch coordinates are then
     * relative to the actual displayed image instead of any letterbox area.
     */
    private void updateVideoSurfaceLayout() {
        int frameWidth = getWidth();
        int frameHeight = getHeight();
        int width = videoWidth;
        int height = videoHeight;
        if (frameWidth <= 0 || frameHeight <= 0 || width <= 0 || height <= 0) return;

        float scale = Math.min((float) frameWidth / width, (float) frameHeight / height);
        int fittedWidth = Math.max(1, Math.round(width * scale));
        int fittedHeight = Math.max(1, Math.round(height * scale));
        android.view.ViewGroup.LayoutParams current = surfaceView.getLayoutParams();
        if (!(current instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) current;
        if (params.width != fittedWidth || params.height != fittedHeight
            || params.gravity != (Gravity.CENTER)) {
            params.width = fittedWidth;
            params.height = fittedHeight;
            params.gravity = Gravity.CENTER;
            surfaceView.setLayoutParams(params);
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateVideoSurfaceLayout();
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
                // Preserve the pointer-index bits for ACTION_POINTER_DOWN/UP.
                writeTouch(output, event.getAction(), event, actionIndex, width, height);
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
        // scrcpy v4.0 INJECT_TOUCH_EVENT is exactly 32 bytes:
        // type, action, pointer id, position, pressure, action button, buttons.
        ByteBuffer message = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        message.put((byte) 2).put((byte) action).putLong(event.getPointerId(index));
        message.putInt(x).putInt(y).putShort((short) Math.min(width, 65535));
        message.putShort((short) Math.min(height, 65535)).putShort((short) pressure);
        message.putInt(0); // action_button
        message.putInt(0); // buttons
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

    public void sendText(String value) {
        OutputStream output = controlOutput;
        if (!running.get() || output == null || value == null || value.isEmpty()) return;
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 300) {
            setStatus("Văn bản quá dài (tối đa 300 byte UTF-8 mỗi lần gửi)");
            return;
        }
        // scrcpy INJECT_TEXT: type byte + 32-bit UTF-8 byte length + payload.
        ByteBuffer message = ByteBuffer.allocate(5 + utf8.length).order(ByteOrder.BIG_ENDIAN);
        message.put((byte) 1).putInt(utf8.length).put(utf8);
        try {
            synchronized (output) {
                output.write(message.array());
                output.flush();
            }
        } catch (IOException error) {
            setStatus("Không gửi được văn bản: " + safeMessage(error));
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
        sessionGeneration.incrementAndGet();
        running.set(false);
        // The decoder thread owns MediaCodec and releases it in decodeVideo()'s finally block.
        // Closing the video socket unblocks readFully(), allowing that thread to clean up safely.
        stopSockets();
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
        // A fresh Surface after screen unlock is not proof that the remote scrcpy
        // server/ADB forward is ready. Only honor a start explicitly queued while
        // waiting for the very first Surface; reconnects are coordinated by Activity.
        if (pendingSurfaceStart && localPort > 0 && !running.get()) start(localPort);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        pendingSurfaceStart = false;
        stop();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
