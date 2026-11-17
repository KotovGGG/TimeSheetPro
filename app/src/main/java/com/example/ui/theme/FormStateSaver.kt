package com.example.ui.theme

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Small form snapshots only; files and database contents must never enter the saved-state bundle. */
internal inline fun <reified T> formStateSaver(): Saver<T?, String> = Saver(
    save = { value -> value?.let { Json.encodeToString(it) } },
    restore = { Json.decodeFromString<T>(it) },
)
