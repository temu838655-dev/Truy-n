package com.bokaliai.terminal;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class AndroidDeviceSupportCatalogTest {
    private AndroidDeviceSupportCatalog.Entry verifiedEntry() {
        return new AndroidDeviceSupportCatalog.Entry(
            "Example", "Model X", "device_x", "example/device_x:14/BUILD/123:user/release-keys",
            "REGION_A", AndroidDeviceSupportCatalog.Status.VERIFIED_UNLOCK_SUPPORTED,
            "OEM documentation confirms unlock eligibility for this exact SKU.",
            "https://example.com/device-x-unlock", "2026-11-05");
    }

    @Test
    public void requiresExactManufacturerModelCodenameFirmwareAndRegion() {
        AndroidDeviceSupportCatalog.Entry entry = verifiedEntry();
        AndroidDeviceSupportCatalog.Result result = AndroidDeviceSupportCatalog.lookup(
            Collections.singletonList(entry), "Example", "Model X", "device_x",
            "example/device_x:14/BUILD/123:user/release-keys", "REGION_A");
        assertTrue(result.isVerifiedMatch());
        assertEquals(entry, result.getEntry());
    }

    @Test
    public void rejectsDifferentFirmwareEvenWhenModelAndCodenameMatch() {
        AndroidDeviceSupportCatalog.Result result = AndroidDeviceSupportCatalog.lookup(
            Collections.singletonList(verifiedEntry()), "Example", "Model X", "device_x",
            "example/device_x:14/OTHER/456:user/release-keys", "REGION_A");
        assertFalse(result.isVerifiedMatch());
        assertNull(result.getEntry());
    }

    @Test
    public void rejectsDifferentRegionOrSku() {
        AndroidDeviceSupportCatalog.Result result = AndroidDeviceSupportCatalog.lookup(
            Collections.singletonList(verifiedEntry()), "Example", "Model X", "device_x",
            "example/device_x:14/BUILD/123:user/release-keys", "REGION_B");
        assertFalse(result.isVerifiedMatch());
    }

    @Test
    public void rejectsEntriesWithoutSourceOrCompleteEvidence() {
        AndroidDeviceSupportCatalog.Entry incomplete = new AndroidDeviceSupportCatalog.Entry(
            "Example", "Model X", "device_x", "", "REGION_A",
            AndroidDeviceSupportCatalog.Status.VERIFIED_UNLOCK_SUPPORTED,
            "Claim without exact firmware evidence.", "", "2026-11-05");
        AndroidDeviceSupportCatalog.Result result = AndroidDeviceSupportCatalog.lookup(
            Arrays.asList(incomplete), "Example", "Model X", "device_x", "", "REGION_A");
        assertFalse(result.isVerifiedMatch());
        assertNull(result.getEntry());
    }

    @Test
    public void builtInCatalogDoesNotPretendToSupportUnverifiedDevices() {
        assertTrue(AndroidDeviceSupportCatalog.builtInEntries().isEmpty());
        assertFalse(AndroidDeviceSupportCatalog.lookupBuiltIn(
            "vivo", "Y21", "unknown", "unknown", "unknown").isVerifiedMatch());
    }

    @Test
    public void doesNotUseFuzzyModelMatching() {
        AndroidDeviceSupportCatalog.Result result = AndroidDeviceSupportCatalog.lookup(
            Collections.singletonList(verifiedEntry()), "Example", "Model X Pro", "device_x",
            "example/device_x:14/BUILD/123:user/release-keys", "REGION_A");
        assertFalse(result.isVerifiedMatch());
    }
}
