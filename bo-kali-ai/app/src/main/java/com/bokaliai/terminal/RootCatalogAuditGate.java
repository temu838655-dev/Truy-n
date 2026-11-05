package com.bokaliai.terminal;

/** Exact completion gate for a fresh, one-shot ADB target audit. */
public final class RootCatalogAuditGate {
    private RootCatalogAuditGate() {}

    public static String startMarker(String token) {
        return isValidToken(token) ? "BO_ROOT_CATALOG_AUDIT:" + token : "";
    }

    public static boolean hasCompleteAudit(String output, String token) {
        if (output == null || output.isEmpty()) return false;
        String marker = startMarker(token);
        if (marker.isEmpty()) return false;
        return hasExactLine(output, marker) && hasExactLine(output, marker + ":END");
    }

    private static boolean hasExactLine(String output, String expected) {
        for (String line : output.split("\\r?\\n")) {
            if (expected.equals(line)) return true;
        }
        return false;
    }

    private static boolean isValidToken(String token) {
        return token != null && token.matches("[0-9a-fA-F-]{36}");
    }
}
