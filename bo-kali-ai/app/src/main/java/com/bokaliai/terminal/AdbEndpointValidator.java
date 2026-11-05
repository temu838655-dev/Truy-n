package com.bokaliai.terminal;

/** Pure validation for ADB connect endpoints (not wireless-pairing endpoints). */
public final class AdbEndpointValidator {
    private AdbEndpointValidator() { }

    public static boolean isValid(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) return false;
        String host;
        String portText;
        if (endpoint.startsWith("[")) {
            int close = endpoint.indexOf(']');
            if (close < 0 || close + 1 >= endpoint.length()
                    || endpoint.charAt(close + 1) != ':') return false;
            host = endpoint.substring(1, close);
            portText = endpoint.substring(close + 2);
            if (!host.matches("[0-9A-Fa-f:.%a-zA-Z_-]+") || host.indexOf(':') < 0) return false;
        } else {
            int colon = endpoint.lastIndexOf(':');
            if (colon <= 0 || endpoint.indexOf(':') != colon) return false;
            host = endpoint.substring(0, colon);
            portText = endpoint.substring(colon + 1);
            if (!host.matches("[A-Za-z0-9.-]+")) return false;
        }
        if (!portText.matches("[0-9]{1,5}")) return false;
        try {
            int port = Integer.parseInt(portText);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException invalidPort) {
            return false;
        }
    }
}
