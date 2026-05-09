package com.kinginu.pixelmask.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.kinginu.pixelmask.R

object Utils {

    // Open the system "App info" screen for the given package. We can't programmatically
    // force-stop another app from a non-system process — every su / am force-stop dance from
    // here would silently fail — so we just hand the user off to Settings, where the *Force
    // stop* button is one tap away.
    fun openAppInfo(packageName: String, context: Context) {
        Toast.makeText(context, R.string.tap_force_stop_in_app_info, Toast.LENGTH_LONG).show()
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openApplication(packageName: String, context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) context.startActivity(launchIntent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.failed_to_launch_package, Toast.LENGTH_SHORT).show()
        }
    }
}
