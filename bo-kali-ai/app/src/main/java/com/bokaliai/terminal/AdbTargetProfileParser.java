package com.bokaliai.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the read-only TARGET SERIAL blocks emitted by runAdbDeviceSecurityAudit().
 * This parser identifies devices only; it does not infer rootability or unlock support.
 */
public final class AdbTargetProfileParser {
    private AdbTargetProfileParser() {}

    public static final class DeviceProfile {
        private final String serial;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private boolean complete;

        private DeviceProfile(String serial) {
            this.serial = serial;
        }

        public String getSerial() {
            return serial;
        }

        public String get(String property) {
            return properties.get(property);
        }

        public Map<String, String> getProperties() {
            return Collections.unmodifiableMap(properties);
        }


        /**
         * Stable, labeled identity for catalog matching. SKU, region and locale
         * remain separate values; none is silently substituted for another.
         */
        public String getCatalogRegionSkuKey() {
            String[] keys = {
                "ro.boot.hardware.sku",
                "ro.boot.product.hardware.sku",
                "ro.boot.region",
                "ro.product.locale"
            };
            StringBuilder key = new StringBuilder();
            for (String property : keys) {
                String value = get(property);
                if (hasText(value)) {
                    if (key.length() > 0) key.append("|");
                    key.append(property).append("=").append(value);
                }
            }
            return key.toString();
        }

        public boolean isComplete() {
            return complete;
        }

        /**
         * A profile is eligible for exact catalog lookup only when every identity
         * dimension required by the catalog was actually collected. Missing SKU or
         * region data must never be treated as a wildcard.
         */
        public List<String> getMissingMinimumIdentityFields() {
            List<String> missing = new ArrayList<>();
            if (!complete) missing.add("dấu kết thúc hồ sơ ADB");
            if (!hasText(get("ro.product.manufacturer"))) missing.add("hãng (ro.product.manufacturer)");
            if (!hasText(get("ro.product.model"))) missing.add("model (ro.product.model)");
            if (!hasText(get("ro.product.device"))) missing.add("codename (ro.product.device)");
            if (!hasText(get("ro.build.fingerprint"))) missing.add("firmware fingerprint (ro.build.fingerprint)");
            // Locale alone is not a hardware variant identifier. Require an actual
            // boot SKU or boot region before a profile can match root/unlock guidance.
            if (!hasHardwareSkuOrRegion()) missing.add("hardware SKU/region (ro.boot.*.sku or ro.boot.region)");
            return Collections.unmodifiableList(missing);
        }

        public boolean hasMinimumIdentity() {
            return getMissingMinimumIdentityFields().isEmpty();
        }

        private boolean hasHardwareSkuOrRegion() {
            return hasText(get("ro.boot.hardware.sku"))
                || hasText(get("ro.boot.product.hardware.sku"))
                || hasText(get("ro.boot.region"));
        }

        private static boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }

    public static List<DeviceProfile> parse(String output) {
        if (output == null || output.isEmpty()) return Collections.emptyList();
        List<DeviceProfile> profiles = new ArrayList<>();
        DeviceProfile current = null;
        String[] lines = output.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.startsWith("=== TARGET SERIAL: ") && line.endsWith(" ===")) {
                String serial = line.substring("=== TARGET SERIAL: ".length(), line.length() - 4).trim();
                if (isValidSerial(serial)) {
                    current = new DeviceProfile(serial);
                    profiles.add(current);
                } else {
                    current = null;
                }
                continue;
            }
            if (current == null) continue;
            if (line.equals("--- END TARGET PROFILE: READ ONLY ---")) {
                current.complete = true;
                current = null;
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.matches("ro\\.[A-Za-z0-9_.]+") && !value.isEmpty()) {
                current.properties.put(key, value);
            }
        }
        return Collections.unmodifiableList(profiles);
    }

    private static boolean isValidSerial(String serial) {
        return serial != null && !serial.isEmpty()
            && serial.matches("[A-Za-z0-9._:-]{1,128}");
    }
}
