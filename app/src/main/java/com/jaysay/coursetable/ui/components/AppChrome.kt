package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.ui.theme.AppShapes

/** 兼容别名：旧调用点仍引用 AppPanelShape，语义与 AppShapes.medium 一致。 */
val AppPanelShape = AppShapes.medium

/** Shared top chrome keeps navigation, titles and dividers consistent across screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            navigationIcon()
            Text(title, Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
    }
}

/** Neutral grouped surfaces, with an outline only for an explicitly selected panel. */
@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.panel,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) BorderStroke(
            0.75.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
        ) else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}
