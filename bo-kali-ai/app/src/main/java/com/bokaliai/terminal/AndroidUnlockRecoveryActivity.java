package com.bokaliai.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Original, owner-authorized Android recovery companion.
 * This screen provides diagnostics and OEM/account recovery routes only.
 * It does not bypass screen locks, Google FRP, or device anti-theft controls.
 */
public final class AndroidUnlockRecoveryActivity extends Activity {
    private static final String[] BRANDS = {
        "Chọn hãng thiết bị", "Samsung", "Xiaomi / Redmi / POCO", "OPPO", "vivo",
        "Huawei", "Google Pixel", "Motorola", "OnePlus", "realme", "Khác"
    };
    private static final int BG = Color.rgb(8, 15, 24);
    private static final int PANEL = Color.rgb(18, 31, 45);
    private static final int GREEN = Color.rgb(87, 225, 157);
    private static final int WHITE = Color.rgb(232, 240, 248);
    private Spinner brandPicker;
    private EditText modelInput;
    private TextView result;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(22, 22, 22, 22);
        page.setBackgroundColor(BG);
        scroll.addView(page);

        page.addView(label("ANDROID UNLOCK & RECOVERY", GREEN, 21, true));
        page.addView(label("Công cụ chẩn đoán và khôi phục thiết bị chính chủ", WHITE, 13, false));
        page.addView(label("Chọn hãng", GREEN, 13, true));
        brandPicker = new Spinner(this);
        brandPicker.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item, BRANDS));
        page.addView(brandPicker, new LinearLayout.LayoutParams(-1, dp(48)));

        page.addView(label("Model / mã máy (nếu biết)", GREEN, 13, true));
        modelInput = new EditText(this);
        modelInput.setSingleLine(true);
        modelInput.setHint("Ví dụ: SM-S918B, 2311DRK48G, CPH...");
        modelInput.setTextColor(WHITE);
        modelInput.setHintTextColor(0xff91a9bd);
        modelInput.setBackgroundColor(PANEL);
        page.addView(modelInput, new LinearLayout.LayoutParams(-1, dp(52)));

        result = label("Chưa chạy kiểm tra. Hãy nhập đúng model để tránh dùng nhầm hướng dẫn.",
            WHITE, 14, false);
        result.setPadding(14, 14, 14, 14);
        result.setBackgroundColor(PANEL);
        page.addView(result, new LinearLayout.LayoutParams(-1, -2));

        addButton(page, "CHẨN ĐOÁN / CHỌN HƯỚNG KHÔI PHỤC", v -> assess());
        addButton(page, "QUÊN PIN / MẬT KHẨU / HÌNH VẼ", v -> showScreenLockGuide());
        addButton(page, "QUÊN TÀI KHOẢN GOOGLE (FRP)", v -> showGoogleRecoveryGuide());
        addButton(page, "HỖ TRỢ CHÍNH HÃNG THEO HÃNG MÁY", v -> openOemSupport());
        addButton(page, "GIẢI THÍCH ADB / FASTBOOT / DOWNLOAD MODE", v -> showModes());
        page.addView(label("Lưu ý: Không có phương pháp hợp pháp nào bảo đảm mở khóa mọi model. Khôi phục cài đặt gốc có thể xóa dữ liệu. Nếu thiết bị yêu cầu tài khoản Google đã đồng bộ trước đó, chủ sở hữu cần xác minh tài khoản đó hoặc liên hệ trung tâm dịch vụ có hóa đơn/chứng từ.", 0xffffca70, 12, false));
        setContentView(scroll);
    }

    private void assess() {
        String brand = (String) brandPicker.getSelectedItem();
        String model = modelInput.getText().toString().trim();
        if (brandPicker.getSelectedItemPosition() == 0 || model.isEmpty()) {
            result.setText("Hãy chọn hãng và nhập model. Nếu không biết model, xem nhãn máy hoặc thông tin trên hộp/hóa đơn.");
            return;
        }
        result.setText("Hồ sơ yêu cầu: " + brand + " / " + model
            + "\nTrạng thái: CHƯA XÁC MINH TRÊN THIẾT BỊ"
            + "\nBước tiếp theo: dùng hướng dẫn chính hãng cho đúng model/biến thể/khu vực."
            + "\nKhông tự động xóa dữ liệu, reset, mở khóa bootloader hoặc ghi phân vùng.");
    }

    private void showScreenLockGuide() {
        new AlertDialog.Builder(this)
            .setTitle("Khôi phục khóa màn hình")
            .setMessage("1. Thử phương thức mở khóa dự phòng đã đăng ký.\n"
                + "2. Nếu máy có dịch vụ tìm thiết bị của hãng, đăng nhập tài khoản chủ sở hữu để xem tùy chọn chính thức.\n"
                + "3. Nếu không thể xác minh, liên hệ trung tâm dịch vụ chính hãng.\n"
                + "4. Khôi phục cài đặt gốc chỉ là lựa chọn cuối cùng và thường xóa dữ liệu; sau reset có thể vẫn cần tài khoản Google đã đồng bộ trước đó.\n\n"
                + "Ứng dụng không phá PIN/mật khẩu/hình vẽ và không trích xuất dữ liệu vượt qua khóa bảo vệ.")
            .setPositiveButton("ĐÃ HIỂU", null).show();
    }

    private void showGoogleRecoveryGuide() {
        new AlertDialog.Builder(this)
            .setTitle("Khôi phục tài khoản Google / FRP")
            .setMessage("Nếu quên tài khoản hoặc mật khẩu, hãy khôi phục tài khoản Google chính chủ. "
                + "Sau khi đổi mật khẩu, Google có thể áp dụng thời gian chờ bảo mật trước khi đăng nhập lại trên thiết bị đã reset. "
                + "Nếu thiết bị thuộc sở hữu của bạn nhưng không còn truy cập được tài khoản, chuẩn bị chứng từ mua hàng và liên hệ nhà sản xuất/đại lý.\n\n"
                + "Không hỗ trợ bỏ qua FRP hoặc vô hiệu hóa chống trộm.")
            .setPositiveButton("MỞ KHÔI PHỤC GOOGLE", (d, w) ->
                openUrl("https://accounts.google.com/signin/recovery"))
            .setNegativeButton("ĐÓNG", null).show();
    }

    private void showModes() {
        new AlertDialog.Builder(this)
            .setTitle("Các chế độ kết nối")
            .setMessage("ADB: chẩn đoán khi Android đang khởi động và người dùng đã cấp quyền gỡ lỗi.\n\n"
                + "Fastboot: kiểm tra thông tin bootloader trên thiết bị hỗ trợ.\n\n"
                + "Download / Recovery: chế độ bảo trì riêng của từng hãng.\n\n"
                + "Chỉ đọc trạng thái trước. Không gửi lệnh unlock, erase, format hoặc flash khi chưa có quy trình đúng model và xác nhận rõ ràng.")
            .setPositiveButton("ĐÓNG", null).show();
    }

    private void openOemSupport() {
        String brand = (String) brandPicker.getSelectedItem();
        String url;
        if (brand.startsWith("Samsung")) url = "https://www.samsung.com/support/";
        else if (brand.startsWith("Xiaomi")) url = "https://www.mi.com/global/support/";
        else if (brand.equals("OPPO")) url = "https://support.oppo.com/";
        else if (brand.equals("vivo")) url = "https://www.vivo.com/en/support";
        else if (brand.equals("Huawei")) url = "https://consumer.huawei.com/en/support/";
        else if (brand.equals("Google Pixel")) url = "https://support.google.com/pixelphone/";
        else if (brand.equals("Motorola")) url = "https://en-us.support.motorola.com/";
        else if (brand.equals("OnePlus")) url = "https://service.oneplus.com/";
        else if (brand.equals("realme")) url = "https://www.realme.com/support";
        else {
            new AlertDialog.Builder(this).setMessage("Hãy chọn hãng cụ thể để mở đúng trang hỗ trợ.")
                .setPositiveButton("ĐÓNG", null).show();
            return;
        }
        openUrl(url);
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) {
            new AlertDialog.Builder(this).setMessage("Không mở được trang hỗ trợ: " + e.getMessage())
                .setPositiveButton("ĐÓNG", null).show();
        }
    }

    private void addButton(LinearLayout page, String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(listener);
        page.addView(button, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private TextView label(String value, int color, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
