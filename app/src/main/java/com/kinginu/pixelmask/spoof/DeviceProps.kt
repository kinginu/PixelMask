package com.kinginu.pixelmask.spoof

object DeviceProps {

    data class DeviceEntry(
        val deviceName: String,
        val props: Map<String, String>,
        val featureLevel: String,
    )

    data class FeatureLevel(
        val name: String,
        val flags: List<String>
    )

    private val allFeatureLevels = listOf(
        FeatureLevel("Pixel 2016", listOf(
            "com.google.android.apps.photos.NEXUS_PRELOAD",
            "com.google.android.apps.photos.nexus_preload",
            "com.google.android.feature.PIXEL_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2016_PRELOAD",
        )),
        FeatureLevel("Pixel 2017", listOf(
            "com.google.android.feature.PIXEL_2017_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2017_PRELOAD",
        )),
        FeatureLevel("Pixel 2018", listOf(
            "com.google.android.feature.PIXEL_2018_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2018_PRELOAD",
        )),
        FeatureLevel("Pixel 2019", listOf(
            "com.google.android.feature.PIXEL_2019_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2019_PRELOAD",
        )),
        FeatureLevel("Pixel 2020", listOf(
            "com.google.android.feature.PIXEL_2020_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2020_PRELOAD",
        )),
        FeatureLevel("Pixel 2021", listOf(
            "com.google.android.feature.PIXEL_2021_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2021_PRELOAD",
        )),
    )

    val allDevices = listOf(
        DeviceEntry(
            deviceName = "Pixel XL",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "marlin",
                "PRODUCT" to "marlin",
                "MODEL" to "Pixel XL",
                "FINGERPRINT" to "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
            ),
            featureLevel = "Pixel 2016",
        ),
        DeviceEntry(
            deviceName = "Pixel 2",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "walleye",
                "PRODUCT" to "walleye",
                "MODEL" to "Pixel 2",
                "FINGERPRINT" to "google/walleye/walleye:8.1.0/OPM1.171019.021/4565141:user/release-keys"
            ),
            featureLevel = "Pixel 2017",
        ),
        DeviceEntry(
            deviceName = "Pixel 3 XL",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "crosshatch",
                "PRODUCT" to "crosshatch",
                "MODEL" to "Pixel 3 XL",
                "FINGERPRINT" to "google/crosshatch/crosshatch:11/RQ3A.211001.001/7641976:user/release-keys"
            ),
            featureLevel = "Pixel 2018",
        ),
        DeviceEntry(
            deviceName = "Pixel 4 XL",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "coral",
                "PRODUCT" to "coral",
                "MODEL" to "Pixel 4 XL",
                "FINGERPRINT" to "google/coral/coral:12/SP1A.211105.002/7743617:user/release-keys"
            ),
            featureLevel = "Pixel 2019",
        ),
        DeviceEntry(
            deviceName = "Pixel 5",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "redfin",
                "PRODUCT" to "redfin",
                "MODEL" to "Pixel 5",
                "FINGERPRINT" to "google/redfin/redfin:12/SP1A.211105.003/7757856:user/release-keys"
            ),
            featureLevel = "Pixel 2020",
        ),
        DeviceEntry(
            deviceName = "Pixel 6 Pro",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "raven",
                "PRODUCT" to "raven",
                "MODEL" to "Pixel 6 Pro",
                "FINGERPRINT" to "google/raven/raven:12/SD1A.210817.036/7805805:user/release-keys"
            ),
            featureLevel = "Pixel 2021",
        ),
    )

    const val defaultDeviceName = "Pixel XL"

    fun getDeviceProps(deviceName: String?): DeviceEntry? =
        allDevices.find { it.deviceName == deviceName }

    fun getFeaturesUpToFromDeviceName(deviceName: String?): Set<String> {
        val entry = getDeviceProps(deviceName) ?: return emptySet()
        val targetIndex = allFeatureLevels.indexOfFirst { it.name == entry.featureLevel }
        if (targetIndex == -1) return emptySet()

        return allFeatureLevels
            .take(targetIndex + 1)
            .flatMap { it.flags }
            .toSet()
    }

    val allKnownFeatures: Set<String> =
        allFeatureLevels.flatMap { it.flags }.toSet()
}
