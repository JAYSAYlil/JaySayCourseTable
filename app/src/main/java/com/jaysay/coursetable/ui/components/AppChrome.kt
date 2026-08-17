package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val AppPanelShape = RoundedCornerShape(16.dp)

/** Shared top chrome keeps navigation, titles and dividers consistent across screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold) },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

/** Shared panel treatment: quiet surface, 16 dp radius and a theme-aware hairline. */
@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppPanelShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            0.75.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}
