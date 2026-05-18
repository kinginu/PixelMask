package com.kinginu.pixelmask.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.kinginu.pixelmask.BuildConfig
import com.kinginu.pixelmask.Constants
import com.kinginu.pixelmask.R
import com.kinginu.pixelmask.spoof.DeviceProps

object Utils {

    // ACK is fast in practice (~200 ms in tests). 2.5 s is the budget we give the
    // hook to respond before assuming it isn't installed in the running Photos
    // process — usually because Photos was launched before LSPosed could inject.
    private const val ACK_TIMEOUT_MS = 2_500L

    // After ACK arrives the Photos process is about to die; give it a moment to
    // actually tear down before we ask Android to start a fresh one, otherwise
    // the launch can race the kill and Android brings the dying activity to the
    // front instead of forking a new process with our hook applied.
    private const val RELAUNCH_DELAY_MS = 800L

    // Ask Google Photos to self-terminate via a broadcast caught by our in-process
    // LSPosed hook. We do it this way instead of asking the user to tap the system
    // "Force stop" button because that button is inert on some OEM skins (the user
    // reported this on Nothing OS).
    //
    // Flow: send kill broadcast → wait for ACK from the hook → relaunch Photos so
    // the user sees a clean restart with the updated spoof config. If no ACK
    // arrives within the timeout the hook isn't installed in the running process
    // (or Photos isn't running at all), and we surface a hint telling the user to
    // reboot once.
    fun restartGooglePhotos(context: Context) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        var done = false

        lateinit var receiver: BroadcastReceiver
        val timeout = Runnable {
            if (done) return@Runnable
            done = true
            runCatching { appContext.unregisterReceiver(receiver) }
            Toast.makeText(appContext, R.string.restart_no_response, Toast.LENGTH_LONG).show()
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != Constants.ACTION_RESTART_ACK) return
                if (done) return
                done = true
                handler.removeCallbacks(timeout)
                runCatching { appContext.unregisterReceiver(this) }
                Toast.makeText(appContext, R.string.restart_in_progress, Toast.LENGTH_LONG).show()
                handler.postDelayed({ launchPhotos(appContext) }, RELAUNCH_DELAY_MS)
            }
        }

        val filter = IntentFilter(Constants.ACTION_RESTART_ACK)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, /* RECEIVER_EXPORTED */ 0x2)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        }

        handler.postDelayed(timeout, ACK_TIMEOUT_MS)

        val kill = Intent(Constants.ACTION_RESTART_PHOTOS).apply {
            // Scope the broadcast to Photos so we don't leak the action to any other
            // app that might register the same filter.
            `package` = Constants.PACKAGE_NAME_GOOGLE_PHOTOS
        }
        appContext.sendBroadcast(kill)
    }

    private fun launchPhotos(context: Context) {
        val launch = context.packageManager
            .getLaunchIntentForPackage(Constants.PACKAGE_NAME_GOOGLE_PHOTOS)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        runCatching { context.startActivity(launch) }
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

        // Photos version is the single most useful field for triage: a regression
        // from a Photos update is a common cause of "not working" reports, and
        // users routinely forget to mention which build they're on. Permitted by
        // the <queries> entry for com.google.android.apps.photos in the manifest.
        val photosVersion = runCatching {
            val pi = context.packageManager.getPackageInfo(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, 0)
            "${pi.versionName} (code ${PackageInfoCompat.getLongVersionCode(pi)})"
        }.getOrDefault("not installed")

        val diag = buildString {
            appendLine("PixelMask:           ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Real device:         $device")
            appendLine("Android:             $androidVersion")
            appendLine("Photos:              $photosVersion")
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
