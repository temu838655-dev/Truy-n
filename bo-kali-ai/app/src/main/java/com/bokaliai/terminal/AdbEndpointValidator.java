package com.bokaliai.terminal;

import java.net.Inet6Address;
import java.net.InetAddress;

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
                    || endpoint.charAt(close + 1) != ':' || endpoint.indexOf(']', close + 1) >= 0) {
                return false;
            }
            host = endpoint.substring(1, close);
            portText = endpoint.substring(close + 2);
            if (!isIpv6Literal(host)) return false;
        } else {
            int colon = endpoint.lastIndexOf(':');
            if (colon <= 0 || endpoint.indexOf(':') != colon) return false;
            host = endpoint.substring(0, colon);
            portText = endpoint.substring(colon + 1);
            if (!isHostnameOrIpv4(host)) return false;
        }
        if (!portText.matches("[0-9]{1,5}")) return false;
        try {
            int port = Integer.parseInt(portText);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException invalidPort) {
            return false;
        }
    }

    private static boolean isHostnameOrIpv4(String host) {
        if (host == null || host.isEmpty() || host.length() > 253) return false;
        String[] labels = host.split("\\.", -1);
        boolean dottedNumeric = labels.length == 4;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || !label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")) {
                return false;
            }
            if (!label.matches("[0-9]+")) dottedNumeric = false;
        }
        // A four-part numeric host is an IPv4 literal, not a DNS hostname.
        // Validate every octet so values such as 999.1.1.1 are rejected.
        if (dottedNumeric) {
            for (String label : labels) {
                try {
                    if (Integer.parseInt(label) > 255) return false;
                } catch (NumberFormatException invalidOctet) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String host) {
        if (host == null || host.isEmpty()) return false;
        String address = host;
        int zone = host.indexOf('%');
        if (zone >= 0) {
            if (zone == 0 || zone == host.length() - 1 || host.indexOf('%', zone + 1) >= 0) {
                return false;
            }
            String zoneId = host.substring(zone + 1);
            if (!zoneId.matches("[A-Za-z0-9_.-]+")) return false;
            address = host.substring(0, zone);
        }
        if (address.indexOf(':') < 0) return false;
        try {
            return InetAddress.getByName(address) instanceof Inet6Address;
        } catch (Exception invalidAddress) {
            return false;
        }
    }
}
