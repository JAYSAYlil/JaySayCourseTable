@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.jaysay.coursetable.R
import com.jaysay.coursetable.ui.theme.AppShapes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Commit a sheet action once, after the sheet has visibly returned below the window.
 *
 * 收起动画期间宿主可能把面板移出组合（点遮罩、下滑、系统返回），这会取消面板自己的
 * 组合作用域。因此这里用 UNDISPATCHED 立刻进入协程，并在 NonCancellable 里等收起完成、
 * 执行动作——已经按下的“确认”不会因为面板被提前销毁而静默丢失。
 */
@Composable
fun rememberSheetDismiss(sheetState: SheetState): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    var closing by remember(sheetState) { mutableStateOf(false) }
    return remember(sheetState, scope) {
        { action ->
            if (!closing) {
                closing = true
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        withContext(NonCancellable) {
                            sheetState.hide()
                            action()
                        }
                    } finally {
                        closing = false
                    }
                }
            }
        }
    }
}

/** Window-owned exit survives composition removal, including save/delete callbacks.
 * Focus, IME, outside taps, accessibility and system Back remain native Dialog behavior.
 */
@Composable
fun AppDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit) {
    Dialog(onDismissRequest, properties) {
        OverlayWindowMotion(R.style.AppCenteredOverlayAnimation)
        content()
    }
}

@Composable
private fun OverlayWindowMotion(style: Int) {
    val view = LocalView.current
    SideEffect {
        var parent = view.parent
        while (parent != null && parent !is DialogWindowProvider) parent = parent.parent
        (parent as? DialogWindowProvider)?.window?.let { window ->
            window.setWindowAnimations(style)
            window.setDimAmount(0.25f)
        }
    }
}

@Composable
fun AppAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null, shape: Shape = AppShapes.panel,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    properties: DialogProperties = DialogProperties()) {
    AppDialog(onDismissRequest, properties) {
        Surface(modifier.widthIn(max = 520.dp), shape = shape, color = containerColor,
            tonalElevation = 0.dp) {
            Column(Modifier.padding(top = 20.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (icon != null) Box(Modifier.fillMaxWidth(), Alignment.Center) { icon() }
                    if (title != null) ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                    if (text != null) ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    Spacer(Modifier.height(4.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
                // Wrap on narrow/freeform windows and at large font sizes instead of clipping actions.
                FlowRow(Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** Keep native sheet gesture/focus handling with neutral surfaces and a quiet handle.
 * Button actions use rememberSheetDismiss so disposal follows the exit animation.
 */
@Composable
fun AppModalBottomSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape = AppShapes.sheet, containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface, tonalElevation: Dp = 0.dp,
    scrimColor: Color = Color.Black.copy(alpha = 0.25f),
    dragHandle: (@Composable () -> Unit)? = { SheetHandle() }, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest, modifier, sheetState = sheetState, shape = shape,
        containerColor = containerColor, contentColor = contentColor, tonalElevation = 0.dp,
        scrimColor = scrimColor, dragHandle = dragHandle) {
        content()
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), Alignment.Center) {
        Surface(Modifier.size(36.dp, 5.dp), shape = AppShapes.small,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)) {}
    }
}

@Composable
fun AppDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero, shape: Shape = AppShapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface, tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 12.dp,
    border: BorderStroke? = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, onDismissRequest, modifier, offset,
        shape = shape, containerColor = containerColor,
        tonalElevation = 0.dp, shadowElevation = shadowElevation,
        border = border, content = content)
}
