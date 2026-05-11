package com.kinginu.pixelmask

object Constants {

    const val PACKAGE_NAME_GOOGLE_PHOTOS = "com.google.android.apps.photos"
    const val PACKAGE_NAME_GMS = "com.google.android.gms"
    const val SHARED_PREF_FILE_NAME = "prefs"

    // IPC for hooked-process → module-host. Implicit broadcast bypasses Android 11+
    // package-visibility filtering that blocks ContentProvider access without <queries>.
    const val ACTION_AUTH_DATA_BROADCAST = "com.kinginu.pixelmask.AUTH_DATA"
    const val EXTRA_AUTH_DATA = "auth_data"

    const val REPO_URL_PUBLIC = "https://github.com/kinginu/PixelMask"
    private const val REPO_RAW_URL = "https://raw.githubusercontent.com/kinginu/PixelMask/main"

    const val ABOUT_URL = REPO_URL_PUBLIC
    const val UPDATE_INFO_URL = "$REPO_RAW_URL/update_info.json"
    const val LATEST_RELEASE_URL = "$REPO_URL_PUBLIC/releases/latest"

    const val FIELD_LATEST_VERSION_CODE = "latest_version_code"
    const val FIELD_DOWNLOAD_URL = "download_url"

    const val PREF_MODULE_ENABLED = "PREF_MODULE_ENABLED"
    const val PREF_DEVICE_TO_SPOOF = "PREF_DEVICE_TO_SPOOF"
    const val PREF_ENABLE_VERBOSE_LOGS = "PREF_ENABLE_VERBOSE_LOGS"
    const val PREF_CAPTURED_AUTH_DATA = "PREF_CAPTURED_AUTH_DATA"
}
