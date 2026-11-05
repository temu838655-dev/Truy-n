package com.bokaliai.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AndroidDeviceCapabilityAssessmentTest {
    private AdbTargetProfileParser.DeviceProfile profile(String properties) {
        return AdbTargetProfileParser.parse(
            "=== TARGET SERIAL: test-device ===\n" +
            properties +
            "--- END TARGET PROFILE: READ ONLY ---\n"
        ).get(0);
    }

    @Test
    public void nullProfileKeepsAllCapabilitiesUnknown() {
        AndroidDeviceCapabilityAssessment.Result result =
            AndroidDeviceCapabilityAssessment.assess(null);
        assertEquals(AndroidDeviceCapabilityAssessment.State.UNKNOWN, result.rootStatus);
        assertEquals(AndroidDeviceCapabilityAssessment.State.UNKNOWN, result.bootloaderStatus);
        assertEquals(AndroidDeviceCapabilityAssessment.State.UNKNOWN, result.kernelFlashStatus);
    }

    @Test
    public void uidZeroIsOnlyAProvisionalRootSignal() {
        AndroidDeviceCapabilityAssessment.Result result =
            AndroidDeviceCapabilityAssessment.assess(profile("ANDROID_UID=uid=0(root)\n"));
        assertEquals(AndroidDeviceCapabilityAssessment.State.AVAILABLE, result.rootStatus);
    }

    @Test
    public void normalAdbShellDoesNotProveDeviceIsNotRooted() {
        AndroidDeviceCapabilityAssessment.Result result =
            AndroidDeviceCapabilityAssessment.assess(profile("ANDROID_UID=uid=2000(shell)\n"));
        assertEquals(AndroidDeviceCapabilityAssessment.State.UNKNOWN, result.rootStatus);
    }

    @Test
    public void lockedBootloaderIsNotTreatedAsUnlockImpossible() {
        AndroidDeviceCapabilityAssessment.Result result =
            AndroidDeviceCapabilityAssessment.assess(profile(
                "ro.boot.flash.locked=1\n"
            ));
        assertEquals(AndroidDeviceCapabilityAssessment.State.BLOCKED, result.bootloaderStatus);
        assertEquals(AndroidDeviceCapabilityAssessment.State.BLOCKED, result.kernelFlashStatus);
    }

    @Test
    public void unlockedBootloaderStillRequiresVerifiedKernelImage() {
        AndroidDeviceCapabilityAssessment.Result result =
            AndroidDeviceCapabilityAssessment.assess(profile(
                "ro.boot.vbmeta.device_state=unlocked\n"
            ));
        assertEquals(AndroidDeviceCapabilityAssessment.State.AVAILABLE, result.bootloaderStatus);
        assertEquals(AndroidDeviceCapabilityAssessment.State.UNKNOWN, result.kernelFlashStatus);
    }
}
