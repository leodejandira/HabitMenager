package com.example.habitapp.presentation.habits

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.habitapp.R
import com.example.habitapp.presentation.components.SectionEmptyContent
import com.example.habitapp.presentation.components.TopLevelSectionScaffold

/**
 * Habits tab. Phase 1 renders the section frame with its empty state; the
 * feature content and ViewModel replace the empty state in Phase 3.
 */
@Composable
fun HabitsScreen(modifier: Modifier = Modifier) {
    TopLevelSectionScaffold(
        title = stringResource(R.string.nav_habits),
        testTag = HabitsTestTags.SCREEN,
        modifier = modifier,
    ) { innerPadding ->
        SectionEmptyContent(
            contentPadding = innerPadding,
            iconRes = R.drawable.ic_habits,
            title = stringResource(R.string.habits_empty_title),
            message = stringResource(R.string.habits_empty_message),
        )
    }
}

object HabitsTestTags {
    const val SCREEN = "screen_habits"
}
