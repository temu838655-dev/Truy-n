package com.bokaliai.terminal;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdbEndpointValidatorTest {
    @Test public void acceptsIpv4AndHostnameEndpoints() {
        assertTrue(AdbEndpointValidator.isValid("192.168.1.20:5555"));
        assertTrue(AdbEndpointValidator.isValid("android-box.local:5555"));
    }

    @Test public void acceptsBracketedIpv6Endpoints() {
        assertTrue(AdbEndpointValidator.isValid("[2001:db8::10]:5555"));
        assertTrue(AdbEndpointValidator.isValid("[fe80::1%wlan0]:37123"));
    }

    @Test public void rejectsUnbracketedIpv6AndMalformedBrackets() {
        assertFalse(AdbEndpointValidator.isValid("2001:db8::10:5555"));
        assertFalse(AdbEndpointValidator.isValid("[2001:db8::10:5555"));
        assertFalse(AdbEndpointValidator.isValid("2001:db8::10]:5555"));
        assertFalse(AdbEndpointValidator.isValid("[2001:db8::10]5555"));
    }

    @Test public void rejectsMissingHostOrPort() {
        assertFalse(AdbEndpointValidator.isValid(":5555"));
        assertFalse(AdbEndpointValidator.isValid("192.168.1.20:"));
        assertFalse(AdbEndpointValidator.isValid(""));
        assertFalse(AdbEndpointValidator.isValid(null));
    }

    @Test public void enforcesPortRangeAndNumericFormat() {
        assertTrue(AdbEndpointValidator.isValid("box.local:1"));
        assertTrue(AdbEndpointValidator.isValid("box.local:65535"));
        assertFalse(AdbEndpointValidator.isValid("box.local:0"));
        assertFalse(AdbEndpointValidator.isValid("box.local:65536"));
        assertFalse(AdbEndpointValidator.isValid("box.local:abc"));
        assertFalse(AdbEndpointValidator.isValid("box.local:123456"));
    }

    @Test public void rejectsWhitespaceAndUnexpectedCharacters() {
        assertFalse(AdbEndpointValidator.isValid("192.168.1.20 :5555"));
        assertFalse(AdbEndpointValidator.isValid("box.local:55 55"));
        assertFalse(AdbEndpointValidator.isValid("box.local;reboot:5555"));
    }
}
