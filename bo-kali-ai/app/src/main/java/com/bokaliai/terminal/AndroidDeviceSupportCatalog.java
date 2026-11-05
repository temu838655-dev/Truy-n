package com.bokaliai.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Strict, evidence-backed Android support catalog.
 *
 * An entry is usable only when manufacturer, retail model, device codename,
 * full firmware fingerprint and region/SKU all match exactly. No fuzzy matching
 * is allowed for bootloader, root or flashing guidance.
 */
public final class AndroidDeviceSupportCatalog {
    public enum EvidenceSource {
        OEM_OFFICIAL,
        PROJECT_DOCUMENTATION,
        COMMUNITY_REFERENCE,
        UNKNOWN
    }

    public enum Status {
        VERIFIED_UNLOCK_SUPPORTED,
        VERIFIED_UNLOCK_UNSUPPORTED,
        VERIFIED_ROOT_METHOD,
        RESEARCH_REQUIRED
    }

    public static final class Entry {
        public final String manufacturer;
        public final String model;
        public final String codename;
        public final String firmwareFingerprint;
        public final String regionOrSku;
        public final Status status;
        public final String summary;
        public final String sourceUrl;
        public final String verifiedDate;
        public final EvidenceSource evidenceSource;
        public final boolean manuallyReviewed;
        public final String reviewerNote;

        /** Legacy constructor is deliberately unreviewed and cannot assert verified support. */
        public Entry(String manufacturer, String model, String codename,
                String firmwareFingerprint, String regionOrSku, Status status,
                String summary, String sourceUrl, String verifiedDate) {
            this(manufacturer, model, codename, firmwareFingerprint, regionOrSku,
                status, summary, sourceUrl, verifiedDate, EvidenceSource.UNKNOWN,
                false, "");
        }

        public Entry(String manufacturer, String model, String codename,
                String firmwareFingerprint, String regionOrSku, Status status,
                String summary, String sourceUrl, String verifiedDate,
                EvidenceSource evidenceSource, boolean manuallyReviewed,
                String reviewerNote) {
            this.manufacturer = clean(manufacturer);
            this.model = clean(model);
            this.codename = clean(codename);
            this.firmwareFingerprint = clean(firmwareFingerprint);
            this.regionOrSku = clean(regionOrSku);
            this.status = status == null ? Status.RESEARCH_REQUIRED : status;
            this.summary = clean(summary);
            this.sourceUrl = clean(sourceUrl);
            this.verifiedDate = clean(verifiedDate);
            this.evidenceSource = evidenceSource == null ? EvidenceSource.UNKNOWN : evidenceSource;
            this.manuallyReviewed = manuallyReviewed;
            this.reviewerNote = clean(reviewerNote);
        }

        public boolean hasRequiredEvidence() {
            return !manufacturer.isEmpty() && !model.isEmpty()
                && !codename.isEmpty() && !firmwareFingerprint.isEmpty()
                && !regionOrSku.isEmpty()
                && isHttpsSource(sourceUrl)
                && isValidVerificationDate(verifiedDate)
                && !summary.isEmpty()
                && evidenceSource != EvidenceSource.UNKNOWN
                && evidenceSource != EvidenceSource.COMMUNITY_REFERENCE
                && manuallyReviewed && !reviewerNote.isEmpty();
        }
    }

    public static final class Result {
        private final Entry entry;

        private Result(Entry entry) {
            this.entry = entry;
        }

        public boolean isVerifiedMatch() {
            return entry != null && entry.status != Status.RESEARCH_REQUIRED
                && entry.hasRequiredEvidence();
        }

        public Entry getEntry() {
            return entry;
        }

        public String getMessageVi() {
            if (entry == null) {
                return "CHƯA CÓ HỒ SƠ XÁC MINH KHỚP CHÍNH XÁC model + codename + firmware + SKU/region. Không suy đoán lệnh root/unlock/flash.";
            }
            if (!entry.hasRequiredEvidence() || entry.status == Status.RESEARCH_REQUIRED) {
                return "CÓ BẢN GHI NHƯNG BẰNG CHỨNG CHƯA ĐỦ. Chỉ tiếp tục nghiên cứu, không dùng làm hướng dẫn thao tác.";
            }
            switch (entry.status) {
                case VERIFIED_UNLOCK_SUPPORTED:
                    return "ĐÃ XÁC MINH: OEM có chính sách unlock cho đúng biến thể này. Vẫn phải đọc hướng dẫn và cảnh báo xóa dữ liệu.";
                case VERIFIED_UNLOCK_UNSUPPORTED:
                    return "ĐÃ XÁC MINH: biến thể này không hỗ trợ unlock theo nguồn dẫn. Không thử lệnh mở khóa.";
                case VERIFIED_ROOT_METHOD:
                    return "ĐÃ XÁC MINH phương pháp root cho đúng fingerprint/biến thể; phải kiểm tra image và checksum trước khi thao tác.";
                default:
                    return "CHƯA ĐỦ BẰNG CHỨNG.";
            }
        }

        /** Human-readable provenance for a verified exact match; never a flash command. */
        public String getEvidenceSummaryVi() {
            if (!isVerifiedMatch()) return "Không có bằng chứng đủ điều kiện để hiển thị.";
            return "Model: " + entry.model + " | Codename: " + entry.codename
                + " | Firmware: " + entry.firmwareFingerprint
                + " | SKU/Region: " + entry.regionOrSku
                + " | Xác minh: " + entry.verifiedDate
                + " | Nguồn: " + entry.sourceUrl
                + " | Ghi chú: " + entry.summary;
        }
    }

    private AndroidDeviceSupportCatalog() {}

    /** Empty until exact-model evidence has been reviewed and added. */
    public static List<Entry> builtInEntries() {
        return Collections.emptyList();
    }

    public static Result lookupBuiltIn(String manufacturer, String model,
            String codename, String firmwareFingerprint, String regionOrSku) {
        return lookup(builtInEntries(), manufacturer, model, codename,
            firmwareFingerprint, regionOrSku);
    }

    public static Result lookup(List<Entry> entries, String manufacturer,
            String model, String codename, String firmwareFingerprint, String regionOrSku) {
        if (entries == null || entries.isEmpty()) return new Result(null);
        for (Entry entry : entries) {
            if (entry == null || !entry.hasRequiredEvidence()) continue;
            if (same(entry.manufacturer, manufacturer)
                    && same(entry.model, model)
                    && same(entry.codename, codename)
                    && same(entry.firmwareFingerprint, firmwareFingerprint)
                    && same(entry.regionOrSku, regionOrSku)) {
                return new Result(entry);
            }
        }
        return new Result(null);
    }


    /** Accept only a real calendar date in the documented ISO format. */
    private static boolean isValidVerificationDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return parsed != null && position.getIndex() == value.length();
    }

    private static boolean isHttpsSource(String value) {
        if (value == null || value.trim().isEmpty() || value.matches(".*\\\\s+.*")) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null && !uri.getHost().isEmpty()
                && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean same(String expected, String actual) {
        return expected != null && actual != null
            && normalize(expected).equals(normalize(actual));
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
