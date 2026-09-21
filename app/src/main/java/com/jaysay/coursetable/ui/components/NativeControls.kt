@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jaysay.coursetable.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.ui.theme.AppShapes
import com.jaysay.coursetable.ui.theme.Motion
import com.jaysay.coursetable.ui.theme.pressScale
import com.jaysay.coursetable.R

/** Shared native-style controls. Keep Compose semantics, input and accessibility contracts. */
@Composable
fun AppButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = AppShapes.small, colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.Button(onClick, modifier.pressScale(source), enabled, shape,
        colors, elevation = null, contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
fun AppTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = AppShapes.small, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.TextButton(onClick, modifier.pressScale(source), enabled, shape,
        colors, contentPadding = contentPadding, interactionSource = source, content = content)
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = AppShapes.small, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) = AppButton(onClick, modifier, enabled, shape,
    colors.copy(containerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), contentPadding, content)

@Composable
fun AppFilledTonalButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = AppShapes.small, colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) = AppOutlinedButton(onClick, modifier, enabled, shape, colors, contentPadding, content)

@Composable
fun AppIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(), content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.IconButton(onClick, modifier.pressScale(source), enabled, colors,
        interactionSource = source, content = content)
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    enabled: Boolean = true) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, Motion.interactive(), label = "switchThumb")
    val track by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), Motion.eased(150), label = "switchTrack")
    val source = remember { MutableInteractionSource() }
    Box(modifier.sizeIn(minWidth = 51.dp, minHeight = 48.dp)
        .then(if (onCheckedChange == null) Modifier else Modifier.toggleable(checked, source, null,
            enabled, Role.Switch, onCheckedChange)).alpha(if (enabled) 1f else 0.4f), Alignment.Center) {
        Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(track)) {
            Box(Modifier.offset(x = 2.dp + 20.dp * progress, y = 2.dp).size(27.dp)
                .shadow(2.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
}

@Composable
fun AppCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val color by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        Motion.eased(150), label = "checkFill")
    Box(modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
        .then(if (onCheckedChange == null) Modifier else Modifier.toggleable(checked, source, null,
            enabled, Role.Checkbox, onCheckedChange)).alpha(if (enabled) 1f else 0.4f), Alignment.Center) {
        Box(Modifier.size(23.dp).background(color, CircleShape).border(1.dp,
            if (checked) color else MaterialTheme.colorScheme.outline, CircleShape), Alignment.Center) {
            if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun AppRadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier,
    enabled: Boolean = true, colors: RadioButtonColors = RadioButtonDefaults.colors()) {
    val source = remember { MutableInteractionSource() }
    Box(modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
        .then(if (onClick == null) Modifier else Modifier.selectable(selected, source, null, enabled,
            Role.RadioButton, onClick)), Alignment.Center) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = if (enabled) colors.selectedColor else colors.disabledSelectedColor)
    }
}

@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null, shape: Shape = AppShapes.small) {
    val source = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else
        MaterialTheme.colorScheme.surfaceVariant, Motion.eased(150), label = "selectionFill")
    CompositionLocalProvider(LocalContentColor provides if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(modifier.defaultMinSize(minHeight = 40.dp).pressScale(source).clip(shape).background(fill)
                .selectable(selected, source, null, enabled, Role.Tab, onClick)
                .alpha(if (enabled) 1f else 0.4f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                leadingIcon?.invoke()
                label()
                trailingIcon?.invoke()
            }
        }
    }
}

@Composable
fun AppAssistChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null) {
    AppTextButton(onClick, modifier, enabled, colors = ButtonDefaults.textButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface)) {
        leadingIcon?.invoke()
        label()
        trailingIcon?.invoke()
    }
}

/** Stable inset labels and soft fields; labels never float or cut through an outline. */
@Composable
fun AppTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1,
    shape: Shape = AppShapes.input, colors: TextFieldColors = OutlinedTextFieldDefaults.colors()) {
    val foreground = when {
        !enabled -> colors.disabledTextColor
        isError -> colors.errorTextColor
        else -> colors.unfocusedTextColor
    }
    val errorDescription = stringResource(R.string.input_error_accessibility)
    BasicTextField(value, onValueChange,
        modifier = modifier.defaultMinSize(minHeight = 56.dp).clip(shape)
            .background(if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant).alpha(if (enabled) 1f else 0.5f)
            .semantics { if (isError) error(errorDescription) },
        enabled = enabled, readOnly = readOnly,
        textStyle = textStyle.merge(TextStyle(color = foreground)),
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        decorationBox = { input ->
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    leadingIcon?.invoke()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CompositionLocalProvider(LocalContentColor provides if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant) {
                            if (label != null) ProvideTextStyle(MaterialTheme.typography.labelSmall) { label() }
                        }
                        Box {
                            if (value.isEmpty()) CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                placeholder?.invoke()
                            }
                            input()
                        }
                    }
                    trailingIcon?.invoke()
                }
                if (supportingText != null) {
                    Spacer(Modifier.height(4.dp))
                    ProvideTextStyle(MaterialTheme.typography.bodySmall) { supportingText() }
                }
            }
        })
}
