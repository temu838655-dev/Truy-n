package com.bokaliai.terminal;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AdbTargetProfileParserTest {
    @Test
    public void parsesTwoTargetsWithoutMixingTheirProperties() {
        String output =
            "=== TARGET SERIAL: 192.0.2.10:5555 ===\n" +
            "--- DEVICE IDENTITY ---\n" +
            "ro.product.manufacturer=Acme\n" +
            "ro.product.model=Phone A\n" +
            "ro.product.device=phone_a\n" +
            "ro.build.fingerprint=acme/phone_a:14/BUILD_A/1:user/release-keys\n" +
            "ro.boot.hardware.sku=SKU_A\n" +
            "ro.build.version.release=14\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n" +
            "=== TARGET SERIAL: emulator-5554 ===\n" +
            "--- DEVICE IDENTITY ---\n" +
            "ro.product.manufacturer=Example\n" +
            "ro.product.model=Emulator B\n" +
            "ro.product.device=emu_b\n" +
            "ro.build.fingerprint=example/emu_b:15/BUILD_B/2:user/release-keys\n" +
            "ro.boot.hardware.sku=SKU_B\n" +
            "ro.build.version.release=15\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n";

        List<AdbTargetProfileParser.DeviceProfile> profiles =
            AdbTargetProfileParser.parse(output);

        assertEquals(2, profiles.size());
        assertEquals("192.0.2.10:5555", profiles.get(0).getSerial());
        assertEquals("Phone A", profiles.get(0).get("ro.product.model"));
        assertEquals("14", profiles.get(0).get("ro.build.version.release"));
        assertEquals("Emulator B", profiles.get(1).get("ro.product.model"));
        assertEquals("15", profiles.get(1).get("ro.build.version.release"));
        assertTrue(profiles.get(0).hasMinimumIdentity());
        assertTrue(profiles.get(1).hasMinimumIdentity());
    }

    @Test
    public void incompleteProfileIsNotMarkedComplete() {
        String output =
            "=== TARGET SERIAL: device-01 ===\n" +
            "ro.product.model=Partial Phone\n";
        List<AdbTargetProfileParser.DeviceProfile> profiles =
            AdbTargetProfileParser.parse(output);
        assertEquals(1, profiles.size());
        assertFalse(profiles.get(0).isComplete());
        assertFalse(profiles.get(0).hasMinimumIdentity());
    }

    @Test
    public void exactCatalogIdentityRequiresManufacturerFingerprintAndRegionSku() {
        String output =
            "=== TARGET SERIAL: device-identity ===\n" +
            "ro.product.model=Phone Identity\n" +
            "ro.product.device=phone_identity\n" +
            "ro.build.fingerprint=maker/phone_identity:14/BUILD/1:user/release-keys\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n";
        AdbTargetProfileParser.DeviceProfile profile =
            AdbTargetProfileParser.parse(output).get(0);
        assertFalse(profile.hasMinimumIdentity());
        assertEquals(2, profile.getMissingMinimumIdentityFields().size());
        assertTrue(profile.getMissingMinimumIdentityFields().contains("hãng (ro.product.manufacturer)"));
        assertTrue(profile.getMissingMinimumIdentityFields().contains("SKU/region/locale"));
    }

    @Test
    public void completeExactIdentityIsEligibleForCatalogLookup() {
        String output =
            "=== TARGET SERIAL: device-exact ===\n" +
            "ro.product.manufacturer=Maker\n" +
            "ro.product.model=Phone Exact\n" +
            "ro.product.device=phone_exact\n" +
            "ro.build.fingerprint=maker/phone_exact:14/BUILD/1:user/release-keys\n" +
            "ro.boot.hardware.sku=SKU_EXACT\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n";
        AdbTargetProfileParser.DeviceProfile profile =
            AdbTargetProfileParser.parse(output).get(0);
        assertTrue(profile.hasMinimumIdentity());
        assertTrue(profile.getMissingMinimumIdentityFields().isEmpty());
    }

    @Test
    public void ignoresMalformedSerialAndUnrelatedHostProperties() {
        String output =
            "ro.product.model=HOST SHOULD NOT BE USED\n" +
            "=== TARGET SERIAL: bad serial ===\n" +
            "ro.product.model=Bad\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n";
        assertTrue(AdbTargetProfileParser.parse(output).isEmpty());
    }


    @Test
    public void keepsSkuRegionAndLocaleDistinctInCatalogIdentity() {
        String output =
            "=== TARGET SERIAL: device-03 ===\n" +
            "ro.product.model=Phone C\n" +
            "ro.product.device=phone_c\n" +
            "ro.boot.hardware.sku=SKU_A\n" +
            "ro.boot.product.hardware.sku=PRODUCT_SKU_B\n" +
            "ro.boot.region=REGION_C\n" +
            "ro.product.locale=en-US\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n";
        AdbTargetProfileParser.DeviceProfile profile =
            AdbTargetProfileParser.parse(output).get(0);
        assertEquals(
            "ro.boot.hardware.sku=SKU_A|ro.boot.product.hardware.sku=PRODUCT_SKU_B|" +
            "ro.boot.region=REGION_C|ro.product.locale=en-US",
            profile.getCatalogRegionSkuKey());
    }

    @Test
    public void missingSkuDoesNotFallBackToRegionOrLocaleAsSku() {
        String output =
            "=== TARGET SERIAL: device-04 ===\n" +
            "ro.boot.region=REGION_D\n" +
            "ro.product.locale=fr-FR\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n";
        AdbTargetProfileParser.DeviceProfile profile =
            AdbTargetProfileParser.parse(output).get(0);
        assertEquals("ro.boot.region=REGION_D|ro.product.locale=fr-FR",
            profile.getCatalogRegionSkuKey());
        assertFalse(profile.getCatalogRegionSkuKey().contains("ro.boot.hardware.sku="));
    }

    @Test
    public void ignoresNonAndroidPropertiesAndEmptyValues() {
        String output =
            "=== TARGET SERIAL: device-02 ===\n" +
            "ro.product.model=\n" +
            "ANDROID_UID=u0_a123\n" +
            "ro.product.device=phone_c\n" +
            "--- END TARGET PROFILE: READ ONLY ---\n";
        AdbTargetProfileParser.DeviceProfile profile =
            AdbTargetProfileParser.parse(output).get(0);
        assertNull(profile.get("ro.product.model"));
        assertNull(profile.get("ANDROID_UID"));
        assertEquals("phone_c", profile.get("ro.product.device"));
        assertFalse(profile.hasMinimumIdentity());
    }
    @Test
    public void completeLookingIdentityWithoutEndMarkerIsRejected() {
        String output =
            "=== TARGET SERIAL: device-truncated ===\\n" +
            "ro.product.manufacturer=Maker\\n" +
            "ro.product.model=Phone Exact\\n" +
            "ro.product.device=phone_exact\\n" +
            "ro.build.fingerprint=maker/phone_exact:14/BUILD/1:user/release-keys\\n" +
            "ro.boot.hardware.sku=SKU_EXACT\\n";
        AdbTargetProfileParser.DeviceProfile profile =
            AdbTargetProfileParser.parse(output).get(0);
        assertFalse(profile.isComplete());
        assertFalse(profile.hasMinimumIdentity());
        assertTrue(profile.getMissingMinimumIdentityFields().contains("dấu kết thúc hồ sơ ADB"));
    }

    @Test
    public void missingIdentityFieldsCannotBeModifiedByCaller() {
        String output =
            "=== TARGET SERIAL: device-immutable ===\\n" +
            "ro.product.model=Partial Phone\\n";
        AdbTargetProfileParser.DeviceProfile profile =
            AdbTargetProfileParser.parse(output).get(0);
        try {
            profile.getMissingMinimumIdentityFields().clear();
            fail("Missing-field report must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertFalse(profile.hasMinimumIdentity());
        }
    }

}
