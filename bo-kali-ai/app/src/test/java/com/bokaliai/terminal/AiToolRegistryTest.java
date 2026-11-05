package com.bokaliai.terminal;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests for the AI-to-ADB proposal validation boundary. */
public class AiToolRegistryTest {
    private Set<String> grant(String scope) {
        return new HashSet<>(Collections.singletonList(scope));
    }

    @Test
    public void acceptsSingleReadOnlyCommandWithMatchingGrant() {
        assertEquals("OK", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.READ_INFO, "device-01", "getprop", grant(AiToolRegistry.READ_INFO)));
    }

    @Test
    public void rejectsMissingPermissionGrant() {
        assertEquals("SCOPE_NOT_GRANTED", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.READ_INFO, "device-01", "getprop", Collections.emptySet()));
    }

    @Test
    public void rejectsCommandInjectionSeparators() {
        assertEquals("INVALID_SINGLE_COMMAND", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.READ_INFO, "device-01", "getprop; reboot", grant(AiToolRegistry.READ_INFO)));
    }

    @Test
    public void rejectsDestructiveCommands() {
        assertEquals("BLOCKED_COMMAND", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.READ_INFO, "device-01", "reboot", grant(AiToolRegistry.READ_INFO)));
        assertEquals("BLOCKED_COMMAND", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.APP_INSTALL_REMOVE, "device-01", "pm clear com.example.app",
            grant(AiToolRegistry.APP_INSTALL_REMOVE)));
    }

    @Test
    public void rejectsCommandThatDoesNotMatchGrantedScope() {
        assertEquals("COMMAND_NOT_ALLOWED_FOR_SCOPE", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.READ_INFO, "device-01", "pm list packages",
            grant(AiToolRegistry.READ_INFO)));
        assertEquals("COMMAND_NOT_ALLOWED_FOR_SCOPE", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.APP_LIST, "device-01", "pm uninstall com.example.app",
            grant(AiToolRegistry.APP_LIST)));
    }

    @Test
    public void acceptsOnlyAllowlistedCommandForAppListAndRemovalScopes() {
        assertEquals("OK", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.APP_LIST, "device-01", "pm list packages -3",
            grant(AiToolRegistry.APP_LIST)));
        assertEquals("OK", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.APP_INSTALL_REMOVE, "device-01", "pm uninstall com.example.app",
            grant(AiToolRegistry.APP_INSTALL_REMOVE)));
    }

    @Test
    public void rawShellGrantDoesNotAllowArbitraryShellCommands() {
        assertEquals("COMMAND_NOT_ALLOWED_FOR_SCOPE", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.RAW_ADB_SHELL, "device-01", "echo hello",
            grant(AiToolRegistry.RAW_ADB_SHELL)));
        assertEquals("COMMAND_NOT_ALLOWED_FOR_SCOPE", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.RAW_ADB_SHELL, "device-01", "pm disable com.example.app",
            grant(AiToolRegistry.RAW_ADB_SHELL)));
    }

    @Test
    public void rejectsMalformedDeviceSerial() {
        assertEquals("INVALID_DEVICE_SERIAL", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.READ_INFO, "device-01\nother", "getprop", grant(AiToolRegistry.READ_INFO)));
    }

    @Test
    public void rejectsUnknownAdbScope() {
        assertEquals("UNKNOWN_ADB_SCOPE", AiToolRegistry.validateAdbPlan(
            "UNRECOGNIZED_SCOPE", "device-01", "getprop", grant("UNRECOGNIZED_SCOPE")));
    }

    @Test
    public void validatesRepositoryPaths() {
        assertTrue(AiToolRegistry.validRepoPath("app/src/main/MainActivity.java"));
        assertFalse(AiToolRegistry.validRepoPath("../secrets.txt"));
        assertFalse(AiToolRegistry.validRepoPath("/etc/passwd"));
        assertFalse(AiToolRegistry.validRepoPath("app//MainActivity.java"));
    }

    @Test
    public void rejectsAllShellCompositionCharacters() {
        String[] commands = {
            "getprop && id",
            "getprop || id",
            "getprop > /sdcard/out.txt",
            "getprop `id`",
            "getprop $HOME",
            "getprop\nreboot"
        };
        for (String command : commands) {
            assertEquals("INVALID_SINGLE_COMMAND", AiToolRegistry.validateAdbPlan(
                AiToolRegistry.READ_INFO, "device-01", command, grant(AiToolRegistry.READ_INFO)));
        }
    }

    @Test
    public void readInfoScopeRejectsCommandsOutsideItsExactAllowlist() {
        String[] commands = {
            "cat /data/system/packages.xml",
            "pm list packages",
            "settings get system screen_brightness",
            "getprop ro.serialno"
        };
        for (String command : commands) {
            assertEquals("COMMAND_NOT_ALLOWED_FOR_SCOPE", AiToolRegistry.validateAdbPlan(
                AiToolRegistry.READ_INFO, "device-01", command, grant(AiToolRegistry.READ_INFO)));
        }
        assertEquals("BLOCKED_COMMAND", AiToolRegistry.validateAdbPlan(
            AiToolRegistry.READ_INFO, "device-01", "curl https://example.com",
            grant(AiToolRegistry.READ_INFO)));
    }

    @Test
    public void readInfoScopeAcceptsOnlyDocumentedReadOnlyCommands() {
        String[] commands = {
            "getprop",
            "getprop ro.product.model",
            "getprop ro.build.version.release",
            "df -h",
            "uptime",
            "uname -a",
            "dumpsys battery",
            "ip addr show wlan0",
            "cat /proc/meminfo"
        };
        for (String command : commands) {
            assertEquals("OK", AiToolRegistry.validateAdbPlan(
                AiToolRegistry.READ_INFO, "device-01", command, grant(AiToolRegistry.READ_INFO)));
        }
    }

}
