package com.kinginu.pixelmask

object Constants {

    const val PACKAGE_NAME_GOOGLE_PHOTOS = "com.google.android.apps.photos"
    const val SHARED_PREF_FILE_NAME = "prefs"

    const val REPO_URL_PUBLIC = "https://github.com/kinginu/PixelMask"
    private const val REPO_RAW_URL = "https://raw.githubusercontent.com/kinginu/PixelMask/main"

    const val ABOUT_URL = REPO_URL_PUBLIC
    const val UPDATE_INFO_URL = "$REPO_RAW_URL/update_info.json"
    const val LATEST_RELEASE_URL = "$REPO_URL_PUBLIC/releases/latest"

    const val FIELD_LATEST_VERSION_CODE = "latest_version_code"

    const val PREF_MODULE_ENABLED = "PREF_MODULE_ENABLED"
    const val PREF_DEVICE_TO_SPOOF = "PREF_DEVICE_TO_SPOOF"
    const val PREF_STRICTLY_CHECK_GOOGLE_PHOTOS = "PREF_STRICTLY_CHECK_GOOGLE_PHOTOS"
    const val PREF_ENABLE_VERBOSE_LOGS = "PREF_ENABLE_VERBOSE_LOGS"
}
