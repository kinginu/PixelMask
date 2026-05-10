package com.kinginu.pixelmask.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.kinginu.pixelmask.BuildConfig
import com.kinginu.pixelmask.Constants
import com.kinginu.pixelmask.R
import com.kinginu.pixelmask.spoof.DeviceProps

object Utils {

    // Open the system "App info" screen for the given package. We can't programmatically
    // force-stop another app from a non-system process — every su / am force-stop dance from
    // here would silently fail — so we just hand the user off to Settings, where the *Force
    // stop* button is one tap away.
    fun openAppInfo(packageName: String, context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            // Only show the "tap force stop" hint after we successfully reached Settings;
            // otherwise it's a misleading instruction sitting on top of a no-op.
            Toast.makeText(context, R.string.tap_force_stop_in_app_info, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, R.string.failed_to_launch_package, Toast.LENGTH_SHORT).show()
        }
    }

    fun openApplication(packageName: String, context: Context) {
        // getLaunchIntentForPackage returns null when the package has no launcher activity
        // (e.g. Photos uninstalled or disabled). Surface that to the user instead of
        // silently doing nothing.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(context, R.string.failed_to_launch_package, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(launchIntent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.failed_to_launch_package, Toast.LENGTH_SHORT).show()
        }
    }

    // Build a GitHub issue URL with the right template + as much pre-filled context as we
    // can responsibly collect, then open it in the browser. GitHub issue forms accept a
    // `template=<file>.yml` query param and one query param per field id.
    fun openReportIssue(working: Boolean, context: Context) {
        val template = if (working) "report-working.yml" else "report-not-working.yml"

        val prefs = context.getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
        val spoofTarget = prefs.getString(Constants.PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
            ?: DeviceProps.defaultDeviceName
        val masterEnabled = prefs.getBoolean(Constants.PREF_MODULE_ENABLED, true)
        val verboseLogs   = prefs.getBoolean(Constants.PREF_ENABLE_VERBOSE_LOGS, false)

        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val diag = buildString {
            appendLine("PixelMask:           ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Real device:         $device")
            appendLine("Android:             $androidVersion")
            appendLine("Build fingerprint:   ${Build.FINGERPRINT}")
            appendLine("Spoof target:        $spoofTarget")
            appendLine("Master switch:       $masterEnabled")
            appendLine("Verbose logs:        $verboseLogs")
        }

        val uri = "${Constants.REPO_URL_PUBLIC}/issues/new".toUri().buildUpon()
            .appendQueryParameter("template", template)
            .appendQueryParameter("module-version", BuildConfig.VERSION_NAME)
            .appendQueryParameter("android-version", androidVersion)
            .appendQueryParameter("real-device", device)
            .appendQueryParameter("spoof-target", spoofTarget)
            .appendQueryParameter("diag-context", diag)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.failed_to_launch_package, Toast.LENGTH_SHORT).show()
        }
    }
}
