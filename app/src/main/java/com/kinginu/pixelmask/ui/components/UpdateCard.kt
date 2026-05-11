package com.kinginu.pixelmask.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kinginu.pixelmask.R

// Drives the UpdateCard's visual state. The previous version of the card
// stayed on "Module Up to Date / Tap to check for updates" both while the
// HTTP check was running and after it had finished, regardless of whether
// the check succeeded — so users couldn't tell "no update available" apart
// from "the request silently errored out". Now the card distinguishes all
// four cases explicitly.
sealed class UpdateCheckState {
    object Checking : UpdateCheckState()
    data class UpToDate(val checkedAt: Long) : UpdateCheckState()
    data class UpdateAvailable(val url: String, val checkedAt: Long) : UpdateCheckState()
    data class Failed(val checkedAt: Long) : UpdateCheckState()
}

@Composable
fun UpdateCard(
    state: UpdateCheckState,
    onOpenLink: (String) -> Unit,
    onCheckForUpdate: () -> Unit,
) {
    val containerColor = when (state) {
        is UpdateCheckState.UpdateAvailable -> MaterialTheme.colorScheme.tertiaryContainer
        is UpdateCheckState.Failed -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when (state) {
        is UpdateCheckState.UpdateAvailable -> MaterialTheme.colorScheme.onTertiaryContainer
        is UpdateCheckState.Failed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    // While a check is in flight, eat taps — the underlying coroutine is
    // already running and re-entrancy would just stack another in-flight
    // request behind it.
    val isClickable = state !is UpdateCheckState.Checking
    val onClick: () -> Unit = {
        when (state) {
            is UpdateCheckState.UpdateAvailable -> onOpenLink(state.url)
            else -> onCheckForUpdate()
        }
    }

    val title = when (state) {
        UpdateCheckState.Checking -> stringResource(R.string.update_checking)
        is UpdateCheckState.UpToDate -> stringResource(R.string.module_up_to_date)
        is UpdateCheckState.UpdateAvailable -> stringResource(R.string.new_version_available)
        is UpdateCheckState.Failed -> stringResource(R.string.update_check_failed)
    }
    val subtitle = when (state) {
        UpdateCheckState.Checking -> stringResource(R.string.update_please_wait)
        is UpdateCheckState.UpToDate -> stringResource(
            R.string.update_checked_at,
            formatRelativeTime(state.checkedAt)
        )
        is UpdateCheckState.UpdateAvailable -> stringResource(R.string.tap_to_download)
        is UpdateCheckState.Failed -> stringResource(R.string.update_tap_to_retry)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isClickable) it.clickable(onClick = onClick) else it }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state is UpdateCheckState.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = contentColor,
                    strokeWidth = 3.dp,
                )
            } else {
                val icon = when (state) {
                    is UpdateCheckState.UpdateAvailable -> Icons.Default.Update
                    is UpdateCheckState.Failed -> Icons.Default.Warning
                    else -> Icons.Default.CheckCircle
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

// Formats a past-millis timestamp as "just now" / "5 minutes ago" / "2 hours
// ago" using the OS-locale relative strings (so JP users see "5分前" etc.).
// Not live-updating — recomputed on each composition. In practice the user
// is on the Home tab for seconds, not minutes, so the staleness window
// doesn't matter; the next time they come back the Home screen recomposes
// and the string refreshes.
private fun formatRelativeTime(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
