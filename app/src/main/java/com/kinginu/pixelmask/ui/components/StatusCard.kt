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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ModuleState {
    /** Framework isn't loading PixelMask at all. */
    NOT_LOADED,
    /** Master switch is off. */
    DISABLED,
    /** Hook reported from Photos and every step succeeded. */
    PHOTOS_OK,
    /** Hook reported from Photos but something failed. */
    PHOTOS_ERROR,
    /** Photos hasn't reported yet, or is running with an older setup. */
    PHOTOS_WAITING,
}

@Composable
fun StatusCard(state: ModuleState, title: String, subtitle: String?) {
    val containerColor = when (state) {
        ModuleState.PHOTOS_OK -> MaterialTheme.colorScheme.primaryContainer
        ModuleState.DISABLED, ModuleState.PHOTOS_WAITING -> MaterialTheme.colorScheme.tertiaryContainer
        ModuleState.NOT_LOADED, ModuleState.PHOTOS_ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (state) {
        ModuleState.PHOTOS_OK -> MaterialTheme.colorScheme.onPrimaryContainer
        ModuleState.DISABLED, ModuleState.PHOTOS_WAITING -> MaterialTheme.colorScheme.onTertiaryContainer
        ModuleState.NOT_LOADED, ModuleState.PHOTOS_ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = if (state == ModuleState.PHOTOS_OK) Icons.Default.CheckCircle else Icons.Default.Warning

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
                    text = title,
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
