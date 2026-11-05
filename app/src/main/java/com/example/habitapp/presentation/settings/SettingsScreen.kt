package com.example.habitapp.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.BuildConfig
import com.example.habitapp.R
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.theme.HabitTheme
import com.example.habitapp.presentation.theme.supportsDynamicColor
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        actions = SettingsActions(
            onNavigateBack = onNavigateBack,
            onThemeModeSelected = viewModel::onThemeModeSelected,
            onDynamicColorToggled = viewModel::onDynamicColorToggled,
            onFirstDayOfWeekSelected = viewModel::onFirstDayOfWeekSelected,
            onDisplayNameSaved = viewModel::onDisplayNameSaved,
            onUserMessageShown = viewModel::onUserMessageShown,
        ),
    )
}

class SettingsActions(
    val onNavigateBack: () -> Unit,
    val onThemeModeSelected: (ThemeMode) -> Unit,
    val onDynamicColorToggled: (Boolean) -> Unit,
    val onFirstDayOfWeekSelected: (DayOfWeek?) -> Unit,
    val onDisplayNameSaved: (String) -> Unit,
    val onUserMessageShown: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageRes = (uiState as? SettingsUiState.Ready)?.userMessage
    val messageText = messageRes?.let { stringResource(it) }
    LaunchedEffect(messageRes) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onUserMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.testTag(SettingsTestTags.SCREEN),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            SettingsUiState.Loading -> FullScreenLoading(Modifier.padding(innerPadding))
            SettingsUiState.Error -> FullScreenError(
                message = stringResource(R.string.error_loading),
                modifier = Modifier.padding(innerPadding),
            )
            is SettingsUiState.Ready -> SettingsList(
                preferences = uiState.preferences,
                contentPadding = innerPadding,
                actions = actions,
            )
        }
    }
}

@Composable
private fun SettingsList(
    preferences: UserPreferences,
    contentPadding: PaddingValues,
    actions: SettingsActions,
) {
    val spacing = HabitTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "appearance") {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            ThemeModeSelector(
                selected = preferences.themeMode,
                onSelected = actions.onThemeModeSelected,
            )
            if (supportsDynamicColor()) {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = preferences.useDynamicColor,
                    onCheckedChange = actions.onDynamicColorToggled,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = spacing.sm))
        }
        item(key = "calendar") {
            SectionHeader(stringResource(R.string.settings_section_calendar))
            WeekStartSelector(
                selected = preferences.firstDayOfWeek,
                onSelected = actions.onFirstDayOfWeekSelected,
            )
            HorizontalDivider(Modifier.padding(vertical = spacing.sm))
        }
        item(key = "profile") {
            SectionHeader(stringResource(R.string.settings_section_profile))
            DisplayNameEditor(
                savedName = preferences.displayName,
                onSave = actions.onDisplayNameSaved,
            )
            HorizontalDivider(Modifier.padding(vertical = spacing.sm))
        }
        item(key = "about") {
            SectionHeader(stringResource(R.string.settings_section_about))
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = spacing.screenHorizontal,
                    vertical = spacing.sm,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val spacing = HabitTheme.spacing
    val options = ThemeMode.entries
    Text(
        text = stringResource(R.string.settings_theme),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.testTag("${SettingsTestTags.THEME_PREFIX}${mode.name}"),
            ) {
                Text(stringResource(mode.labelRes()))
            }
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = HabitTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row is toggleable, so the switch itself is not separately clickable.
        Switch(checked = checked, onCheckedChange = null)
    }
}

private val WeekStartOptions: List<DayOfWeek?> =
    listOf(null, DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)

@Composable
private fun WeekStartSelector(selected: DayOfWeek?, onSelected: (DayOfWeek?) -> Unit) {
    val spacing = HabitTheme.spacing
    val locale = currentLocale()
    Text(
        text = stringResource(R.string.settings_week_start),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    )
    Column(Modifier.selectableGroup()) {
        WeekStartOptions.forEach { option ->
            val label = if (option == null) {
                val regionDay = resolveFirstDayOfWeek(null, locale).getDisplayName(TextStyle.FULL, locale)
                stringResource(R.string.week_start_region_default, regionDay)
            } else {
                option.getDisplayName(TextStyle.FULL, locale)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    )
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = spacing.md),
                )
            }
        }
    }
}

@Composable
private fun DisplayNameEditor(savedName: String, onSave: (String) -> Unit) {
    val spacing = HabitTheme.spacing
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable(savedName) { mutableStateOf(savedName) }
    val canSave = draft.trim() != savedName

    fun save() {
        if (canSave) onSave(draft)
        focusManager.clearFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(UserPreferences.MAX_DISPLAY_NAME_LENGTH) },
            label = { Text(stringResource(R.string.settings_display_name)) },
            supportingText = { Text(stringResource(R.string.settings_display_name_supporting)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .weight(1f)
                .testTag(SettingsTestTags.NAME_FIELD),
        )
        TextButton(
            onClick = { save() },
            enabled = canSave,
            modifier = Modifier.padding(start = spacing.sm),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

object SettingsTestTags {
    const val SCREEN = "screen_settings"
    const val THEME_PREFIX = "theme_"
    const val NAME_FIELD = "settings_name_field"
}
