package com.kinginu.pixelmask.spoof

object DeviceProps {

    // Feature flags Photos checks via PackageManager.hasSystemFeature(). Listed in
    // chronological generations: when a device entry below points at e.g. PIXEL_2020,
    // we tell Photos every flag from PIXEL_2016 up through PIXEL_2020 is present and
    // every later one is absent. Order matters — the enum's ordinal drives that range.
    enum class FeatureLevel(val flags: List<String>) {
        PIXEL_2016(listOf(
            "com.google.android.apps.photos.NEXUS_PRELOAD",
            "com.google.android.apps.photos.nexus_preload",
            "com.google.android.feature.PIXEL_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2016_PRELOAD",
        )),
        PIXEL_2017(listOf(
            "com.google.android.feature.PIXEL_2017_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2017_PRELOAD",
        )),
        PIXEL_2018(listOf(
            "com.google.android.feature.PIXEL_2018_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2018_PRELOAD",
        )),
        PIXEL_2019(listOf(
            "com.google.android.feature.PIXEL_2019_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2019_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2019_PRELOAD",
        )),
        PIXEL_2020(listOf(
            "com.google.android.feature.PIXEL_2020_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2020_EXPERIENCE",
        )),
        PIXEL_2021(listOf(
            "com.google.android.feature.PIXEL_2021_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2021_EXPERIENCE",
        )),
        PIXEL_2022(listOf(
            "com.google.android.feature.PIXEL_2022_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2022_EXPERIENCE",
        )),
        PIXEL_2023(listOf(
            "com.google.android.feature.PIXEL_2023_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2023_EXPERIENCE",
        )),
        PIXEL_2024(listOf(
            "com.google.android.feature.PIXEL_2024_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2024_EXPERIENCE",
        )),
        PIXEL_2025(listOf(
            "com.google.android.feature.PIXEL_2025_MIDYEAR_EXPERIENCE",
            "com.google.android.feature.PIXEL_2025_EXPERIENCE",
        )),
    }

    data class DeviceEntry(
        val deviceName: String,
        val summary: String,
        val props: Map<String, String>,
        val featureLevel: FeatureLevel,
    )

    val allDevices = listOf(
        DeviceEntry(
            deviceName = "Pixel",
            summary = "Unlimited Original-quality backup (lifetime)",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "sailfish",
                "PRODUCT" to "sailfish",
                "MODEL" to "Pixel",
                "FINGERPRINT" to "google/sailfish/sailfish:10/QP1A.191005.007.A3/5972272:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2016,
        ),
        DeviceEntry(
            deviceName = "Pixel 2",
            summary = "Unlimited Storage Saver backup",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "walleye",
                "PRODUCT" to "walleye",
                "MODEL" to "Pixel 2",
                "FINGERPRINT" to "google/walleye/walleye:8.1.0/OPM1.171019.021/4565141:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2017,
        ),
        DeviceEntry(
            deviceName = "Pixel 3 XL",
            summary = "Unlimited Storage Saver backup",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "crosshatch",
                "PRODUCT" to "crosshatch",
                "MODEL" to "Pixel 3 XL",
                "FINGERPRINT" to "google/crosshatch/crosshatch:11/RQ3A.211001.001/7641976:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2018,
        ),
        DeviceEntry(
            deviceName = "Pixel 4 XL",
            summary = "Unlimited Storage Saver backup",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "coral",
                "PRODUCT" to "coral",
                "MODEL" to "Pixel 4 XL",
                "FINGERPRINT" to "google/coral/coral:12/SP1A.211105.002/7743617:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2019,
        ),
        DeviceEntry(
            deviceName = "Pixel 5",
            summary = "Unlimited Storage Saver backup",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "redfin",
                "PRODUCT" to "redfin",
                "MODEL" to "Pixel 5",
                "FINGERPRINT" to "google/redfin/redfin:12/SP1A.211105.003/7757856:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2020,
        ),
        DeviceEntry(
            deviceName = "Pixel 6 Pro",
            summary = "No notable Photos perks remain",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "raven",
                "PRODUCT" to "raven",
                "MODEL" to "Pixel 6 Pro",
                "FINGERPRINT" to "google/raven/raven:12/SD1A.210817.036/7805805:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2021,
        ),
        DeviceEntry(
            deviceName = "Pixel 7 Pro",
            summary = "No notable Photos perks remain",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "cheetah",
                "PRODUCT" to "cheetah",
                "MODEL" to "Pixel 7 Pro",
                "FINGERPRINT" to "google/cheetah/cheetah:13/TQ2A.230305.008.C1/9619669:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2022,
        ),
        DeviceEntry(
            deviceName = "Pixel 8 Pro",
            summary = "Video Boost, Night Sight Video",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "husky",
                "PRODUCT" to "husky",
                "MODEL" to "Pixel 8 Pro",
                "FINGERPRINT" to "google/husky/husky:14/UD1A.230803.041/10808477:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2023,
        ),
        DeviceEntry(
            deviceName = "Pixel 9 Pro XL",
            summary = "Add Me, Reimagine, unlimited Magic Editor",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "komodo",
                "PRODUCT" to "komodo",
                "MODEL" to "Pixel 9 Pro XL",
                "FINGERPRINT" to "google/komodo/komodo:14/AD1A.240530.047.F1/12150327:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2024,
        ),
        DeviceEntry(
            deviceName = "Pixel 10 Pro XL",
            summary = "Latest Pixel-first AI features",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "mustang",
                "PRODUCT" to "mustang",
                "MODEL" to "Pixel 10 Pro XL",
                "FINGERPRINT" to "google/mustang/mustang:16/BP1A.250805.005/14000000:user/release-keys"
            ),
            featureLevel = FeatureLevel.PIXEL_2025,
        ),
    )

    const val defaultDeviceName = "Pixel"

    fun getDeviceProps(deviceName: String?): DeviceEntry? =
        allDevices.find { it.deviceName == deviceName }

    fun getFeaturesUpToFromDeviceName(deviceName: String?): Set<String> {
        val entry = getDeviceProps(deviceName) ?: return emptySet()
        return FeatureLevel.entries
            .take(entry.featureLevel.ordinal + 1)
            .flatMap { it.flags }
            .toSet()
    }

    val allKnownFeatures: Set<String> =
        FeatureLevel.entries.flatMap { it.flags }.toSet()
}
