package com.example.habitapp.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.MainActivity
import com.example.habitapp.presentation.home.HomeTestTags
import com.example.habitapp.presentation.navigation.TopLevelDestination
import com.example.habitapp.presentation.navigation.navItemTestTag
import com.example.habitapp.presentation.timers.TimerTestTags
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens to PNG files for visual review (dark theme, Portuguese, like the reference prints).
 * Only runs when the `steady.screenshots` system property points to an output directory.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val outDir: File? = System.getProperty("steady.screenshots")?.let(::File)

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        val dir = outDir ?: return
        dir.mkdirs()
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        val bitmap = compose.onNode(isRoot()).captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun openHabitCreation() {
        waitForTag("dashboard_fab")
        compose.onNodeWithTag("dashboard_fab").performClick()
        compose.onNodeWithTag("fab_new_habit").performClick()
    }

    private fun saveHabit() {
        compose.onNodeWithTag("habit_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("habit_save")).fetchSemanticsNodes().isEmpty() }
        waitForTag(HomeTestTags.SCREEN)
    }

    private fun suggestion(id: String) {
        openHabitCreation()
        waitForTag("suggestion_$id")
        compose.onNodeWithTag("suggestion_$id").performScrollTo().performClick()
        waitForTag("habit_save")
        saveHabit()
    }

    @Test
    fun captureScreens() {
        Assume.assumeTrue(outDir != null)
        waitForTag(HomeTestTags.SCREEN)
        shot("01_hoje_vazio")
        openHabitCreation()
        waitForTag("habit_custom")
        shot("02_criar_sugestoes")
        compose.onNodeWithTag("habit_custom").performClick()
        waitForTag("type_count")
        shot("03_tipo")
        compose.onNodeWithTag("type_count").performClick()
        waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_name_field").performTextReplacement("Ler 20 páginas")
        shot("04_editor")
        saveHabit()
        suggestion("water")
        suggestion("meditate")
        suggestion("exercise")
        compose.onNodeWithTag("dashboard_fab").performClick()
        shot("05_fab")
        compose.onNodeWithTag("fab_new_task").performClick()
        waitForTag("quick_task_title")
        compose.onNodeWithTag("quick_task_title").performTextReplacement("Pagar conta de luz")
        shot("06_tarefa_rapida")
        compose.onNodeWithTag("quick_task_send").performClick()
        waitForTag("task_row_Pagar conta de luz")
        shot("07_hoje")
        compose.onNodeWithTag("home_menu").performClick()
        waitForTag("drawer_all_habits")
        shot("08_menu")
        back()
        compose.onNodeWithTag("habit_card_Ler 20 páginas").performClick()
        waitForTag("detail_plus")
        compose.onNodeWithTag("detail_plus").performClick()
        shot("09_detalhe")
        compose.onNodeWithTag("detail_tab_STATS").performClick()
        shot("10_detalhe_estatisticas")
        back()
        waitForTag("home_reports")
        compose.onNodeWithTag("home_reports").performClick()
        compose.waitForIdle()
        shot("11_relatorios")
        back()
        waitForTag("home_timer")
        compose.onNodeWithTag("home_timer").performClick()
        waitForTag(TimerTestTags.START)
        shot("12_timer")
        back()
        waitForTag(HomeTestTags.SCREEN)
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.CALENDAR)).performClick()
        compose.waitForIdle()
        shot("13_calendario")
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.JOURNAL)).performClick()
        waitForTag("journal_fab")
        shot("14_diario")
        compose.onNodeWithTag(navItemTestTag(TopLevelDestination.SETTINGS)).performClick()
        waitForTag("settings_link_archived")
        shot("15_ajustes")
    }
}
