package com.example.ui.screens.clients

import com.example.R
import com.example.appText
import com.example.ui.theme.AppButton as Button
import com.example.ui.theme.AppAction
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppSize
import com.example.ui.theme.LocalStatusPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.theme.keepFocusedFieldVisible
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerStatus
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.Client
import com.example.data.local.entity.RateHistoryEntry
import com.example.ui.AppViewModelProvider
import com.example.ui.screens.timer.formatCurrency
import com.example.ui.screens.timer.formatCurrencyTotals
import com.example.data.local.entity.billingCurrencies
import com.example.ui.screens.timer.safeParseColor
import com.example.ui.theme.LedgerStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@kotlinx.serialization.Serializable
data class ClientEditorState(
    val client: Client? = null, // null means adding a new client
    val name: String = "",
    val rate: String = "",
    val colorHex: String = "#D0BCFF"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val clients by viewModel.clientsState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val unpaidBalances by viewModel.unpaidBalancesState.collectAsStateWithLifecycle()

    var editorState by rememberSaveable(stateSaver = com.example.ui.theme.formStateSaver<ClientEditorState>()) { mutableStateOf<ClientEditorState?>(null) }
    var showDeleteConfirmDialogForClient by remember { mutableStateOf<Client?>(null) }

    val activeClients = remember(clients) { clients }

    val colorPresets = listOf(
        // Row 1: Reds, Corals, Oranges, Ambers, Golds
        "#E63946", // Crimson Red
        "#FF5A5F", // Soft Coral
        "#E76F51", // Terracotta Orange
        "#FB5607", // Intense Orange
        "#FF8533", // Bright Amber-Orange
        "#F4A261", // Sandy Amber
        "#DDA15E", // Warm Bronze
        "#FFBE0B", // Bright Gold
        
        // Row 2: Yellows, Warm Vanilla, Mint Greens, Emerald
        "#FFD166", // Soft Yellow
        "#E9D8A6", // Warm Vanilla
        "#76C893", // Mint Green
        "#52B788", // Forest Mint
        "#2D6A4F", // Deep Emerald
        "#00C49F", // Green Teal
        "#2EC4B6", // Emerald Teal
        "#A8DADC", // Soft Mint Cyan
        
        // Row 3: Cyans, Teal-Blues, Deep Blues, Royal Violets, Purples
        "#4CC9F0", // Sky Cyan
        "#48CAE4", // Bright Cyan
        "#00B4D8", // Bright Teal-Blue
        "#1A759F", // Ocean Blue
        "#0077B6", // Deep Azure
        "#3A86FF", // Electric Blue
        "#8338EC", // Royal Violet
        "#9B5DE5", // Amethyst Purple
        
        // Row 4: Orchids, Lavenders, Muted Slate, Dusty Roses, Pinks, Magentas
        "#C77DFF", // Pastel Orchid
        "#D0BCFF", // Premium Violet
        "#6D597A", // Muted Slate Violet
        "#B5838D", // Dusty Rose
        "#E8AEB2", // Soft Dusty Rose
        "#FF70A6", // Pastel Pink
        "#F15BB5", // Magenta
        "#FF2A6D"  // Neon Pink
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editorState = ClientEditorState() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .padding(bottom = AppSpacing.xs)
                    .size(AppSize.primaryAction)
                    .testTag("add_client_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = appText(R.string.copy_add_client),
                    modifier = Modifier.size(AppSpacing.xxl)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.page, vertical = AppSpacing.lg)
                .testTag("clients_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            // --- Header ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppSize.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = appText(R.string.copy_clients),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.testTag("screen_title")
                    )
                }
            }


            // --- Active Clients list ---
            if (activeClients.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PeopleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = appText(R.string.copy_no_clients_yet),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(activeClients, key = { it.id }) { client ->
                    val unpaidAmounts = unpaidBalances[client.id].orEmpty()
                    ClientCardItem(
                        client = client,
                        globalHourlyRate = settings.globalHourlyRate,
                        currency = client.currencyCode ?: settings.currency,
                        unpaidAmounts = unpaidAmounts,
                        onClick = {
                            editorState = ClientEditorState(
                                client = client,
                                name = client.name,
                                rate = client.defaultHourlyRate?.toString() ?: "",
                                colorHex = client.colorHex
                            )
                        }
                    )
                }
            }


        }
    }

    // --- Elegant Add/Edit Bottom Sheet ---
    editorState?.let { state ->
        val isEditMode = state.client != null
        val historyFlow = remember(state.client) {
            if (isEditMode) viewModel.getRateHistoryForClient(state.client!!.id) else null
        }
        val rateHistory by (historyFlow?.collectAsStateWithLifecycle(initialValue = emptyList()) ?: remember { mutableStateOf(emptyList()) })

        var nameInput by rememberSaveable { mutableStateOf(state.name) }
        var rateInput by rememberSaveable { mutableStateOf(state.rate) }
        var clientCurrency by rememberSaveable { mutableStateOf(state.client?.currencyCode) }
        var chooseCurrency by rememberSaveable { mutableStateOf(false) }
        val parsedPersonalRate = rateInput.trim().replace(',', '.').toDoubleOrNull()
        val needsPersonalRate = clientCurrency != null && (parsedPersonalRate == null || parsedPersonalRate <= 0.0)
        val invalidRate = needsPersonalRate || rateInput.isNotBlank() &&
            (parsedPersonalRate == null || !parsedPersonalRate.isFinite() || parsedPersonalRate < 0.0)
        if (chooseCurrency) {
            val inherited = ""
            com.example.ui.theme.AppChoiceSheet(
                appText(R.string.copy_client_currency),
                listOf(inherited to appText(R.string.copy_default_currency_1, settings.currency)) + billingCurrencies.map { it to it },
                clientCurrency ?: inherited,
                onSelect = { selection ->
                    val next = selection.ifEmpty { null }
                    if ((next ?: settings.currency) != (clientCurrency ?: settings.currency)) {
                        rateInput = ""
                    }
                    clientCurrency = next
                    chooseCurrency = false
                },
                onDismiss = { chooseCurrency = false }
            )
        }
        var colorHexInput by rememberSaveable { mutableStateOf(state.colorHex) }
        var isHistoryExpanded by rememberSaveable { mutableStateOf(false) }
        var showColorPicker by rememberSaveable { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { editorState = null },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xl)
                    .padding(bottom = AppSpacing.xxl)
                    .testTag("client_editor_dialog_content")
            ) {
                // Header with Title
                Text(
                    text = if (isEditMode) appText(R.string.copy_edit_client) else appText(R.string.copy_add_client),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                // Name Field
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(appText(R.string.copy_name)) },
                    placeholder = { Text(appText(R.string.copy_for_example_north_studio)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .keepFocusedFieldVisible()
                        .testTag("dialog_client_name_input"),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                com.example.ui.theme.AppSettingsRow(
                    appText(R.string.copy_client_currency),
                    clientCurrency ?: appText(R.string.copy_default_currency_1, settings.currency),
                    onClick = { chooseCurrency = true }
                )

                // Personal Rate Field
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = it },
                    label = { Text(appText(R.string.copy_hourly_rate_1, clientCurrency ?: settings.currency)) },
                    placeholder = { Text(appText(R.string.copy_for_example_75_00)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .keepFocusedFieldVisible()
                        .testTag("dialog_client_rate_input"),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = invalidRate,
                    supportingText = {
                        Text(when {
                            needsPersonalRate -> appText(R.string.copy_enter_a_rate_in_the_client_s)
                            invalidRate -> appText(R.string.copy_enter_a_number_greater_than_or_equal)
                            clientCurrency == null -> appText(R.string.copy_leave_blank_or_enter_0_to_use)
                            else -> appText(R.string.copy_rate_in_the_selected_currency)
                        })
                    }
                )

                Button(onClick = { showColorPicker = true }, variant = AppAction.Secondary,
                    modifier = Modifier.testTag("client_color_picker")) {
                    Box(Modifier.size(AppSize.smallIcon).clip(CircleShape).background(safeParseColor(colorHexInput)))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(appText(R.string.copy_client_color))
                }
                if (showColorPicker) {
                    val names = listOf(appText(R.string.copy_red), appText(R.string.copy_coral), appText(R.string.copy_terracotta), appText(R.string.copy_orange), appText(R.string.copy_amber), appText(R.string.copy_sand), appText(R.string.copy_bronze), appText(R.string.copy_gold),
                        appText(R.string.copy_yellow), appText(R.string.copy_vanilla), appText(R.string.copy_mint), appText(R.string.copy_green), appText(R.string.copy_forest_green), appText(R.string.copy_turquoise), appText(R.string.copy_emerald), appText(R.string.copy_pale_turquoise),
                        appText(R.string.copy_sky_blue), appText(R.string.copy_light_blue), appText(R.string.copy_blue_teal), appText(R.string.copy_ocean_blue), appText(R.string.copy_azure), appText(R.string.copy_blue), appText(R.string.copy_violet), appText(R.string.copy_amethyst),
                        appText(R.string.copy_orchid), appText(R.string.copy_lavender), appText(R.string.copy_muted_purple), appText(R.string.copy_dusty_rose), appText(R.string.copy_pale_rose), appText(R.string.copy_pink), appText(R.string.copy_magenta), appText(R.string.copy_raspberry))
                    com.example.ui.theme.AppColorPickerSheet(colorPresets.zip(names), colorHexInput,
                        onSelect = { colorHexInput = it; showColorPicker = false }, onDismiss = { showColorPicker = false })
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                if (isEditMode) {
                    val client = state.client!!
                    val context = LocalContext.current
                    val timerState by TimerStateManager.timerState.collectAsStateWithLifecycle()
                    val isTimerActiveForClient = timerState.status != TimerStatus.IDLE && timerState.clientId == client.id

                    Button(
                        onClick = {
                            if (isTimerActiveForClient) {
                                Toast.makeText(context, appText(R.string.copy_stop_the_client_s_timer_before_deleting), Toast.LENGTH_SHORT).show()
                            } else {
                                showDeleteConfirmDialogForClient = client
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSize.touchTarget)
                            .testTag("dialog_delete_button"),
                        variant = AppAction.DestructiveQuiet
                    ) {
                        Text(appText(R.string.copy_delete_client), fontWeight = FontWeight.Bold)
                    }
                }

                com.example.ui.theme.AppFormActions {
                    Button(
                        onClick = { editorState = null },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSize.touchTarget),
                        variant = AppAction.Quiet
                    ) {
                        Text(appText(R.string.copy_cancel))
                    }

                    Button(
                        onClick = {
                            if (nameInput.isNotBlank() && !invalidRate) {
                                val parsedRate = parsedPersonalRate?.takeIf { it > 0.0 }
                                val clientToSave = state.client?.copy(
                                    name = nameInput,
                                    colorHex = colorHexInput,
                                    currencyCode = clientCurrency,
                                    defaultHourlyRate = parsedRate
                                ) ?: Client(
                                    name = nameInput,
                                    colorHex = colorHexInput,
                                    currencyCode = clientCurrency,
                                    defaultHourlyRate = parsedRate,
                                    isArchived = false
                                )
                                
                                viewModel.saveClient(clientToSave, state.client?.defaultHourlyRate, state.client?.currencyCode)
                                editorState = null
                            }
                        },
                        enabled = nameInput.isNotBlank() && !invalidRate,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSize.touchTarget)
                            .testTag("dialog_confirm_button")
                    ) {
                        Text(appText(R.string.copy_save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- Delete Client Confirm Dialog ---
    showDeleteConfirmDialogForClient?.let { client ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialogForClient = null },
            title = { Text(appText(R.string.copy_delete_this_client)) },
            text = {
                Text(appText(R.string.copy_permanently_delete_1_their_records_reports_and, client.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClient(client)
                        showDeleteConfirmDialogForClient = null
                        editorState = null // Close editor bottom sheet
                    },
                    variant = AppAction.Destructive
                ) {
                    Text(appText(R.string.copy_delete))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmDialogForClient = null },
                    variant = AppAction.Quiet
                ) {
                    Text(appText(R.string.copy_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
fun ClientCardItem(
    client: Client,
    globalHourlyRate: Double,
    currency: String,
    unpaidAmounts: Map<String, Double> = emptyMap(),
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("client_card_${client.id}")
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSize.touchTarget)
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Align the color marker with the title; the text may grow independently.
            Box(
                modifier = Modifier
                    .padding(top = AppSpacing.xs)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(safeParseColor(client.colorHex))
            )

            Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    Text(
                        text = if (client.defaultHourlyRate != null && client.defaultHourlyRate > 0.0) {
                            formatCurrency(client.defaultHourlyRate, currency) + appText(R.string.copy_hr)
                        } else {
                            formatCurrency(globalHourlyRate, currency) + appText(R.string.copy_hr)
                        },
                        style = LedgerStyles.NumberSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (unpaidAmounts.values.any { it > 0.0 }) {
                        Text(appText(R.string.copy_amount_due_n_1, formatCurrencyTotals(unpaidAmounts)),
                            style = LedgerStyles.NumberSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = AppSpacing.sm)
                                .testTag("unpaid_balance_${client.id}"))
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
