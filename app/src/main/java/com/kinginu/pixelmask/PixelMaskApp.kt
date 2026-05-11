package com.kinginu.pixelmask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

class PixelMaskApp : ModuleApplication() {

    private val authDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Constants.ACTION_AUTH_DATA_BROADCAST) return
            val authData = intent.getStringExtra(Constants.EXTRA_AUTH_DATA)
                ?.takeIf { it.isNotBlank() } ?: return
            context.getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, MODE_PRIVATE)
                .edit()
                .putString(Constants.PREF_CAPTURED_AUTH_DATA, authData)
                .commit()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            authDataReceiver,
            IntentFilter(Constants.ACTION_AUTH_DATA_BROADCAST),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
