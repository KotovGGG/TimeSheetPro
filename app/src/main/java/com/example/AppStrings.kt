
package com.example

import android.app.Application
import androidx.annotation.StringRes
import java.util.Locale

private lateinit var application: Application

internal fun initializeAppStrings(app: Application) {
    application = app
}

internal fun appText(@StringRes id: Int, vararg args: Any?): String =
    if (args.isEmpty()) application.getString(id) else application.getString(id, *args)

internal fun appLocale(): Locale = application.resources.configuration.locales[0]
