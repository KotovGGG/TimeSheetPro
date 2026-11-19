package com.example.ui.screens.settings

import com.example.R
import com.example.appText
import com.example.ui.theme.keepFocusedFieldVisible
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.data.local.entity.AmountRoundingMode
import com.example.data.local.entity.ThemeMode
import com.example.ui.AppViewModelProvider
import com.example.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var rateInput by rememberSaveable { mutableStateOf("") }
    var backupPath by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var operationError by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun performOperation(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                operationError = when (error) {
                    is java.io.IOException -> appText(R.string.copy_could_not_read_or_save_the_file)
                    is SecurityException -> appText(R.string.copy_the_selected_file_is_inaccessible_select_it)
                    else -> error.localizedMessage ?: appText(R.string.copy_try_again)
                }
            }
            finally { busy = false }
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val path = backupPath
        if (uri != null && path != null) performOperation {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)) { appText(R.string.copy_could_not_open_the_file) }.use { output ->
                    File(path).inputStream().use { it.copyTo(output) }
                }
            }
            backupPath = null
            message = appText(R.string.copy_backup_saved)
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) restoreUri = uri.toString()
    }

    val timeOptions = listOf(0 to appText(R.string.copy_no_rounding), 5 to appText(R.string.copy_text_5_minutes), 10 to appText(R.string.copy_text_10_minutes), 15 to appText(R.string.copy_text_15_minutes), 30 to appText(R.string.copy_text_30_minutes))
    val amountOptions = listOf(
        AmountRoundingMode.NONE to appText(R.string.copy_no_extra_rounding),
        AmountRoundingMode.ROUND_UP to appText(R.string.copy_round_up),
        AmountRoundingMode.ROUND_DOWN to appText(R.string.copy_round_down),
        AmountRoundingMode.ROUND_NEAREST to appText(R.string.copy_round_to_nearest),
    )
    val themeOptions = listOf(ThemeMode.SYSTEM to appText(R.string.copy_follow_device), ThemeMode.LIGHT to appText(R.string.copy_light), ThemeMode.DARK to appText(R.string.copy_dark))

    Column(modifier.fillMaxSize().testTag("settings_scroll").verticalScroll(rememberScrollState())
        .padding(horizontal = AppSpacing.page, vertical = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)) {
        Text(appText(R.string.copy_settings), style = MaterialTheme.typography.headlineMedium)
        SettingsGroup(appText(R.string.copy_billing)) {
            AppSettingsRow(appText(R.string.copy_hourly_rate), appText(R.string.copy_text_1_hr_2, com.example.ui.screens.timer.formatCurrency(settings.globalHourlyRate, settings.currency)), onClick = {
                rateInput = settings.globalHourlyRate.toString(); editor = "rate"
            }, modifier = Modifier.testTag("settings_rate"))
            AppSettingsRow(appText(R.string.copy_currency), settings.currency, onClick = { editor = "currency" }, modifier = Modifier.testTag("settings_currency"))
        }
        SettingsGroup(appText(R.string.copy_calculations)) {
            AppSettingsRow(appText(R.string.copy_time_rounding), timeOptions.firstOrNull { it.first == settings.timeRoundingMinutes }?.second
                ?: appText(R.string.copy_text_1_minutes, settings.timeRoundingMinutes), onClick = { editor = "time" }, modifier = Modifier.testTag("settings_time_rounding"))
            AppSettingsRow(appText(R.string.copy_amount_rounding), amountOptions.first { it.first == settings.amountRoundingMode }.second,
                onClick = { editor = "amount" }, modifier = Modifier.testTag("settings_amount_rounding"))
        }
        SettingsGroup(appText(R.string.copy_appearance)) {
            AppSettingsRow(appText(R.string.copy_theme), themeOptions.first { it.first == settings.themeMode }.second,
                onClick = { editor = "theme" }, modifier = Modifier.testTag("settings_theme"))
        }
        SettingsGroup(appText(R.string.copy_backup), Modifier.testTag("backup_restore_card")) {
            AppSettingsRow(appText(R.string.copy_create_backup), appText(R.string.copy_clients_records_rates_and_settings), onClick = {
                performOperation {
                    val json = viewModel.createBackupJson()
                    val file = withContext(Dispatchers.IO) {
                        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
                        File(context.cacheDir, "TimeSheetPro_Backup_$stamp.json").also { it.writeText(json, Charsets.UTF_8) }
                    }
                    backupPath = file.absolutePath
                }
            }, modifier = Modifier.testTag("settings_backup"))
            AppSettingsRow(appText(R.string.copy_restore_from_file), appText(R.string.copy_replace_data_with_the_contents_of_a), onClick = {
                if (!busy) openBackup.launch("*/*")
            }, modifier = Modifier.testTag("settings_restore"))
        }
        Text(
            "TimeSheetPro ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    when (editor) {
        "rate" -> {
            val rate = rateInput.replace(',', '.').toDoubleOrNull()
            val invalid = rate == null || !rate.isFinite() || rate < 0.0
            AlertDialog(onDismissRequest = { editor = null }, title = { Text(appText(R.string.copy_hourly_rate)) },
                text = {
                    OutlinedTextField(shape = MaterialTheme.shapes.medium, value = rateInput, onValueChange = { rateInput = it },
                        label = { Text(appText(R.string.copy_rate_1_hr, settings.currency)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = invalid && rateInput.isNotEmpty(),
                        supportingText = { Text(if (invalid && rateInput.isNotEmpty()) appText(R.string.copy_enter_a_number_greater_than_or_equal) else appText(R.string.copy_for_clients_without_a_custom_rate)) },
                        modifier = Modifier.keepFocusedFieldVisible().fillMaxWidth().testTag("settings_rate_input"))
                },
                confirmButton = { AppButton(onClick = { viewModel.updateGlobalRate(checkNotNull(rate)); editor = null }, enabled = !invalid) { Text(appText(R.string.copy_save)) } },
                dismissButton = { AppButton(onClick = { editor = null }, variant = AppAction.Quiet) { Text(appText(R.string.copy_cancel)) } })
        }
        "currency" -> AppChoiceSheet(appText(R.string.copy_currency), com.example.data.local.entity.billingCurrencies.map { it to it },
            settings.currency, onSelect = { viewModel.updateCurrency(it); editor = null }, onDismiss = { editor = null })
        "time" -> AppChoiceSheet(appText(R.string.copy_time_rounding), timeOptions, settings.timeRoundingMinutes,
            onSelect = { viewModel.updateTimeRounding(it); editor = null }, onDismiss = { editor = null })
        "amount" -> AppChoiceSheet(appText(R.string.copy_amount_rounding), amountOptions, settings.amountRoundingMode,
            onSelect = { viewModel.updateAmountRoundingMode(it); editor = null }, onDismiss = { editor = null })
        "theme" -> AppChoiceSheet(appText(R.string.copy_theme), themeOptions, settings.themeMode,
            onSelect = { viewModel.updateThemeMode(it); editor = null }, onDismiss = { editor = null })
    }

    backupPath?.let { path ->
        val file = File(path)
        AlertDialog(onDismissRequest = { backupPath = null }, title = { Text(appText(R.string.copy_backup_ready)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Text(appText(R.string.copy_save_the_file_to_your_device_or))
                Text(file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { AppButton(onClick = { saveBackup.launch(file.name) }, enabled = !busy) { Text(appText(R.string.copy_save_file)) } },
            dismissButton = { AppButton(onClick = {
                performOperation {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, appText(R.string.copy_share_backup)))
                }
            }, variant = AppAction.Quiet, enabled = !busy) { Text(appText(R.string.copy_share)) } })
    }
    restoreUri?.let { source ->
        AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text(appText(R.string.copy_replace_current_data)) },
            text = { Text(appText(R.string.copy_this_replaces_clients_records_rate_history_and)) },
            confirmButton = { AppButton(onClick = {
                restoreUri = null
                performOperation {
                    val json = withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openInputStream(android.net.Uri.parse(source))) { appText(R.string.copy_could_not_read_the_file) }
                            .bufferedReader().use { it.readText() }
                    }
                    viewModel.restoreBackupJson(json).getOrThrow()
                    com.example.utils.AutoBackupManager.triggerBackup(context)
                    message = appText(R.string.copy_data_restored)
                }
            }, variant = AppAction.Destructive, enabled = !busy) { Text(appText(R.string.copy_replace_data)) } },
            dismissButton = { AppButton(onClick = { restoreUri = null }, variant = AppAction.Quiet) { Text(appText(R.string.copy_cancel)) } })
    }
    if (busy) AlertDialog(onDismissRequest = {}, confirmButton = {},
        text = { Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            CircularProgressIndicator(Modifier.size(AppSize.icon)); Text(appText(R.string.copy_processing_file))
        } })
    (operationError ?: message)?.let { text ->
        AlertDialog(onDismissRequest = { operationError = null; message = null },
            title = { Text(if (operationError != null) appText(R.string.copy_backup_error) else appText(R.string.copy_done)) },
            text = { Text(text) }, confirmButton = {
                AppButton(onClick = { operationError = null; message = null }, variant = AppAction.Quiet) { Text(appText(R.string.copy_close)) }
            })
    }
}

@Composable
private fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        AppSectionTitle(title, Modifier.padding(horizontal = AppSpacing.lg))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
