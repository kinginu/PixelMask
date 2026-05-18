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
    const val FIELD_DOWNLOAD_URL = "download_url"

    const val PREF_MODULE_ENABLED = "PREF_MODULE_ENABLED"
    const val PREF_DEVICE_TO_SPOOF = "PREF_DEVICE_TO_SPOOF"
    const val PREF_ENABLE_VERBOSE_LOGS = "PREF_ENABLE_VERBOSE_LOGS"

    // Signal Google Photos to self-terminate so the LSPosed hook re-runs with
    // the current spoof config on respawn. Used instead of the system "Force
    // stop" button, which is inert on some OEM skins.
    const val ACTION_RESTART_PHOTOS = "com.kinginu.pixelmask.action.RESTART_PHOTOS"

    // The Photos-side receiver sends this back right before killing itself, so
    // the Manager knows the hook is present and the kill is about to happen.
    // No ACK within the timeout means either Photos wasn't running, or it was
    // launched before LSPosed had a chance to inject our hook (e.g. right after
    // first install/enable) — in which case the user must reboot once.
    const val ACTION_RESTART_ACK = "com.kinginu.pixelmask.action.RESTART_ACK"
}
