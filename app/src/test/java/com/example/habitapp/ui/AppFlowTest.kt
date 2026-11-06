package com.example.habitapp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.MainActivity
import com.example.habitapp.presentation.calendar.CalendarTestTags
import com.example.habitapp.presentation.home.HomeTestTags
import com.example.habitapp.presentation.journal.JournalTestTags
import com.example.habitapp.presentation.navigation.TopLevelDestination
import com.example.habitapp.presentation.navigation.navItemTestTag
import com.example.habitapp.presentation.settings.SettingsTestTags
import com.example.habitapp.presentation.timers.TimerTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end flows on the real app (Hilt, Room, DataStore) running under Robolectric.
 * These catch runtime crashes in navigation, database access and screen composition.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun tab(destination: TopLevelDestination) = compose.onNodeWithTag(navItemTestTag(destination)).performClick()

    @Test
    fun allTabsOpen() {
        waitForTag(HomeTestTags.SCREEN)
        mapOf(
            TopLevelDestination.JOURNAL to JournalTestTags.SCREEN,
            TopLevelDestination.CALENDAR to CalendarTestTags.SCREEN,
            TopLevelDestination.SETTINGS to SettingsTestTags.SCREEN,
            TopLevelDestination.TODAY to HomeTestTags.SCREEN,
        ).forEach { (destination, tag) ->
            tab(destination)
            waitForTag(tag)
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun createCustomCountHabitAndOpenDetail() {
        waitForTag("empty_new_habit")
        compose.onNodeWithTag("empty_new_habit").performClick()
        waitForTag("habit_custom")
        compose.onNodeWithTag("habit_custom").performClick()
        waitForTag("type_count")
        compose.onNodeWithTag("type_count").performClick()
        waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_name_field").performTextReplacement("Read a book")
        compose.onNodeWithTag("habit_target_plus").performScrollTo().performClick()
        compose.onNodeWithTag("habit_target_plus").performClick()
        compose.onNodeWithTag("habit_save").performClick()
        waitForTag("habit_card_Read a book")
        compose.onNodeWithText("0 / 3", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("habit_card_Read a book").performClick()
        waitForTag("detail_plus")
        compose.onNodeWithTag("detail_plus").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("1", substring = false)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("detail_tab_STATS").performClick()
        compose.onNodeWithTag("detail_tab_LOG").performClick()
        waitForText("Export CSV")
        back()
        waitForTag(HomeTestTags.SCREEN)
        waitForText("1 / 3")
    }

    @Test
    fun createHabitFromSuggestionAndQuickTask() {
        waitForTag("dashboard_fab")
        compose.onNodeWithTag("dashboard_fab").performClick()
        compose.onNodeWithTag("fab_new_habit").performClick()
        waitForTag("suggestion_water")
        compose.onNodeWithTag("suggestion_water").performClick()
        waitForTag("habit_save")
        compose.onNodeWithTag("habit_save").performClick()
        waitForTag("habit_card_Drink water")

        compose.onNodeWithTag("dashboard_fab").performClick()
        compose.onNodeWithTag("fab_new_task").performClick()
        waitForTag("quick_task_title")
        compose.onNodeWithTag("quick_task_title").performTextReplacement("Buy milk")
        compose.onNodeWithTag("quick_task_send").performClick()
        waitForTag("task_row_Buy milk")

        tab(TopLevelDestination.CALENDAR)
        waitForTag(CalendarTestTags.SCREEN)
        waitForText("Week")
        compose.onNodeWithText("Week").performClick()
        waitForText("Long-press")
        compose.onNodeWithText("Day").performClick()
    }

    @Test
    fun writeJournalEntry() {
        waitForTag(HomeTestTags.SCREEN)
        tab(TopLevelDestination.JOURNAL)
        waitForTag("journal_fab")
        compose.onNodeWithTag("journal_fab").performClick()
        waitForTag("journal_title_field")
        compose.onNodeWithTag("journal_title_field").performTextReplacement("First day")
        compose.onNodeWithTag("journal_content_field").performTextReplacement("Felt **great** today")
        compose.onNodeWithTag("journal_save").performClick()
        waitForText("First day")
        waitForText("Felt great today")
        compose.onNodeWithText("Felt great today", substring = true).assertIsDisplayed()
    }

    @Test
    fun secondaryScreensOpen() {
        waitForTag(HomeTestTags.SCREEN)
        waitForTag("home_reports")
        compose.waitForIdle()
        compose.onNodeWithTag("home_reports", useUnmergedTree = true).performClick()
        waitForText("Save image")
        back()
        waitForTag("home_timer")
        compose.onNodeWithTag("home_timer").performClick()
        waitForText("Create a habit first")
        back()
        waitForTag(HomeTestTags.SCREEN)
        compose.onNodeWithTag("home_menu").performClick()
        waitForTag("drawer_all_habits")
        compose.onNodeWithTag("drawer_all_habits").performClick()

        tab(TopLevelDestination.SETTINGS)
        waitForTag("settings_link_achievements")
        compose.onNodeWithTag("settings_link_achievements").performClick()
        waitForText("unlocked")
        back()
        waitForTag("settings_link_archived")
        compose.onNodeWithTag("settings_link_archived").performClick()
        waitForText("No archived habits")
        back()
        waitForTag("settings_link_routines")
        compose.onNodeWithTag("settings_link_routines").performClick()
        waitForText("No routines yet")
        back()
        waitForTag("settings_link_statistics")
        compose.onNodeWithTag("settings_link_statistics").performClick()
        waitForText("By habit")
    }

    @Test
    fun timerRunsForHabit() {
        waitForTag("empty_new_habit")
        compose.onNodeWithTag("empty_new_habit").performClick()
        waitForTag("suggestion_meditate")
        compose.onNodeWithTag("suggestion_meditate").performClick()
        waitForTag("habit_save")
        compose.onNodeWithTag("habit_save").performClick()
        waitForTag("home_timer")
        compose.onNodeWithTag("home_timer").performClick()
        waitForTag(TimerTestTags.START)
        compose.onNodeWithTag(TimerTestTags.START).performClick()
        waitForTag(TimerTestTags.LAP)
        compose.onNodeWithTag(TimerTestTags.LAP).performClick()
        waitForText("Lap 1")
        compose.onNodeWithTag(TimerTestTags.SAVE).performClick()
        waitForTag(TimerTestTags.START)
    }
}
