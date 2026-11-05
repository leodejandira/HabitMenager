package com.example.habitapp.presentation.journal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.habitapp.R
import com.example.habitapp.presentation.components.SectionEmptyContent
import com.example.habitapp.presentation.components.TopLevelSectionScaffold

/**
 * Journal tab. Phase 1 renders the section frame with its empty state; the
 * feature content and ViewModel replace the empty state in Phase 6.
 */
@Composable
fun JournalScreen(modifier: Modifier = Modifier) {
    TopLevelSectionScaffold(
        title = stringResource(R.string.nav_journal),
        testTag = JournalTestTags.SCREEN,
        modifier = modifier,
    ) { innerPadding ->
        SectionEmptyContent(
            contentPadding = innerPadding,
            iconRes = R.drawable.ic_journal,
            title = stringResource(R.string.journal_empty_title),
            message = stringResource(R.string.journal_empty_message),
        )
    }
}

object JournalTestTags {
    const val SCREEN = "screen_journal"
}
