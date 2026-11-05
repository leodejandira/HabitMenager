package com.example.habitapp.presentation.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.habitapp.R
import com.example.habitapp.presentation.components.SectionEmptyContent
import com.example.habitapp.presentation.components.TopLevelSectionScaffold

/**
 * Tasks tab. Phase 1 renders the section frame with its empty state; the
 * feature content and ViewModel replace the empty state in Phase 5.
 */
@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    TopLevelSectionScaffold(
        title = stringResource(R.string.nav_tasks),
        testTag = TasksTestTags.SCREEN,
        modifier = modifier,
    ) { innerPadding ->
        SectionEmptyContent(
            contentPadding = innerPadding,
            iconRes = R.drawable.ic_tasks,
            title = stringResource(R.string.tasks_empty_title),
            message = stringResource(R.string.tasks_empty_message),
        )
    }
}

object TasksTestTags {
    const val SCREEN = "screen_tasks"
}
