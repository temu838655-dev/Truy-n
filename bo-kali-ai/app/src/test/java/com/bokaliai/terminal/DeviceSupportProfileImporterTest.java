package com.bokaliai.terminal;

import org.junit.Test;

import static org.junit.Assert.*;

public class DeviceSupportProfileImporterTest {
    private String validJson() {
        return "{\"schemaVersion\":1,\"profiles\":[{" +
            "\"manufacturer\":\"Example\",\"model\":\"Model X\"," +
            "\"codename\":\"device_x\",\"firmwareFingerprint\":\"fingerprint\"," +
            "\"regionOrSku\":\"REGION_A\",\"status\":\"VERIFIED_UNLOCK_SUPPORTED\"," +
            "\"summary\":\"OEM source metadata\",\"sourceUrl\":\"https://example.com/source\"," +
            "\"verifiedDate\":\"2026-11-05\",\"evidenceSource\":\"OEM_OFFICIAL\"," +
            "\"manuallyReviewed\":true,\"reviewerNote\":\"trusted claim\"}]}";
    }

    @Test public void importedProfileIsAlwaysUnverifiedEvenIfJsonClaimsReview() {
        DeviceSupportProfileImporter.ImportResult result =
            DeviceSupportProfileImporter.parse(validJson());
        assertTrue(result.isSuccess());
        assertEquals(1, result.getEntries().size());
        AndroidDeviceSupportCatalog.Entry entry = result.getEntries().get(0);
        assertEquals(AndroidDeviceSupportCatalog.Status.RESEARCH_REQUIRED, entry.status);
        assertFalse(entry.manuallyReviewed);
        assertFalse(entry.hasRequiredEvidence());
    }

    @Test public void rejectsMalformedJson() {
        DeviceSupportProfileImporter.ImportResult result =
            DeviceSupportProfileImporter.parse("{not-json");
        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test public void rejectsUnsupportedSchemaVersion() {
        DeviceSupportProfileImporter.ImportResult result =
            DeviceSupportProfileImporter.parse(validJson().replace("\"schemaVersion\":1",
                "\"schemaVersion\":2"));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("schema"));
    }

    @Test public void rejectsMissingRequiredIdentityFields() {
        DeviceSupportProfileImporter.ImportResult result =
            DeviceSupportProfileImporter.parse(validJson().replace("\"codename\":\"device_x\",", ""));
        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test public void rejectsUnknownStatusAndEvidenceEnums() {
        String badStatus = validJson().replace("VERIFIED_UNLOCK_SUPPORTED", "UNIVERSAL_ROOT");
        assertFalse(DeviceSupportProfileImporter.parse(badStatus).isSuccess());
        String badSource = validJson().replace("OEM_OFFICIAL", "RANDOM_BLOG");
        assertFalse(DeviceSupportProfileImporter.parse(badSource).isSuccess());
    }

    @Test public void malformedSourceAndDateRemainUnverified() {
        String badSource = validJson().replace("https://example.com/source", "http://example.com/source");
        DeviceSupportProfileImporter.ImportResult sourceResult =
            DeviceSupportProfileImporter.parse(badSource);
        assertTrue(sourceResult.isSuccess());
        assertFalse(sourceResult.getEntries().get(0).hasRequiredEvidence());
        String badDate = validJson().replace("2026-11-05", "2026-02-30");
        DeviceSupportProfileImporter.ImportResult dateResult =
            DeviceSupportProfileImporter.parse(badDate);
        assertTrue(dateResult.isSuccess());
        assertFalse(dateResult.getEntries().get(0).hasRequiredEvidence());
    }

    @Test public void doesNotImportExecutableCommandPayload() {
        String withCommand = validJson().replace("\"summary\":",
            "\"commands\":[\"fastboot flashing unlock\"],\"summary\":");
        DeviceSupportProfileImporter.ImportResult result =
            DeviceSupportProfileImporter.parse(withCommand);
        assertTrue(result.isSuccess());
        assertEquals(AndroidDeviceSupportCatalog.Status.RESEARCH_REQUIRED,
            result.getEntries().get(0).status);
    }

    @Test public void rejectsEmptyProfilesArray() {
        DeviceSupportProfileImporter.ImportResult result =
            DeviceSupportProfileImporter.parse("{\"schemaVersion\":1,\"profiles\":[]}");
        assertFalse(result.isSuccess());
    }
}
