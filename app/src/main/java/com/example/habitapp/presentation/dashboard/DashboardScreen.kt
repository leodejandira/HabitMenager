package com.example.habitapp.presentation.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.util.DayPeriod
import com.example.habitapp.presentation.components.EmptyState
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(uiState = uiState, onOpenSettings = onOpenSettings)
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.testTag(DashboardTestTags.SCREEN)) { innerPadding ->
        when (uiState) {
            DashboardUiState.Loading -> FullScreenLoading(Modifier.padding(innerPadding))
            DashboardUiState.Error -> FullScreenError(
                message = stringResource(R.string.error_loading),
                modifier = Modifier.padding(innerPadding),
            )
            is DashboardUiState.Ready -> DashboardReady(
                state = uiState,
                contentPadding = innerPadding,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun DashboardReady(
    state: DashboardUiState.Ready,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val spacing = HabitTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            DashboardHeaderRow(header = state.header, onOpenSettings = onOpenSettings)
        }
        item(key = "empty") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.lg),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_today,
                    title = stringResource(R.string.dashboard_empty_title),
                    message = stringResource(R.string.dashboard_empty_message),
                )
            }
        }
    }
}

@Composable
private fun DashboardHeaderRow(header: DashboardHeader, onOpenSettings: () -> Unit) {
    val spacing = HabitTheme.spacing
    val locale = currentLocale()
    val dayOfWeek = remember(header.date, locale) {
        header.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }
    val formattedDate = remember(header.date, locale) {
        header.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.screenHorizontal, end = spacing.sm, top = spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dayOfWeek.uppercase(locale),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = greeting(header.dayPeriod, header.displayName),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = spacing.xs)
                    .semantics { heading() },
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.dashboard_settings_cd),
            )
        }
    }
}

@Composable
private fun greeting(period: DayPeriod, name: String): String {
    val hasName = name.isNotBlank()
    return when (period) {
        DayPeriod.MORNING ->
            if (hasName) stringResource(R.string.greeting_morning_named, name) else stringResource(R.string.greeting_morning)
        DayPeriod.AFTERNOON ->
            if (hasName) stringResource(R.string.greeting_afternoon_named, name) else stringResource(R.string.greeting_afternoon)
        DayPeriod.EVENING ->
            if (hasName) stringResource(R.string.greeting_evening_named, name) else stringResource(R.string.greeting_evening)
        DayPeriod.NIGHT ->
            if (hasName) stringResource(R.string.greeting_night_named, name) else stringResource(R.string.greeting_night)
    }
}

object DashboardTestTags {
    const val SCREEN = "screen_today"
}
