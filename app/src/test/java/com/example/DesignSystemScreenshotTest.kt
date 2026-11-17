package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TimeSheetApp::class, qualifiers = "w412dp-h915dp-xxhdpi", sdk = [36])
class DesignSystemScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun fixedFixtures() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val db = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext())
        db.clientDao().insertClient(Client(id = 101, name = "Студия Север", colorHex = "#D0BCFF", defaultHourlyRate = 2500.0, createdAt = 1))
        db.clientDao().insertClient(Client(id = 102, name = "Международная студия архитектуры и проектирования", colorHex = "#52B788", defaultHourlyRate = 3500.0, createdAt = 1))
        db.timeEntryDao().insertTimeEntry(TimeEntry(id = 201, clientId = 101, startTime = 1700000000000,
            endTime = 1700003600000, durationSeconds = 3600, rawDurationSeconds = 3600,
            comment = "Разработка интерфейса", appliedRate = 2500.0, amount = 2500.0,
            status = TimeEntryStatus.NOT_PAID, createdAt = 1, updatedAt = 1))
        db.timeEntryDao().insertTimeEntry(TimeEntry(id = 202, clientId = 102, startTime = 1700003600000,
            endTime = 1700007200000, durationSeconds = 3600, rawDurationSeconds = 3600,
            comment = "Большая сумма и длинное название", appliedRate = 9876543.21, amount = 9876543.21,
            status = TimeEntryStatus.PAID, createdAt = 1, updatedAt = 1))
        db.appSettingsDao().saveSettings(AppSettings(globalHourlyRate = 1500.0, currency = "RUB"))
    }

    @Test fun mainScreensInBothThemes() {
        captureMainScreens()
    }

    @Test fun mainScreensWithLargeText() {
        captureMainScreens(fontScale = 2f, suffix = "-large-text")
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun mainScreensInLandscape() {
        captureMainScreens(suffix = "-landscape")
    }

    private fun captureMainScreens(fontScale: Float = 1f, suffix: String = "") {
        val dark = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme(dark.value) { MainAppNavigation() }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            for (route in listOf("timer", "records", "clients", "settings")) {
                compose.onNodeWithTag("nav_item_$route").performClick()
                if (route == "records") {
                    compose.onNodeWithContentDescription("Фильтры").performClick()
                    compose.onNodeWithText("Все").performClick()
                    compose.onNodeWithText("Применить").performScrollTo().performClick()
                    compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_202"))
                }
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                compose.onNodeWithTag("nav_item_$route").assertIsSelected()
                compose.onRoot().captureRoboImage("src/test/screenshots/design-system/${if (night) "dark" else "light"}-$route$suffix.png")
            }
        }
    }

    @Test fun manualEntryCanBeOpenedAndDismissed() {
        compose.setContent { MyApplicationTheme { MainAppNavigation() } }
        compose.onNodeWithTag("manual_entry_button").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("manual_entry_button").assertIsDisplayed()
    }
}
