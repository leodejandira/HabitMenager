package com.example.habitapp.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.MainActivity
import com.example.habitapp.presentation.calendar.CalendarTestTags
import com.example.habitapp.presentation.home.HomeTestTags
import com.example.habitapp.presentation.journal.JournalTestTags
import com.example.habitapp.presentation.navigation.TopLevelDestination
import com.example.habitapp.presentation.navigation.navItemTestTag
import com.example.habitapp.presentation.settings.SettingsTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val screenTags = mapOf(
        TopLevelDestination.TODAY to HomeTestTags.SCREEN,
        TopLevelDestination.JOURNAL to JournalTestTags.SCREEN,
        TopLevelDestination.CALENDAR to CalendarTestTags.SCREEN,
        TopLevelDestination.SETTINGS to SettingsTestTags.SCREEN,
    )

    @Test
    fun appStartsOnToday() {
        waitForTag(HomeTestTags.SCREEN)
        composeRule.onNodeWithTag(HomeTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun everyBottomTabOpensItsScreen() {
        waitForTag(HomeTestTags.SCREEN)
        TopLevelDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(navItemTestTag(destination)).performClick()
            waitForTag(screenTags.getValue(destination))
            composeRule.onNodeWithTag(screenTags.getValue(destination)).assertIsDisplayed()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
