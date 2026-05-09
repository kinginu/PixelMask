package com.kinginu.pixelmask.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

enum class ModuleState { ACTIVE, DISABLED, NOT_LOADED }

@Composable
fun StatusCard(state: ModuleState) {
    val containerColor = when (state) {
        ModuleState.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        ModuleState.DISABLED -> MaterialTheme.colorScheme.tertiaryContainer
        ModuleState.NOT_LOADED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (state) {
        ModuleState.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        ModuleState.DISABLED -> MaterialTheme.colorScheme.onTertiaryContainer
        ModuleState.NOT_LOADED -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = if (state == ModuleState.ACTIVE) Icons.Default.CheckCircle else Icons.Default.Warning
    val titleRes = when (state) {
        ModuleState.ACTIVE -> R.string.module_active
        ModuleState.DISABLED -> R.string.module_disabled
        ModuleState.NOT_LOADED -> R.string.module_not_active
    }
    val appName = stringResource(R.string.app_name)
    val subtitle: String? = when (state) {
        ModuleState.ACTIVE -> null
        ModuleState.DISABLED -> stringResource(R.string.module_disabled_text)
        ModuleState.NOT_LOADED -> stringResource(R.string.module_not_active_text, appName)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(titleRes),
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}
