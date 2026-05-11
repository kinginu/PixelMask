package com.kinginu.pixelmask.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kinginu.pixelmask.Constants
import com.kinginu.pixelmask.R
import com.kinginu.pixelmask.ui.components.InfoCard
import com.kinginu.pixelmask.ui.components.InfoCardItemData
import com.kinginu.pixelmask.ui.components.LinkCard
import com.kinginu.pixelmask.ui.components.LinkCardItemData
import com.kinginu.pixelmask.ui.components.ModuleState
import com.kinginu.pixelmask.ui.components.StatusCard
import com.kinginu.pixelmask.ui.components.UpdateCard
import com.kinginu.pixelmask.ui.components.UpdateCheckState
import com.kinginu.pixelmask.utils.ModuleStatus
import com.kinginu.pixelmask.utils.Utils

@Composable
fun HomeScreen(
    updateState: UpdateCheckState,
    appVersion: String,
    onOpenLink: (String) -> Unit,
    onCheckForUpdate: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    }
    // Track PREF_MODULE_ENABLED reactively so toggling the master switch in Settings
    // immediately re-renders the StatusCard when the user navigates back here.
    // The initial value is remembered separately so produceState's argument doesn't
    // re-read the pref on every recomposition (which would discard the value but still
    // hit SharedPreferences each frame).
    val initialEnabled = remember(prefs) { prefs.getBoolean(Constants.PREF_MODULE_ENABLED, true) }
    val moduleEnabled by produceState(initialValue = initialEnabled, prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Constants.PREF_MODULE_ENABLED) {
                value = prefs.getBoolean(Constants.PREF_MODULE_ENABLED, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val moduleState = when {
        !ModuleStatus.isModuleActive() -> ModuleState.NOT_LOADED
        !moduleEnabled -> ModuleState.DISABLED
        else -> ModuleState.ACTIVE
    }
    val appName = stringResource(R.string.app_name)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(state = moduleState)

        UpdateCard(
            state = updateState,
            onOpenLink = onOpenLink,
            onCheckForUpdate = onCheckForUpdate
        )

        InfoCard(
            items = listOf(
                InfoCardItemData(stringResource(R.string.android_version), Build.VERSION.RELEASE),
                InfoCardItemData(stringResource(R.string.device_model), Build.MODEL),
                InfoCardItemData(stringResource(R.string.app_version), appVersion),
            )
        )

        Text(
            text = stringResource(R.string.info_and_links),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp)
        )

        LinkCard(
            items = listOf(
                LinkCardItemData(
                    title = stringResource(R.string.about_app, appName),
                    icon = Icons.Default.Info,
                    onClick = { onOpenLink(Constants.ABOUT_URL) }
                ),
                LinkCardItemData(
                    title = stringResource(R.string.report_working),
                    icon = Icons.Default.ThumbUp,
                    onClick = { Utils.openReportIssue(working = true, context = context) }
                ),
                LinkCardItemData(
                    title = stringResource(R.string.report_not_working),
                    icon = Icons.Default.BugReport,
                    onClick = { Utils.openReportIssue(working = false, context = context) }
                ),
            )
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
