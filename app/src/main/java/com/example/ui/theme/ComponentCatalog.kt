package com.example.ui.theme

import com.example.R
import androidx.compose.ui.res.stringResource
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
            AppSectionTitle(stringResource(R.string.copy_time_and_calculations))
            TimerDial(4523, MaterialTheme.colorScheme.primary, isRunning = true, dialSize = 280.dp)
            Card {
                Column(Modifier.padding(AppSpacing.lg)) {
                    Text(stringResource(R.string.copy_app_development), style = MaterialTheme.typography.titleMedium)
                    Text("12 450,00 ₽", style = LedgerStyles.NumberMedium)
                }
            }
            AppButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(AppSpacing.sm))
                Text(stringResource(R.string.copy_resume))
            }
            Column(Modifier.testTag("catalog_actions"), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                AppSectionTitle(stringResource(R.string.copy_action_hierarchy))
                for ((variant, label) in listOf(
                    AppAction.Primary to stringResource(R.string.copy_save_record),
                    AppAction.Tonal to stringResource(R.string.copy_share),
                    AppAction.Secondary to stringResource(R.string.copy_add_manually),
                    AppAction.Quiet to stringResource(R.string.copy_cancel),
                    AppAction.Destructive to stringResource(R.string.copy_delete_record),
                    AppAction.DestructiveQuiet to stringResource(R.string.copy_delete_client),
                )) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        AppButton(onClick = {}, variant = variant) { Text(label) }
                        AppButton(onClick = {}, variant = variant, enabled = false) { Text(label) }
                    }
                }
            }
            Column(Modifier.testTag("catalog_fields"), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                AppSectionTitle(stringResource(R.string.copy_fields_and_selection))
                OutlinedTextField(shape = MaterialTheme.shapes.medium, value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.copy_comment)) })
                OutlinedTextField(shape = MaterialTheme.shapes.medium, value = "", onValueChange = {}, isError = true,
                    label = { Text(stringResource(R.string.copy_rate)) }, supportingText = { Text(stringResource(R.string.copy_enter_an_amount)) })
                FilterChip(selected = selected, onClick = { selected = !selected }, label = { Text(stringResource(R.string.copy_this_month)) })
                AppCheckboxRow(checked = selected, onCheckedChange = { selected = it }) {
                    Text(stringResource(R.string.copy_include_comments))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = AppSize.touchTarget)
                    .toggleable(selected, role = Role.Switch, onValueChange = { selected = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = selected, onCheckedChange = null)
                    Spacer(Modifier.width(AppSpacing.md))
                    Text(stringResource(R.string.copy_selected_state))
                }
                AppSettingsRow(stringResource(R.string.copy_theme), stringResource(R.string.copy_follow_device), onClick = {})
            }
            AppLedgerSummary(stringResource(R.string.copy_period_total), stringResource(R.string.copy_text_124_50_hr), "9 876 543,21 ₽")
            Column(Modifier.testTag("catalog_statuses"), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                AppSectionTitle(stringResource(R.string.copy_statuses))
                AppStatusLabel(stringResource(R.string.copy_paid_2), LocalStatusPalette.current.paid)
                AppStatusLabel(stringResource(R.string.copy_invoiced), LocalStatusPalette.current.invoiced)
                AppStatusLabel(stringResource(R.string.copy_unpaid_2), LocalStatusPalette.current.unpaid)
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Large text", fontScale = 2f, showBackground = true)
@Composable
private fun CatalogPreview() { MyApplicationTheme { ComponentCatalog() } }
