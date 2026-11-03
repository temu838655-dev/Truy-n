package com.bokaliai.terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Bundle result = intent.getBundleExtra("result");
        if (result == null) return;
        String stdout = result.getString("stdout", "");
        String stderr = result.getString("stderr", "");
        int exitCode = result.getInt("exitCode", -1);
        int err = result.getInt("err", -1);
        String errmsg = result.getString("errmsg", "");
        StringBuilder output = new StringBuilder();
        output.append("Mã thoát: ").append(exitCode).append("\n");
        if (!stdout.isEmpty()) output.append("STDOUT:\n").append(stdout).append("\n");
        if (!stderr.isEmpty()) output.append("STDERR:\n").append(stderr).append("\n");
        if (err != -1 || !errmsg.isEmpty()) output.append("Lỗi Termux: ").append(errmsg).append(" (").append(err).append(")\n");
        Intent show = new Intent(context, MainActivity.class);
        show.setAction("com.bokaliai.terminal.TERMUX_RESULT");
        show.putExtra("termux_output", output.toString());
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(show);
    }
}
