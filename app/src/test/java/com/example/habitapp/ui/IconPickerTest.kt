package com.example.habitapp.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.presentation.components.ListIconCatalog
import com.example.habitapp.presentation.components.ListIconPickerDialog
import com.example.habitapp.presentation.theme.HabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class IconPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalogHasManyUniqueIcons() {
        val keys = ListIconCatalog.categories.flatMap { c -> c.second.map { it.first } }
        assertTrue("expected a large icon set, got ${keys.size}", keys.size >= 200)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun pickerShowsFirstIcons() {
        var picked: String? = null
        compose.setContent { HabitTheme(darkTheme = true) { ListIconPickerDialog("teal", { picked = it }, {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("list_icon_checklist").fetchSemanticsNodes().isNotEmpty() }
        System.getProperty("steady.screenshots")?.let { dir ->
            val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            val image = compose.onAllNodes(isRoot())[roots.size - 1].captureToImage().asAndroidBitmap()
            File(dir).mkdirs()
            File(dir, "16_icones.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
