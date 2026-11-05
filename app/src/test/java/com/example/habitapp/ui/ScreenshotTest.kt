package com.example.habitapp.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.MainActivity
import com.example.habitapp.presentation.navigation.TopLevelDestination
import com.example.habitapp.presentation.navigation.navItemTestTag
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens to PNG files for visual review. Only runs when the
 * `steady.screenshots` system property points to an output directory.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val outDir: File? = System.getProperty("steady.screenshots")?.let(::File)

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shot(name: String) {
        val dir = outDir ?: return
        dir.mkdirs()
        compose.waitForIdle()
        val bitmap = compose.onNode(isRoot()).captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun createHabit(name: String, typeLabel: String? = null, target: String? = null) {
        compose.onNodeWithTag("dashboard_fab").performClick()
        compose.onNodeWithTag("fab_new_habit").performClick()
        waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_name_field").performTextReplacement(name)
        if (typeLabel != null) compose.onNodeWithText(typeLabel).performClick()
        if (target != null) {
            waitForTag("habit_target_field")
            compose.onNodeWithTag("habit_target_field").performTextReplacement(target)
        }
        compose.onNodeWithTag("habit_save").performClick()
        waitForTag("habit_card_$name")
    }

    @Test
    fun captureScreens() {
        Assume.assumeTrue(outDir != null)
        waitForTag("dashboard_fab")
        shot("01_today_empty")
        createHabit("Read 20 pages")
        createHabit("Drink water", "Amount", "2000")
        createHabit("Meditate", "Duration", "10")
        createHabit("No smoking", "Quit")
        compose.onNodeWithTag("dashboard_fab").performClick()
        compose.onNodeWithTag("fab_new_task").performClick()
        waitForTag("task_title_field")
        compose.onNodeWithTag("task_title_field").performTextReplacement("Pay electricity bill")
        compose.onNodeWithText("High").performClick()
        compose.onNodeWithTag("task_save").performClick()
        waitForTag("task_row_Pay electricity bill")
        compose.onNodeWithTag("habit_card_Read 20 pages").let { }
        shot("02_today_filled")
        compose.onNodeWithTag("habit_card_Drink water").performClick()
        waitForText("Current streak")
        shot("03_habit_detail")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.HABITS)).performClick()
        waitForText("Read 20 pages")
        shot("04_habits")
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.CALENDAR)).performClick()
        waitForText("Month")
        shot("05_calendar")
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.TASKS)).performClick()
        waitForTag("task_row_Pay electricity bill")
        shot("06_tasks")
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.JOURNAL)).performClick()
        waitForTag("journal_fab")
        shot("07_journal")
        compose.onNodeWithTag("journal_fab").performClick()
        waitForTag("journal_title_field")
        shot("08_journal_editor")
    }
}
