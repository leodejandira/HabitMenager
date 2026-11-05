package com.example.habitapp.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habitapp.MainActivity
import com.example.habitapp.R
import com.example.habitapp.presentation.calendar.CalendarTestTags
import com.example.habitapp.presentation.dashboard.DashboardTestTags
import com.example.habitapp.presentation.habits.HabitsTestTags
import com.example.habitapp.presentation.journal.JournalTestTags
import com.example.habitapp.presentation.navigation.TopLevelDestination
import com.example.habitapp.presentation.navigation.navItemTestTag
import com.example.habitapp.presentation.settings.SettingsTestTags
import com.example.habitapp.presentation.tasks.TasksTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val screenTags = mapOf(
        TopLevelDestination.TODAY to DashboardTestTags.SCREEN,
        TopLevelDestination.HABITS to HabitsTestTags.SCREEN,
        TopLevelDestination.CALENDAR to CalendarTestTags.SCREEN,
        TopLevelDestination.JOURNAL to JournalTestTags.SCREEN,
        TopLevelDestination.TASKS to TasksTestTags.SCREEN,
    )

    @Test
    fun appStartsOnToday() {
        composeRule.waitForTag(DashboardTestTags.SCREEN)
        composeRule.onNodeWithTag(DashboardTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun everyBottomTabOpensItsScreen() {
        composeRule.waitForTag(DashboardTestTags.SCREEN)
        TopLevelDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(navItemTestTag(destination)).performClick()
            composeRule.onNodeWithTag(screenTags.getValue(destination)).assertIsDisplayed()
        }
    }

    @Test
    fun settingsOpensFromTodayAndNavigatesBack() {
        composeRule.waitForTag(DashboardTestTags.SCREEN)
        composeRule.onNodeWithContentDescription(context.getString(R.string.dashboard_settings_cd))
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()
        composeRule.onNodeWithTag(DashboardTestTags.SCREEN).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
