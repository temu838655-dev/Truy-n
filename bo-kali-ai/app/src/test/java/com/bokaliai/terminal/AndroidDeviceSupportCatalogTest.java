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
            "https://example.com/device-x-unlock", "2026-11-05",
            AndroidDeviceSupportCatalog.EvidenceSource.OEM_OFFICIAL, true,
            "Unit-test fixture: evidence manually reviewed.");
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
            "Claim without exact firmware evidence.", "", "2026-11-05",
            AndroidDeviceSupportCatalog.EvidenceSource.OEM_OFFICIAL, true,
            "Incomplete fixture.");
        AndroidDeviceSupportCatalog.Result result = AndroidDeviceSupportCatalog.lookup(
            Arrays.asList(incomplete), "Example", "Model X", "device_x", "", "REGION_A");
        assertFalse(result.isVerifiedMatch());
        assertNull(result.getEntry());
    }

    @Test
    public void verifiedMatchExposesItsEvidenceAndProvenance() {
        AndroidDeviceSupportCatalog.Result result = AndroidDeviceSupportCatalog.lookup(
            Collections.singletonList(verifiedEntry()), "Example", "Model X", "device_x",
            "example/device_x:14/BUILD/123:user/release-keys", "REGION_A");
        assertTrue(result.getEvidenceSummaryVi().contains("2026-11-05"));
        assertTrue(result.getEvidenceSummaryVi().contains("https://example.com/device-x-unlock"));
        assertTrue(result.getEvidenceSummaryVi().contains("REGION_A"));
    }

    @Test
    public void legacyEntryCannotClaimVerifiedStatusWithoutManualReview() {
        AndroidDeviceSupportCatalog.Entry legacy = new AndroidDeviceSupportCatalog.Entry(
            "Example", "Model X", "device_x", "fingerprint", "REGION_A",
            AndroidDeviceSupportCatalog.Status.VERIFIED_UNLOCK_SUPPORTED,
            "Claim from an unreviewed record.", "https://example.com/source", "2026-11-05");
        assertFalse(legacy.hasRequiredEvidence());
        assertFalse(AndroidDeviceSupportCatalog.lookup(
            Collections.singletonList(legacy), "Example", "Model X", "device_x",
            "fingerprint", "REGION_A").isVerifiedMatch());
    }

    @Test
    public void communityReferenceAloneDoesNotCountAsVerifiedEvidence() {
        AndroidDeviceSupportCatalog.Entry community = new AndroidDeviceSupportCatalog.Entry(
            "Example", "Model X", "device_x", "fingerprint", "REGION_A",
            AndroidDeviceSupportCatalog.Status.VERIFIED_UNLOCK_SUPPORTED,
            "Community report only.", "https://example.com/community", "2026-11-05",
            AndroidDeviceSupportCatalog.EvidenceSource.COMMUNITY_REFERENCE, true,
            "Reviewed but not corroborated by OEM documentation.");
        assertFalse(community.hasRequiredEvidence());
    }

    @Test
    public void reviewedOfficialEvidenceCanPassTheTrustGate() {
        AndroidDeviceSupportCatalog.Entry entry = verifiedEntry();
        assertTrue(entry.manuallyReviewed);
        assertEquals(AndroidDeviceSupportCatalog.EvidenceSource.OEM_OFFICIAL, entry.evidenceSource);
        assertTrue(entry.hasRequiredEvidence());
    }

    @Test
    public void reviewedProjectDocumentationCanPassEvidenceGate() {
        AndroidDeviceSupportCatalog.Entry projectDoc = new AndroidDeviceSupportCatalog.Entry(
            "Example", "Model X", "device_x", "fingerprint", "REGION_A",
            AndroidDeviceSupportCatalog.Status.VERIFIED_ROOT_METHOD,
            "Project documentation for the exact firmware.", "https://example.com/project-doc",
            "2026-11-05", AndroidDeviceSupportCatalog.EvidenceSource.PROJECT_DOCUMENTATION,
            true, "Reviewed against the exact device and firmware.");
        assertTrue(projectDoc.hasRequiredEvidence());
    }

    @Test
    public void projectDocumentationWithoutManualReviewIsRejected() {
        AndroidDeviceSupportCatalog.Entry unreviewed = new AndroidDeviceSupportCatalog.Entry(
            "Example", "Model X", "device_x", "fingerprint", "REGION_A",
            AndroidDeviceSupportCatalog.Status.VERIFIED_ROOT_METHOD,
            "Unreviewed project documentation.", "https://example.com/project-doc",
            "2026-11-05", AndroidDeviceSupportCatalog.EvidenceSource.PROJECT_DOCUMENTATION,
            false, "");
        assertFalse(unreviewed.hasRequiredEvidence());
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
