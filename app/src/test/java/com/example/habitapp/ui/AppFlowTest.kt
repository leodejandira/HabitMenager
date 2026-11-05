package com.example.habitapp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.MainActivity
import com.example.habitapp.presentation.calendar.CalendarTestTags
import com.example.habitapp.presentation.dashboard.DashboardTestTags
import com.example.habitapp.presentation.habits.HabitsTestTags
import com.example.habitapp.presentation.journal.JournalTestTags
import com.example.habitapp.presentation.navigation.TopLevelDestination
import com.example.habitapp.presentation.navigation.navItemTestTag
import com.example.habitapp.presentation.tasks.TasksTestTags
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

    @Test
    fun allTabsOpen() {
        waitForTag(DashboardTestTags.SCREEN)
        val tags = mapOf(
            TopLevelDestination.HABITS to HabitsTestTags.SCREEN,
            TopLevelDestination.CALENDAR to CalendarTestTags.SCREEN,
            TopLevelDestination.JOURNAL to JournalTestTags.SCREEN,
            TopLevelDestination.TASKS to TasksTestTags.SCREEN,
            TopLevelDestination.TODAY to DashboardTestTags.SCREEN,
        )
        tags.forEach { (destination, tag) ->
            compose.onNodeWithTag(navItemTestTag(destination)).performClick()
            waitForTag(tag)
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun createAndCompleteBooleanHabit() {
        waitForTag("empty_new_habit")
        compose.onNodeWithTag("empty_new_habit").performClick()
        waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_name_field").performTextReplacement("Read a book")
        compose.onNodeWithTag("habit_save").performClick()
        waitForTag("habit_card_Read a book")
        // Open the detail screen and come back.
        compose.onNodeWithTag("habit_card_Read a book").performClick()
        waitForText("Current streak")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag(DashboardTestTags.SCREEN)
    }

    @Test
    fun createNumericHabitAndTask() {
        waitForTag("dashboard_fab")
        compose.onNodeWithTag("dashboard_fab").performClick()
        compose.onNodeWithTag("fab_new_habit").performClick()
        waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_name_field").performTextReplacement("Water")
        compose.onNodeWithText("Amount").performClick()
        waitForTag("habit_target_field")
        compose.onNodeWithTag("habit_target_field").performTextReplacement("2000")
        compose.onNodeWithTag("habit_save").performClick()
        waitForTag("habit_card_Water")
        compose.onNodeWithText("0 / 2000", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("dashboard_fab").performClick()
        compose.onNodeWithTag("fab_new_task").performClick()
        waitForTag("task_title_field")
        compose.onNodeWithTag("task_title_field").performTextReplacement("Buy milk")
        compose.onNodeWithTag("task_save").performClick()
        waitForTag("task_row_Buy milk")

        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.TASKS)).performClick()
        waitForTag("task_row_Buy milk")
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.CALENDAR)).performClick()
        waitForTag(CalendarTestTags.SCREEN)
        compose.onNodeWithText("Week").performClick()
        waitForText("Long-press")
        compose.onNodeWithText("Day").performClick()
    }

    @Test
    fun writeJournalEntry() {
        waitForTag(DashboardTestTags.SCREEN)
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.JOURNAL)).performClick()
        waitForTag("journal_fab")
        compose.onNodeWithTag("journal_fab").performClick()
        waitForTag("journal_title_field")
        compose.onNodeWithTag("journal_title_field").performTextReplacement("First day")
        compose.onNodeWithTag("journal_content_field").performTextReplacement("Felt **great** today")
        compose.onNodeWithTag("journal_save").performClick()
        waitForText("First day")
        // The list renders the formatting markers as styles, so the plain text is shown.
        waitForText("Felt great today")
        compose.onNodeWithText("Felt great today", substring = true).assertIsDisplayed()
    }

    @Test
    fun secondaryScreensOpen() {
        waitForTag(DashboardTestTags.SCREEN)
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.HABITS)).performClick()
        waitForTag(HabitsTestTags.SCREEN)
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Habit ideas")).performClick()
        waitForText("Drink water")
        compose.onNodeWithText("Drink water").performClick()
        waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_save").performClick()
        waitForTag(HabitsTestTags.SCREEN)
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Statistics")).performClick()
        waitForText("By habit")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Routines")).performClick()
        waitForText("No routines yet")
    }
}
