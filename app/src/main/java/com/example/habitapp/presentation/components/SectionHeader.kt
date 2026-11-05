package com.example.habitapp.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.habitapp.presentation.theme.HabitTheme

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val spacing = HabitTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)
            .semantics { heading() },
    )
}
