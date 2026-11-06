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
    // Header parsing and bounds checks are centralized and unit-tested.

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
            if (sessionGeneration.get() == generation && running.get()) {
                // A failed TCP connect must not leave the view marked healthy.
                // The Activity/network lifecycle can then safely request a new handshake.
                running.set(false);
                setStatus("Không mở được luồng scrcpy: " + safeMessage(error)
                    + " · Có thể thử khôi phục lại kết nối");
            }
        }
    }

    private void decodeVideo(Socket socket, Socket sessionControlSocket, int generation) {
        MediaCodec codec = null;
        try {
            DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 256 * 1024));
            int codecId = input.readInt();
            if (codecId != ScrcpyProtocol.CODEC_H264) throw new IOException("Codec không hỗ trợ: 0x" + Integer.toHexString(codecId));

            // scrcpy v4.0 sends a 12-byte SESSION packet immediately after
            // the codec id, including on the first capture session.
            byte[] initialSession = new byte[ScrcpyProtocol.HEADER_SIZE];
            input.readFully(initialSession);
            ScrcpyProtocol.VideoSize initialSize;
            try {
                initialSize = ScrcpyProtocol.parseInitialSession(initialSession);
            } catch (IllegalArgumentException invalidHeader) {
                throw new IOException(invalidHeader.getMessage(), invalidHeader);
            }
            codec = createDecoder(initialSize.width, initialSize.height);
            decoder = codec;

            while (isCurrentSession(generation)) {
                byte[] headerBytes = new byte[ScrcpyProtocol.HEADER_SIZE];
                input.readFully(headerBytes);
                ScrcpyProtocol.FrameHeader frameHeader;
                try {
                    frameHeader = ScrcpyProtocol.parseFrameHeader(headerBytes);
                } catch (IllegalArgumentException invalidHeader) {
                    throw new IOException(invalidHeader.getMessage(), invalidHeader);
                }

                if (frameHeader.session) {
                    try { codec.stop(); } catch (Exception ignored) { }
                    codec.release();
                    codec = createDecoder(frameHeader.size.width, frameHeader.size.height);
                    decoder = codec;
                    continue;
                }

                long ptsAndFlags = frameHeader.ptsAndFlags;
                int packetSize = frameHeader.packetSize;
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
                    boolean config = (ptsAndFlags & ScrcpyProtocol.FLAG_CONFIG) != 0;
                    long pts = ptsAndFlags & ScrcpyProtocol.FLAG_PTS_MASK;
                    int codecFlags = config ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0;
                    if ((ptsAndFlags & ScrcpyProtocol.FLAG_KEY_FRAME) != 0) codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
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
            // EOF, Wi-Fi loss, or a remote scrcpy exit must clear the local
            // running flag. Otherwise Activity thinks a dead socket is healthy
            // and refuses to run its reconnect handshake after the network returns.
            if (sessionGeneration.get() == generation) {
                running.set(false);
                post(() -> {
                    if (sessionGeneration.get() == generation) {
                        statusView.setText("Mất luồng scrcpy · ADB chưa bị ngắt · Mở lại màn hình để kết nối lại");
                    }
                });
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
            // A dead control socket means the mirrored session is no longer usable.
            // Stop both channels so the next Activity resume can rebuild them together.
            stop();
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
        byte[] message = ScrcpyControlMessage.touch(action, event.getPointerId(index),
            x, y, Math.min(width, 65535), Math.min(height, 65535), pressure, 0, 0);
        synchronized (output) {
            output.write(message);
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
        final byte[] message;
        try {
            message = ScrcpyControlMessage.text(value);
        } catch (IllegalArgumentException invalidText) {
            setStatus("Văn bản không hợp lệ hoặc vượt quá 300 byte UTF-8");
            return;
        }
        try {
            synchronized (output) {
                output.write(message);
                output.flush();
            }
        } catch (IOException error) {
            setStatus("Không gửi được văn bản: " + safeMessage(error));
        }
    }

    private void writeKey(OutputStream output, int action, int keyCode) throws IOException {
        byte[] message = ScrcpyControlMessage.key(action, keyCode);
        synchronized (output) {
            output.write(message);
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
