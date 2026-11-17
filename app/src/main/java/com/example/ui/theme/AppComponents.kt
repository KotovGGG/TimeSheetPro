package com.example.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role

/** Intent, rather than a screen's choice of color or corner radius, determines emphasis. */
enum class AppAction { Primary, Secondary, Tonal, Quiet, Destructive, DestructiveQuiet }

/** Keep complete action labels readable when the available width or font scale changes. */
@Composable
fun AppFormActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) { content() }
}

/** The whole labelled row is one checkbox target, including its accessibility state. */
@Composable
fun AppCheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = AppSize.touchTarget)
        .clip(MaterialTheme.shapes.small)
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Checkbox(checked = checked, onCheckedChange = null)
        content()
    }
}

@Composable
fun AppInfoMessage(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(AppSpacing.md), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Icon(Icons.Default.Info, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(AppSize.icon))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun AppClientField(value: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, border = BorderStroke(AppSpacing.hairline, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.heightIn(min = AppSize.primaryAction).padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Box(Modifier.size(AppSpacing.md).clip(CircleShape).background(color))
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
}

@androidx.compose.runtime.Immutable
data class ClientChoice(val id: Long, val name: String, val color: Color)

@Composable
fun AppClientPickerSheet(options: List<ClientChoice>, selectedId: Long,
    onSelect: (Long) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AppFormSheet(onDismiss, modifier) {
        AppSheetHeader("Заказчик", onDismiss)
        if (options.isEmpty()) {
            Text("Пока нет заказчиков. Добавьте заказчика во вкладке «Заказчики».",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = AppSize.primaryAction)
                    .selectable(option.id == selectedId, role = Role.RadioButton, onClick = { onSelect(option.id) })
                    .then(Modifier.padding(vertical = AppSpacing.sm)),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    RadioButton(selected = option.id == selectedId, onClick = null)
                    Box(Modifier.size(AppSpacing.md).clip(CircleShape).background(option.color))
                    Text(option.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState(), content = content)
}

@Composable
fun AppIconAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AppTooltip(label) {
        IconButton(onClick = onClick, enabled = enabled,
            modifier = modifier.sizeIn(minWidth = AppSize.touchTarget, minHeight = AppSize.touchTarget)) {
            Icon(icon, contentDescription = label)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePicker(state: TimePickerState) {
    val compact = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() < 600.dp
    } ||
        androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    var keyboard by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (compact || keyboard) TimeInput(state) else TimePicker(state)
        if (!compact) AppButton(onClick = { keyboard = !keyboard }, variant = AppAction.Quiet) {
            Text(if (keyboard) "Выбрать на циферблате" else "Ввести время")
        }
    }
}

@Composable
fun AppSheetHeader(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        AppIconAction("Закрыть", androidx.compose.material.icons.Icons.Default.Close, onDismiss)
    }
}

/** Repeat relocation after the keyboard changes the form's visible scroll area. */
@Composable
fun Modifier.keepFocusedFieldVisible(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused && imeBottom > 0) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return bringIntoViewRequester(requester).onFocusChanged { focused = it.hasFocus }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFormSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.xl).padding(bottom = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg), content = content)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: AppAction = AppAction.Primary,
    content: @Composable RowScope.() -> Unit,
) {
    val bounds = modifier.heightIn(min = AppSize.touchTarget)
    val shapes = ButtonDefaults.shapes()
    when (variant) {
        AppAction.Primary, AppAction.Destructive -> Button(
            onClick = onClick, shapes = shapes, modifier = bounds, enabled = enabled,
            colors = if (variant == AppAction.Destructive) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
            ) else ButtonDefaults.buttonColors(), content = content,
        )
        AppAction.Secondary -> OutlinedButton(onClick = onClick, shapes = shapes,
            modifier = bounds, enabled = enabled, content = content)
        AppAction.Tonal -> FilledTonalButton(onClick = onClick, shapes = shapes,
            modifier = bounds, enabled = enabled, content = content)
        AppAction.Quiet, AppAction.DestructiveQuiet -> TextButton(
            onClick = onClick, shapes = shapes, modifier = bounds, enabled = enabled,
            colors = if (variant == AppAction.DestructiveQuiet) ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ) else ButtonDefaults.textButtonColors(), content = content,
        )
    }
}

@Composable
fun AppStatusLabel(text: String, colors: StatusColors, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = colors.container, contentColor = colors.content) {
        Text(text, modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AppSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
fun AppSettingsRow(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        onClick = onClick, supportingContent = { Text(value) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
        content = { Text(title) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppChoiceSheet(
    title: String, options: List<Pair<T, String>>, selected: T,
    onSelect: (T) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("choice_dialog_surface"),
        dragHandle = { BottomSheetDefaults.DragHandle(Modifier.testTag("choice_dialog_handle")) },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().testTag("choice_dialog_content").verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.xl).padding(bottom = AppSpacing.xl)) {
            AppSheetHeader(title, onDismiss)
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = AppSize.primaryAction)
                        .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(vertical = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(AppSpacing.md))
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppColorPickerSheet(
    options: List<Pair<String, String>>, selected: String,
    onSelect: (String) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.xl).padding(bottom = AppSpacing.xl)) {
            Text("Цвет заказчика", style = MaterialTheme.typography.titleLarge)
            Text("Метка помогает различать заказчиков в записях.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = AppSpacing.lg))
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                options.forEach { (hex, label) ->
                    val color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
                    Box(Modifier.size(AppSize.touchTarget)
                        .selectable(selected = selected == hex, role = Role.RadioButton, onClick = { onSelect(hex) })
                        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                        Surface(Modifier.size(AppSpacing.xxl), shape = CircleShape, color = color,
                            border = if (hex == selected) androidx.compose.foundation.BorderStroke(
                                AppSpacing.micro, MaterialTheme.colorScheme.onSurface) else null) {}
                        if (hex == selected) Icon(Icons.Default.Check, contentDescription = null,
                            tint = if (color.luminance() > 0.4f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(AppSize.smallIcon))
                    }
                }
            }
        }
    }
}

@Composable
fun AppCompactLedgerSummary(title: String, hours: String, amount: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(hours, style = LedgerStyles.MonospaceNumberSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(amount, style = LedgerStyles.MonospaceNumberSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun AppLedgerSummary(title: String, time: String, amount: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppSectionTitle(title)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val amountWidth = measurer.measure(androidx.compose.ui.text.AnnotatedString(amount),
                    style = LedgerStyles.MonospaceNumberMedium, softWrap = false).size.width
                val columnWidth = with(density) { ((maxWidth - AppSpacing.lg) / 2).toPx() }
                if (maxWidth < AppSize.summaryColumnsMin || density.fontScale > 1.3f || amountWidth > columnWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        LedgerMetric("Время", time)
                        LedgerMetric("Сумма", amount)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                        LedgerMetric("Время", time, Modifier.weight(1f))
                        LedgerMetric("Сумма", amount, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (value.length > 14) LedgerStyles.MonospaceAmountLong else LedgerStyles.MonospaceNumberMedium)
    }
}
