package com.example.ui.screens.settings

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
                    is java.io.IOException -> "Не удалось прочитать или сохранить файл. Проверьте доступность хранилища и попробуйте ещё раз."
                    is SecurityException -> "Нет доступа к выбранному файлу. Выберите его повторно или сохраните копию в другом месте."
                    else -> error.localizedMessage ?: "Попробуйте ещё раз"
                }
            }
            finally { busy = false }
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val path = backupPath
        if (uri != null && path != null) performOperation {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)) { "Не удалось открыть файл" }.use { output ->
                    File(path).inputStream().use { it.copyTo(output) }
                }
            }
            backupPath = null
            message = "Резервная копия сохранена"
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) restoreUri = uri.toString()
    }

    val timeOptions = listOf(0 to "Без округления", 5 to "5 минут", 10 to "10 минут", 15 to "15 минут", 30 to "30 минут")
    val amountOptions = listOf(
        AmountRoundingMode.NONE to "Без дополнительного округления",
        AmountRoundingMode.ROUND_UP to "В большую сторону",
        AmountRoundingMode.ROUND_DOWN to "В меньшую сторону",
        AmountRoundingMode.ROUND_NEAREST to "До ближайшего значения",
    )
    val themeOptions = listOf(ThemeMode.SYSTEM to "Как на устройстве", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная")

    Column(modifier.fillMaxSize().testTag("settings_scroll").verticalScroll(rememberScrollState())
        .padding(horizontal = AppSpacing.page, vertical = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        SettingsGroup("Оплата") {
            AppSettingsRow("Часовая ставка", "${com.example.ui.screens.timer.formatCurrency(settings.globalHourlyRate, settings.currency)} / ч", onClick = {
                rateInput = settings.globalHourlyRate.toString(); editor = "rate"
            }, modifier = Modifier.testTag("settings_rate"))
            AppSettingsRow("Валюта", settings.currency, onClick = { editor = "currency" }, modifier = Modifier.testTag("settings_currency"))
        }
        SettingsGroup("Расчёты") {
            AppSettingsRow("Округление времени", timeOptions.firstOrNull { it.first == settings.timeRoundingMinutes }?.second
                ?: "${settings.timeRoundingMinutes} минут", onClick = { editor = "time" }, modifier = Modifier.testTag("settings_time_rounding"))
            AppSettingsRow("Округление суммы", amountOptions.first { it.first == settings.amountRoundingMode }.second,
                onClick = { editor = "amount" }, modifier = Modifier.testTag("settings_amount_rounding"))
        }
        SettingsGroup("Оформление") {
            AppSettingsRow("Тема", themeOptions.first { it.first == settings.themeMode }.second,
                onClick = { editor = "theme" }, modifier = Modifier.testTag("settings_theme"))
        }
        SettingsGroup("Резервная копия", Modifier.testTag("backup_restore_card")) {
            AppSettingsRow("Создать копию", "Заказчики, записи, ставки и настройки", onClick = {
                performOperation {
                    val json = viewModel.createBackupJson()
                    val file = withContext(Dispatchers.IO) {
                        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
                        File(context.cacheDir, "TimeSheetPro_Backup_$stamp.json").also { it.writeText(json, Charsets.UTF_8) }
                    }
                    backupPath = file.absolutePath
                }
            }, modifier = Modifier.testTag("settings_backup"))
            AppSettingsRow("Восстановить из файла", "Заменить данные содержимым копии", onClick = {
                if (!busy) openBackup.launch("*/*")
            }, modifier = Modifier.testTag("settings_restore"))
        }
        Text("JSON-копия не включает отчёты, вложения и текущую сессию таймера.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("TimeSheetPro ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    when (editor) {
        "rate" -> {
            val rate = rateInput.replace(',', '.').toDoubleOrNull()
            val invalid = rate == null || !rate.isFinite() || rate < 0.0
            AlertDialog(onDismissRequest = { editor = null }, title = { Text("Часовая ставка") },
                text = {
                    OutlinedTextField(shape = MaterialTheme.shapes.medium, value = rateInput, onValueChange = { rateInput = it },
                        label = { Text("Ставка, ${settings.currency} / ч") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = invalid && rateInput.isNotEmpty(),
                        supportingText = { Text(if (invalid && rateInput.isNotEmpty()) "Введите число не меньше нуля" else "Для заказчиков без персональной ставки") },
                        modifier = Modifier.keepFocusedFieldVisible().fillMaxWidth().testTag("settings_rate_input"))
                },
                confirmButton = { AppButton(onClick = { viewModel.updateGlobalRate(checkNotNull(rate)); editor = null }, enabled = !invalid) { Text("Сохранить") } },
                dismissButton = { AppButton(onClick = { editor = null }, variant = AppAction.Quiet) { Text("Отмена") } })
        }
        "currency" -> AppChoiceSheet("Валюта", listOf("USD", "EUR", "RUB", "GBP", "CHF", "JPY", "KZT", "BYN").map { it to it },
            settings.currency, onSelect = { viewModel.updateCurrency(it); editor = null }, onDismiss = { editor = null })
        "time" -> AppChoiceSheet("Округление времени", timeOptions, settings.timeRoundingMinutes,
            onSelect = { viewModel.updateTimeRounding(it); editor = null }, onDismiss = { editor = null })
        "amount" -> AppChoiceSheet("Округление суммы", amountOptions, settings.amountRoundingMode,
            onSelect = { viewModel.updateAmountRoundingMode(it); editor = null }, onDismiss = { editor = null })
        "theme" -> AppChoiceSheet("Тема", themeOptions, settings.themeMode,
            onSelect = { viewModel.updateThemeMode(it); editor = null }, onDismiss = { editor = null })
    }

    backupPath?.let { path ->
        val file = File(path)
        AlertDialog(onDismissRequest = { backupPath = null }, title = { Text("Копия готова") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Text("Сохраните файл на устройстве или отправьте его в своё хранилище.")
                Text(file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { AppButton(onClick = { saveBackup.launch(file.name) }, enabled = !busy) { Text("Сохранить файл") } },
            dismissButton = { AppButton(onClick = {
                performOperation {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Поделиться копией"))
                }
            }, variant = AppAction.Quiet, enabled = !busy) { Text("Поделиться") } })
    }
    restoreUri?.let { source ->
        AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text("Заменить текущие данные?") },
            text = { Text("Заказчики, записи, история ставок и настройки будут заменены данными из файла. Перед восстановлением сохраните копию текущих данных.") },
            confirmButton = { AppButton(onClick = {
                restoreUri = null
                performOperation {
                    val json = withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openInputStream(android.net.Uri.parse(source))) { "Не удалось прочитать файл" }
                            .bufferedReader().use { it.readText() }
                    }
                    viewModel.restoreBackupJson(json).getOrThrow()
                    com.example.utils.AutoBackupManager.triggerBackup(context)
                    message = "Данные восстановлены"
                }
            }, variant = AppAction.Destructive, enabled = !busy) { Text("Заменить данные") } },
            dismissButton = { AppButton(onClick = { restoreUri = null }, variant = AppAction.Quiet) { Text("Отмена") } })
    }
    if (busy) AlertDialog(onDismissRequest = {}, confirmButton = {},
        text = { Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            CircularProgressIndicator(Modifier.size(AppSize.icon)); Text("Обработка файла…")
        } })
    (operationError ?: message)?.let { text ->
        AlertDialog(onDismissRequest = { operationError = null; message = null },
            title = { Text(if (operationError != null) "Ошибка копии" else "Готово") },
            text = { Text(text) }, confirmButton = {
                AppButton(onClick = { operationError = null; message = null }, variant = AppAction.Quiet) { Text("Закрыть") }
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
