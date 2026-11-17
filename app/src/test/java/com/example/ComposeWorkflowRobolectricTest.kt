package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TimeSheetApp::class, sdk = [36])
class ComposeWorkflowRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun navigationBetweenAllFourScreensWorksFlawlessly() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppNavigation()
            }
        }

        // 1. Initial destination is Timer
        composeTestRule.onNodeWithTag("nav_item_timer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_records").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_clients").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_settings").assertIsDisplayed()

        // 2. Navigate to Clients screen
        composeTestRule.onNodeWithTag("nav_item_clients").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screen_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_client_fab").assertIsDisplayed()

        // 3. Navigate to Settings screen
        composeTestRule.onNodeWithTag("nav_item_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_scroll").assertIsDisplayed()
        composeTestRule.onNodeWithText("Оплата").assertIsDisplayed()
        composeTestRule.onNodeWithText("Расчёты").assertIsDisplayed()

        // 4. Navigate to Records screen
        composeTestRule.onNodeWithTag("nav_item_records").performClick()
        composeTestRule.waitForIdle()

        // 5. Navigate back to Timer screen
        composeTestRule.onNodeWithTag("nav_item_timer").performClick()
        composeTestRule.waitForIdle()
    }
}
