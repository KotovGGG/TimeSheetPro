package com.example.ui.screens.records

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.ui.theme.*

/** A bounded resolution is decoded off main; the stored attachment remains untouched. */
@Composable
internal fun ReceiptViewer(uri: String, onDismiss: () -> Unit) {
    val preview = receiptPreview(uri, maxDimension = 2048)
    var scale by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val imageSize = (preview as? ReceiptPreview.Ready)?.bitmap?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
    fun adjustScale(value: Float, pan: Offset = Offset.Zero) {
        scale = value.coerceIn(1f, 5f)
        val limit = receiptPanLimit(viewport, imageSize, scale)
        offset = Offset((offset.x + pan.x).coerceIn(-limit.x, limit.x),
            (offset.y + pan.y).coerceIn(-limit.y, limit.y))
    }
    LaunchedEffect(viewport, imageSize) { adjustScale(scale) }
    val transform = rememberTransformableState { _, zoomChange, panChange, _ -> adjustScale(scale * zoomChange, panChange) }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val dark = LocalAppDarkTheme.current
        DisposableEffect(view, dark) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            onDispose { }
        }
        Surface(Modifier.fillMaxSize().testTag("receipt_viewer"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.systemBarsPadding().padding(AppSpacing.lg)) {
                AppSheetHeader("Чек оплаты · ${(scale * 100).toInt()}%", onDismiss)
                Box(Modifier.weight(1f).fillMaxWidth().testTag("receipt_viewport").clipToBounds().onSizeChanged { viewport = it }
                    .transformable(transform), contentAlignment = Alignment.Center) {
                    when (preview) {
                        is ReceiptPreview.Ready -> Image(preview.bitmap, "Чек целиком", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                            })
                        ReceiptPreview.Loading -> CircularProgressIndicator()
                        ReceiptPreview.Unavailable -> Text("Не удалось открыть чек", style = MaterialTheme.typography.bodyMedium)
                        ReceiptPreview.Empty -> Unit
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Row {
                        AppIconAction("Уменьшить чек", Icons.Default.Remove, { adjustScale(scale / 1.5f) }, enabled = preview is ReceiptPreview.Ready && scale > 1f)
                        AppIconAction("Увеличить чек", Icons.Default.Add, { adjustScale(scale * 1.5f) }, enabled = preview is ReceiptPreview.Ready && scale < 5f)
                    }
                    AppButton(onClick = { scale = 1f; offset = Offset.Zero }, variant = AppAction.Quiet, enabled = scale > 1f) {
                        Text("Сбросить масштаб")
                    }
                }
            }
        }
    }
}
