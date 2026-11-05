package com.example.habitapp.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.habitapp.R
import com.example.habitapp.presentation.components.SectionEmptyContent
import com.example.habitapp.presentation.components.TopLevelSectionScaffold

/**
 * Calendar tab. Phase 1 renders the section frame with its empty state; the
 * feature content and ViewModel replace the empty state in Phase 6.
 */
@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    TopLevelSectionScaffold(
        title = stringResource(R.string.nav_calendar),
        testTag = CalendarTestTags.SCREEN,
        modifier = modifier,
    ) { innerPadding ->
        SectionEmptyContent(
            contentPadding = innerPadding,
            iconRes = R.drawable.ic_calendar,
            title = stringResource(R.string.calendar_empty_title),
            message = stringResource(R.string.calendar_empty_message),
        )
    }
}

object CalendarTestTags {
    const val SCREEN = "screen_calendar"
}
