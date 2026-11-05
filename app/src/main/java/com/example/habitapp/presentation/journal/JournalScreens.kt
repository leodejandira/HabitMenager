package com.example.habitapp.presentation.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.JournalFilter
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.components.EmptyState
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.RichText
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.format.FormatStyle

val Moods = listOf("😞", "😕", "😐", "🙂", "😄")

@Composable
private fun journalFilterLabel(f: JournalFilter) = stringResource(
    when (f) {
        JournalFilter.ALL -> R.string.filter_all
        JournalFilter.DAILY -> R.string.journal_daily
        JournalFilter.HABIT_NOTES -> R.string.journal_habit_notes
        JournalFilter.BOOKMARKED -> R.string.journal_bookmarked
    },
)

@Composable
private fun rangeLabel(r: JournalRange) = stringResource(
    when (r) {
        JournalRange.ALL_TIME -> R.string.range_all_time
        JournalRange.LAST_7_DAYS -> R.string.range_7_days
        JournalRange.LAST_30_DAYS -> R.string.range_30_days
        JournalRange.THIS_YEAR -> R.string.range_this_year
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onNewEntry: () -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    var habitMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag(JournalTestTags.SCREEN),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_journal)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewEntry, modifier = Modifier.testTag("journal_fab")) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.journal_new))
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.journal_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_clear)) }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.sm),
                )
            }
            val ready = state as? JournalUiState.Ready
            item(key = "filters") {
                LazyRow(contentPadding = PaddingValues(horizontal = HabitTheme.spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                    items(JournalFilter.entries) { f ->
                        FilterChip(selected = ready?.filter == f, onClick = { viewModel.setFilter(f) }, label = { Text(journalFilterLabel(f)) })
                    }
                    item {
                        Box {
                            FilterChip(
                                selected = ready?.habitId != null,
                                onClick = { habitMenu = true },
                                label = { Text(ready?.habitId?.let { ready.habits[it]?.name } ?: stringResource(R.string.journal_any_habit)) },
                            )
                            DropdownMenu(expanded = habitMenu, onDismissRequest = { habitMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.journal_any_habit)) }, onClick = { habitMenu = false; viewModel.setHabit(null) })
                                ready?.habits?.values?.sortedBy { it.sortOrder }?.forEach { habit ->
                                    DropdownMenuItem(text = { Text("${habit.emoji} ${habit.name}") }, onClick = { habitMenu = false; viewModel.setHabit(habit.id) })
                                }
                            }
                        }
                    }
                }
            }
            item(key = "ranges") {
                LazyRow(contentPadding = PaddingValues(horizontal = HabitTheme.spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                    items(JournalRange.entries) { r ->
                        FilterChip(selected = ready?.range == r, onClick = { viewModel.setRange(r) }, label = { Text(rangeLabel(r)) })
                    }
                }
            }
            when (val s = state) {
                JournalUiState.Loading -> item(key = "loading") { Box(Modifier.fillMaxWidth().padding(32.dp)) { FullScreenLoading() } }
                JournalUiState.Error -> item(key = "error") { FullScreenError(stringResource(R.string.error_loading)) }
                is JournalUiState.Ready -> {
                    if (s.entries.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                iconRes = R.drawable.ic_journal,
                                title = stringResource(if (s.query.isBlank()) R.string.journal_empty_title else R.string.journal_no_results),
                                message = stringResource(if (s.query.isBlank()) R.string.journal_empty_message else R.string.journal_no_results_message),
                                action = if (s.query.isBlank()) ({ Button(onClick = onNewEntry) { Text(stringResource(R.string.journal_new)) } }) else null,
                            )
                        }
                    }
                    items(s.entries, key = { it.id }) { entry ->
                        Card(
                            onClick = { onOpenEntry(entry.id) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.xs),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(Modifier.padding(HabitTheme.spacing.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        val habit = entry.habitId?.let { s.habits[it] }
                                        Text(
                                            entry.date.format(dateFormatter) + (habit?.let { " · ${it.emoji} ${it.name}" } ?: ""),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            (entry.mood?.let { Moods.getOrNull(it)?.plus(" ") } ?: "") + entry.title.ifBlank { stringResource(R.string.journal_untitled) },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                    IconToggleButton(checked = entry.bookmarked, onCheckedChange = { viewModel.toggleBookmark(entry) }) {
                                        Icon(
                                            painterResource(R.drawable.ic_bookmark),
                                            stringResource(R.string.journal_bookmark),
                                            tint = if (entry.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                                if (entry.content.isNotBlank()) {
                                    Text(
                                        RichText.render(entry.content, headingSize = 15.sp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalEditorScreen(onBack: () -> Unit, viewModel: JournalEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val editing = state as? JournalEditorState.Editing
    var preview by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var habitMenu by remember { mutableStateOf(false) }
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    LaunchedEffect(editing?.done) { if (editing?.done == true) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isNew != false) R.string.journal_new else R.string.journal_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) } },
                actions = {
                    if (editing != null) {
                        IconToggleButton(checked = editing.entry.bookmarked, onCheckedChange = { b -> viewModel.update { it.copy(bookmarked = b) } }) {
                            Icon(
                                painterResource(R.drawable.ic_bookmark),
                                stringResource(R.string.journal_bookmark),
                                tint = if (editing.entry.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconToggleButton(checked = preview, onCheckedChange = { preview = it }) {
                            Icon(painterResource(R.drawable.ic_eye), stringResource(R.string.journal_preview))
                        }
                        if (!editing.isNew) {
                            IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete)) }
                        }
                    }
                    TextButton(onClick = viewModel::save, modifier = Modifier.testTag("journal_save")) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            JournalEditorState.Loading -> FullScreenLoading(Modifier.padding(padding))
            JournalEditorState.NotFound -> FullScreenError(stringResource(R.string.error_not_found), Modifier.padding(padding))
            is JournalEditorState.Editing -> {
                val entry = s.entry
                var content by remember(entry.id) { mutableStateOf(TextFieldValue(entry.content)) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = HabitTheme.spacing.screenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm),
                ) {
                    s.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                        OutlinedButton(onClick = { pickDate = true }) { Text(entry.date.format(dateFormatter)) }
                        Box {
                            OutlinedButton(onClick = { habitMenu = true }) {
                                Text(entry.habitId?.let { id -> habits.firstOrNull { it.id == id }?.let { "${it.emoji} ${it.name}" } } ?: stringResource(R.string.journal_daily_entry))
                            }
                            DropdownMenu(expanded = habitMenu, onDismissRequest = { habitMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.journal_daily_entry)) }, onClick = { habitMenu = false; viewModel.update { it.copy(habitId = null) } })
                                habits.forEach { habit ->
                                    DropdownMenuItem(
                                        text = { Text("${habit.emoji} ${habit.name}") },
                                        onClick = { habitMenu = false; viewModel.update { it.copy(habitId = habit.id) } },
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.journal_mood), style = MaterialTheme.typography.labelLarge)
                        Moods.forEachIndexed { index, mood ->
                            FilterChip(
                                selected = entry.mood == index,
                                onClick = { viewModel.update { it.copy(mood = if (it.mood == index) null else index) } },
                                label = { Text(mood, fontSize = 18.sp) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = entry.title,
                        onValueChange = { v -> viewModel.update { it.copy(title = v.take(120)) } },
                        label = { Text(stringResource(R.string.journal_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().testTag("journal_title_field"),
                    )
                    if (preview) {
                        HorizontalDivider()
                        Text(RichText.render(entry.content), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = 200.dp))
                    } else {
                        FormattingToolbar(onChange = { transform ->
                            val updated = transform(content)
                            content = updated
                            viewModel.update { it.copy(content = updated.text) }
                        })
                        OutlinedTextField(
                            value = content,
                            onValueChange = { v ->
                                content = v
                                viewModel.update { it.copy(content = v.text.take(20_000)) }
                            },
                            label = { Text(stringResource(R.string.journal_content)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).testTag("journal_content_field"),
                        )
                        Text(
                            stringResource(R.string.journal_format_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (pickDate) {
                    LocalDatePickerDialog(entry.date, { d -> viewModel.update { it.copy(date = d) } }, { pickDate = false })
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.journal_delete_title),
            message = stringResource(R.string.journal_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FormattingToolbar(onChange: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    Row {
        IconButton(onClick = { onChange { RichText.toggleInline(it, RichText.Inline.BOLD) } }) {
            Icon(painterResource(R.drawable.ic_bold), stringResource(R.string.format_bold))
        }
        IconButton(onClick = { onChange { RichText.toggleInline(it, RichText.Inline.ITALIC) } }) {
            Icon(painterResource(R.drawable.ic_italic), stringResource(R.string.format_italic))
        }
        IconButton(onClick = { onChange { RichText.toggleInline(it, RichText.Inline.UNDERLINE) } }) {
            Icon(painterResource(R.drawable.ic_underline), stringResource(R.string.format_underline))
        }
        IconButton(onClick = { onChange { RichText.toggleLinePrefix(it, RichText.LinePrefix.HEADING) } }) {
            Icon(painterResource(R.drawable.ic_heading), stringResource(R.string.format_heading))
        }
        IconButton(onClick = { onChange { RichText.toggleLinePrefix(it, RichText.LinePrefix.BULLET) } }) {
            Icon(painterResource(R.drawable.ic_list_bulleted), stringResource(R.string.format_bullets))
        }
        IconButton(onClick = { onChange { RichText.toggleLinePrefix(it, RichText.LinePrefix.NUMBERED) } }) {
            Icon(painterResource(R.drawable.ic_list_numbered), stringResource(R.string.format_numbered))
        }
    }
}

object JournalTestTags {
    const val SCREEN = "screen_journal"
}
