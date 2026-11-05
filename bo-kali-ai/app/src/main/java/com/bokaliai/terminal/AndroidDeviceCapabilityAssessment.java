package com.bokaliai.terminal;

/**
 * Conservative capability assessment from read-only Android properties.
 * It never claims a device is rootable or flashable from a boot-state property alone.
 */
public final class AndroidDeviceCapabilityAssessment {
    public enum State { AVAILABLE, BLOCKED, UNKNOWN }

    public static final class Result {
        public final State rootStatus;
        public final String rootReason;
        public final State bootloaderStatus;
        public final String bootloaderReason;
        public final State kernelFlashStatus;
        public final String kernelFlashReason;

        private Result(State rootStatus, String rootReason,
                       State bootloaderStatus, String bootloaderReason,
                       State kernelFlashStatus, String kernelFlashReason) {
            this.rootStatus = rootStatus;
            this.rootReason = rootReason;
            this.bootloaderStatus = bootloaderStatus;
            this.bootloaderReason = bootloaderReason;
            this.kernelFlashStatus = kernelFlashStatus;
            this.kernelFlashReason = kernelFlashReason;
        }
    }

    private AndroidDeviceCapabilityAssessment() { }

    public static Result assess(AdbTargetProfileParser.DeviceProfile profile) {
        if (profile == null) {
            return new Result(State.UNKNOWN, "Không có hồ sơ thiết bị đích.",
                State.UNKNOWN, "Chưa đọc được trạng thái bootloader.",
                State.UNKNOWN, "Chưa có dữ liệu kernel/phân vùng.");
        }

        String uid = profile.get("ANDROID_UID");
        State root = State.UNKNOWN;
        String rootReason = "ADB shell không chứng minh được quyền root; cần kiểm tra quyền su riêng và có sự đồng ý.";
        if (uid != null && uid.matches("uid=0(?:\\D|$).*")) {
            root = State.AVAILABLE;
            rootReason = "ADB shell báo UID 0; vẫn cần xác minh quyền root ổn định và phạm vi quyền.";
        } else if (uid != null && uid.startsWith("uid=")) {
            root = State.UNKNOWN;
            rootReason = "ADB shell không chạy bằng UID 0; chưa kiểm tra được su/Magisk nên chưa thể kết luận máy đã root hay chưa.";
        }

        String locked = first(profile, "ro.boot.flash.locked", "ro.boot.vbmeta.device_state");
        State bootloader = State.UNKNOWN;
        String bootReason = "Không có thuộc tính bootloader đủ tin cậy; cần kiểm tra trực tiếp trong bootloader/fastboot.";
        if ("0".equals(profile.get("ro.boot.flash.locked"))
                || "unlocked".equalsIgnoreCase(profile.get("ro.boot.vbmeta.device_state"))) {
            bootloader = State.AVAILABLE;
            bootReason = "Android báo trạng thái đã mở khóa; cần xác nhận lại bằng fastboot getvar trên đúng thiết bị.";
        } else if ("1".equals(profile.get("ro.boot.flash.locked"))
                || "locked".equalsIgnoreCase(profile.get("ro.boot.vbmeta.device_state"))) {
            bootloader = State.BLOCKED;
            bootReason = "Android báo bootloader đang khóa; cần kiểm tra chính sách OEM và điều kiện mở khóa.";
        } else if (locked != null) {
            bootReason = "Có dữ liệu boot nhưng giá trị không thuộc trạng thái chuẩn; không suy đoán.";
        }

        String slot = profile.get("ro.boot.slot_suffix");
        String dynamic = profile.get("ro.boot.dynamic_partitions");
        State kernel = State.UNKNOWN;
        String kernelReason = "Chưa xác minh kernel đúng codename, firmware, checksum, chữ ký và sơ đồ phân vùng.";
        if (bootloader == State.BLOCKED) {
            kernel = State.BLOCKED;
            kernelReason = "Bootloader đang khóa theo thuộc tính Android; không thể kết luận flash kernel ngoài OEM an toàn.";
        } else if (bootloader == State.AVAILABLE) {
            kernelReason = "Bootloader có vẻ đã mở; vẫn thiếu image kernel tương thích, checksum và xác nhận phân vùng.";
        }
        if (slot != null || dynamic != null) {
            kernelReason += " Metadata phân vùng: slot=" + (slot == null ? "không rõ" : slot)
                + ", dynamic=" + (dynamic == null ? "không rõ" : dynamic) + ".";
        }

        return new Result(root, rootReason, bootloader, bootReason, kernel, kernelReason);
    }

    private static String first(AdbTargetProfileParser.DeviceProfile profile, String a, String b) {
        String value = profile.get(a);
        return value == null || value.isEmpty() ? profile.get(b) : value;
    }
}
