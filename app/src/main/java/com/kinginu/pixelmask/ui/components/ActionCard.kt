package com.kinginu.pixelmask.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class ActionCardItemData(
    val title: String,
    val icon: ImageVector,
    val color: Color? = null,
    val onClick: () -> Unit
)

@Composable
fun ActionCard(items: List<ActionCardItemData>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column {
            items.forEachIndexed { index, item ->
                ActionItem(item = item)
                if (index < items.size - 1) SoftDivider()
            }
        }
    }
}

@Composable
private fun ActionItem(item: ActionCardItemData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            color = item.color ?: MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = item.color ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
