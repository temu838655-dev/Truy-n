    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "com.bokaliai.terminal.TERMUX_RESULT".equals(intent.getAction())) {
            // This callback consumes the same result that was persisted by the receiver.
            // Clear it here so a later onResume does not replay the transcript twice.
            getSharedPreferences("bo_kali_results", MODE_PRIVATE).edit()
                .remove("pending_output").remove("pending_screen_capture")
                .remove("pending_scrcpy_ready").apply();
            lastCommandOutput = intent.getStringExtra("termux_output");
            if (lastCommandOutput == null) lastCommandOutput = "Termux trả về kết quả trống.";
            if (intent.getBooleanExtra("bo_screen_capture", false)) showDualWorkspace();
            if (adbMirrorResumePending) adbMirrorResumePending = false;
            if (intent.getBooleanExtra("bo_scrcpy_ready", false)) {
                adbMirrorResumeRetries = 0;
                if (activeMirrorView != null) activeMirrorView.start(27183);
                else line("Máy chủ scrcpy đã sẵn sàng. Hãy mở lại MÀN HÌNH KÉP để xem thiết bị.", GREEN);
            } else {
                scheduleScrcpyResumeRetry(lastCommandOutput);
            }
            handleAdbDevicesOutput(lastCommandOutput);
            line("KẾT QUẢ TỪ KALI / TERMUX:", GREEN);
            for (String part : lastCommandOutput.split("\\n")) line(part, WHITE);
            line("Nhấn GIẢI THÍCH KẾT QUẢ KALI BẰNG AI để phân tích.", 0xff79c9ff);
        }
    }

    private void runKaliDirectly() {
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", "nh"});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");