package com.example.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.screens.timer.TimerDial
import androidx.compose.ui.unit.dp

@Composable
fun ComponentCatalog() {
    var text by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(false) }
    Surface {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.page),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            Text("TimeSheetPro", style = MaterialTheme.typography.headlineMedium)
            AppSectionTitle("Время и расчёты")
            TimerDial(4523, MaterialTheme.colorScheme.primary, isRunning = true, dialSize = 280.dp)
            Card {
                Column(Modifier.padding(AppSpacing.lg)) {
                    Text("Разработка приложения", style = MaterialTheme.typography.titleMedium)
                    Text("12 450,00 ₽", style = LedgerStyles.MonospaceNumberMedium)
                }
            }
            AppButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(AppSpacing.sm))
                Text("Продолжить")
            }
            Column(Modifier.testTag("catalog_actions"), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                AppSectionTitle("Иерархия действий")
                for ((variant, label) in listOf(
                    AppAction.Primary to "Сохранить запись",
                    AppAction.Tonal to "Поделиться",
                    AppAction.Secondary to "Добавить вручную",
                    AppAction.Quiet to "Отмена",
                    AppAction.Destructive to "Удалить запись",
                    AppAction.DestructiveQuiet to "Удалить заказчика",
                )) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        AppButton(onClick = {}, variant = variant) { Text(label) }
                        AppButton(onClick = {}, variant = variant, enabled = false) { Text(label) }
                    }
                }
            }
            Column(Modifier.testTag("catalog_fields"), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                AppSectionTitle("Поля и выбор")
                OutlinedTextField(shape = MaterialTheme.shapes.medium, value = text, onValueChange = { text = it }, label = { Text("Комментарий") })
                OutlinedTextField(shape = MaterialTheme.shapes.medium, value = "", onValueChange = {}, isError = true,
                    label = { Text("Ставка") }, supportingText = { Text("Введите сумму") })
                FilterChip(selected = selected, onClick = { selected = !selected }, label = { Text("Этот месяц") })
                AppCheckboxRow(checked = selected, onCheckedChange = { selected = it }) {
                    Text("Включить комментарии")
                }
                Row(Modifier.fillMaxWidth().heightIn(min = AppSize.touchTarget)
                    .toggleable(selected, role = Role.Switch, onValueChange = { selected = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = selected, onCheckedChange = null)
                    Spacer(Modifier.width(AppSpacing.md))
                    Text("Выбранное состояние")
                }
                AppSettingsRow("Тема", "Как на устройстве", onClick = {})
            }
            AppLedgerSummary("Итого за период", "124,50 ч", "9 876 543,21 ₽")
            Column(Modifier.testTag("catalog_statuses"), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                AppSectionTitle("Статусы")
                AppStatusLabel("Оплачено", LocalStatusPalette.current.paid)
                AppStatusLabel("Счёт выставлен", LocalStatusPalette.current.invoiced)
                AppStatusLabel("Не оплачено", LocalStatusPalette.current.unpaid)
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Large text", fontScale = 2f, showBackground = true)
@Composable
private fun CatalogPreview() { MyApplicationTheme { ComponentCatalog() } }
