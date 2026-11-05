package com.example.habitapp.presentation.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    @Test
    fun `render strips markers and applies styles`() {
        val rendered = RichText.render("Hello **bold** *it* __under__")
        assertEquals("Hello bold it under", rendered.text)
        val styles = rendered.spanStyles
        assertTrue(styles.any { it.item.fontWeight == FontWeight.Bold && rendered.text.substring(it.start, it.end) == "bold" })
        assertTrue(styles.any { it.item.fontStyle == FontStyle.Italic && rendered.text.substring(it.start, it.end) == "it" })
        assertTrue(styles.any { it.item.textDecoration == TextDecoration.Underline && rendered.text.substring(it.start, it.end) == "under" })
    }

    @Test
    fun `render lists and headings`() {
        val plain = RichText.plain("# Title\n- one\n2. two")
        assertEquals("Title\n  •  one\n  2.  two", plain)
    }

    @Test
    fun `toggle inline wraps and unwraps the selection`() {
        val value = TextFieldValue("make this bold", TextRange(10, 14))
        val wrapped = RichText.toggleInline(value, RichText.Inline.BOLD)
        assertEquals("make this **bold**", wrapped.text)
        val unwrapped = RichText.toggleInline(wrapped, RichText.Inline.BOLD)
        assertEquals("make this bold", unwrapped.text)
    }

    @Test
    fun `toggle numbered list numbers every selected line`() {
        val value = TextFieldValue("milk\neggs\nbread", TextRange(0, 15))
        val numbered = RichText.toggleLinePrefix(value, RichText.LinePrefix.NUMBERED)
        assertEquals("1. milk\n2. eggs\n3. bread", numbered.text)
        val removed = RichText.toggleLinePrefix(numbered.copy(selection = TextRange(0, numbered.text.length)), RichText.LinePrefix.NUMBERED)
        assertEquals("milk\neggs\nbread", removed.text)
    }
}
