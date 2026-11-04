package com.kinginu.pixelmask.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kinginu.pixelmask.Constants

/**
 * Stores the HookReport that the hook inside Google Photos sends. Declared in the
 * manifest so reports arrive even when the Manager isn't running.
 *
 * Any app could send this broadcast, but the report is only shown to the user and
 * pasted into issue reports, so a forged one can't do harm.
 */
class HookStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.ACTION_HOOK_REPORT) return
        val json = intent.getStringExtra(Constants.EXTRA_HOOK_REPORT) ?: return
        val report = HookReport.fromJson(json) ?: return
        // Photos runs several processes; the main one is where the UI and the
        // storage-tier check live, so only its report is kept.
        if (report.processName != Constants.PACKAGE_NAME_GOOGLE_PHOTOS) return
        HookReport.save(context, json)
    }
}
