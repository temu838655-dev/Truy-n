package com.bokaliai.terminal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Imports evidence metadata only. Imported records are ALWAYS downgraded to
 * RESEARCH_REQUIRED and cannot authorize root, unlock, or flashing operations.
 */
public final class DeviceSupportProfileImporter {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public static final class ImportResult {
        private final List<AndroidDeviceSupportCatalog.Entry> entries;
        private final List<String> errors;

        private ImportResult(List<AndroidDeviceSupportCatalog.Entry> entries,
                List<String> errors) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        public List<AndroidDeviceSupportCatalog.Entry> getEntries() { return entries; }
        public List<String> getErrors() { return errors; }
        public boolean isSuccess() { return errors.isEmpty() && !entries.isEmpty(); }
    }

    private DeviceSupportProfileImporter() {}

    public static ImportResult parse(String json) {
        List<AndroidDeviceSupportCatalog.Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            errors.add("Tệp JSON trống.");
            return new ImportResult(entries, errors);
        }
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("schemaVersion", -1) != SUPPORTED_SCHEMA_VERSION) {
                errors.add("Phiên bản schema không được hỗ trợ.");
                return new ImportResult(entries, errors);
            }
            JSONArray profiles = root.optJSONArray("profiles");
            if (profiles == null || profiles.length() == 0) {
                errors.add("Thiếu mảng profiles hoặc không có hồ sơ.");
                return new ImportResult(entries, errors);
            }
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.optJSONObject(i);
                if (profile == null) {
                    errors.add("Hồ sơ #" + (i + 1) + " không phải object JSON.");
                    continue;
                }
                try {
                    String manufacturer = required(profile, "manufacturer");
                    String model = required(profile, "model");
                    String codename = required(profile, "codename");
                    String fingerprint = required(profile, "firmwareFingerprint");
                    String region = required(profile, "regionOrSku");
                    String summary = required(profile, "summary");
                    String sourceUrl = required(profile, "sourceUrl");
                    String verifiedDate = required(profile, "verifiedDate");
                    AndroidDeviceSupportCatalog.EvidenceSource source =
                        AndroidDeviceSupportCatalog.EvidenceSource.valueOf(
                            required(profile, "evidenceSource"));
                    // Parse the claimed status for schema validation, but never trust it.
                    AndroidDeviceSupportCatalog.Status.valueOf(required(profile, "status"));
                    AndroidDeviceSupportCatalog.Entry entry =
                        new AndroidDeviceSupportCatalog.Entry(
                            manufacturer, model, codename, fingerprint, region,
                            AndroidDeviceSupportCatalog.Status.RESEARCH_REQUIRED,
                            summary, sourceUrl, verifiedDate, source, false,
                            "Hồ sơ nhập từ JSON; cần người dùng kiểm tra nguồn độc lập trong ứng dụng.");
                    entries.add(entry);
                } catch (IllegalArgumentException | JSONException e) {
                    errors.add("Hồ sơ #" + (i + 1) + " không hợp lệ: " + safeMessage(e));
                }
            }
        } catch (JSONException e) {
            errors.add("JSON sai cú pháp: " + safeMessage(e));
        }
        return new ImportResult(entries, errors);
    }

    private static String required(JSONObject object, String key) throws JSONException {
        Object value = object.opt(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new JSONException("Thiếu trường " + key + " dạng chuỗi.");
        }
        return ((String) value).trim();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "dữ liệu không hợp lệ" : message;
    }
}
