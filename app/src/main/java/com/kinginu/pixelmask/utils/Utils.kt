package com.kinginu.pixelmask.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.kinginu.pixelmask.R
import java.io.BufferedWriter
import java.io.OutputStreamWriter

object Utils {

    fun forceStopPackage(packageName: String, context: Context) {
        try {
            Toast.makeText(context, R.string.killing_please_wait, Toast.LENGTH_SHORT).show()
            Runtime.getRuntime().exec("su").apply {
                BufferedWriter(OutputStreamWriter(this.outputStream)).run {
                    write("am force-stop $packageName\n")
                    write("exit\n")
                    flush()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, R.string.failed_to_stop_package, Toast.LENGTH_SHORT).show()
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", packageName, null)
            }
            context.startActivity(intent)
        }
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
