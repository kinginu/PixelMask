package com.kinginu.pixelmask.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kinginu.pixelmask.Constants
import com.kinginu.pixelmask.R
import com.kinginu.pixelmask.spoof.DeviceProps
import com.kinginu.pixelmask.ui.components.ActionCard
import com.kinginu.pixelmask.ui.components.ActionCardItemData
import com.kinginu.pixelmask.ui.components.ConfigurationCard
import com.kinginu.pixelmask.ui.components.DeviceSelectionItem
import com.kinginu.pixelmask.ui.components.DeviceSelectionSheet
import com.kinginu.pixelmask.ui.components.MasterSwitchCard
import com.kinginu.pixelmask.ui.components.SettingSwitchItem
import com.kinginu.pixelmask.ui.components.SoftDivider
import com.kinginu.pixelmask.utils.ModuleStatus
import com.kinginu.pixelmask.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(onSettingChanged: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pref = remember {
        context.getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    }

    val isModuleActive = remember { ModuleStatus.isModuleActive() }

    var showDeviceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    fun notifyChangedIfActive() {
        if (isModuleActive) onSettingChanged()
    }

    fun saveBoolean(key: String, value: Boolean) {
        coroutineScope.launch(Dispatchers.IO) {
            pref.edit().putBoolean(key, value).commit()
        }
        notifyChangedIfActive()
    }

    fun saveString(key: String, value: String) {
        coroutineScope.launch(Dispatchers.IO) {
            pref.edit().putString(key, value).commit()
        }
        notifyChangedIfActive()
    }

    var moduleEnabled by remember {
        mutableStateOf(pref.getBoolean(Constants.PREF_MODULE_ENABLED, true))
    }
    var deviceToSpoof by remember {
        // If the saved name doesn't match any current device entry (e.g. an old name like
        // "Pixel XL" that we removed in a release), fall back to the default for display.
        // We don't write the corrected value back here — the hook entry has the same
        // fallback, and the next user pick will overwrite the stale pref anyway.
        val saved = pref.getString(Constants.PREF_DEVICE_TO_SPOOF, null)
        val validated = saved?.takeIf { name -> DeviceProps.allDevices.any { it.deviceName == name } }
            ?: DeviceProps.defaultDeviceName
        mutableStateOf(validated)
    }
    var verboseLogs by remember {
        mutableStateOf(pref.getBoolean(Constants.PREF_ENABLE_VERBOSE_LOGS, false))
    }

    if (showDeviceSheet) {
        DeviceSelectionSheet(
            sheetState = sheetState,
            deviceList = DeviceProps.allDevices,
            currentDevice = deviceToSpoof,
            onDeviceSelected = { device ->
                deviceToSpoof = device
                saveString(Constants.PREF_DEVICE_TO_SPOOF, device)
                showDeviceSheet = false
            },
            onDismiss = { showDeviceSheet = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MasterSwitchCard(
            checked = moduleEnabled,
            onCheckedChange = { newState ->
                moduleEnabled = newState
                saveBoolean(Constants.PREF_MODULE_ENABLED, newState)
            }
        )

        ConfigurationCard(isEnabled = moduleEnabled) {
            DeviceSelectionItem(
                currentDevice = deviceToSpoof,
                enabled = moduleEnabled,
                onClick = { showDeviceSheet = true }
            )

            SoftDivider()

            SettingSwitchItem(
                title = stringResource(R.string.enable_verbose_logs),
                checked = verboseLogs,
                enabled = moduleEnabled,
                onCheckedChange = {
                    verboseLogs = it
                    saveBoolean(Constants.PREF_ENABLE_VERBOSE_LOGS, it)
                }
            )
        }

        Text(
            text = stringResource(R.string.actions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp)
        )

        ActionCard(
            items = listOf(
                ActionCardItemData(
                    title = stringResource(R.string.restart_google_photos),
                    icon = Icons.Filled.RestartAlt,
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        Utils.restartGooglePhotos(context)
                    }
                ),
                ActionCardItemData(
                    title = stringResource(R.string.open_google_photos),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        Utils.openApplication(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, context)
                    }
                ),
            )
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
