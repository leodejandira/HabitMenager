package com.example.habitapp.presentation.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.habitapp.R
import com.example.habitapp.domain.model.HabitSuggestions
import com.example.habitapp.domain.model.SuggestionCategory
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.components.formatAmount
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    val portuguese = currentLocale().language == "pt"
    var category by rememberSaveable { mutableStateOf<SuggestionCategory?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.suggestions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = HabitTheme.spacing.screenHorizontal),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(HabitTheme.spacing.sm),
                ) {
                    item {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.filter_all)) })
                    }
                    items(SuggestionCategory.entries) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c },
                            label = { Text("${c.emoji} ${if (portuguese) c.namePt else c.nameEn}") },
                        )
                    }
                }
            }
            val visible = SuggestionCategory.entries.filter { category == null || it == category }
            visible.forEach { c ->
                item(key = "header_${c.name}") { SectionHeader("${c.emoji} ${if (portuguese) c.namePt else c.nameEn}") }
                items(HabitSuggestions.all.filter { it.category == c }, key = { it.id }) { s ->
                    val detail = when {
                        s.type.isMeasurable && s.type == com.example.habitapp.domain.model.HabitType.DURATION ->
                            stringResource(R.string.suggestion_minutes, formatAmount(s.target))
                        s.type.isMeasurable -> "${formatAmount(s.target)} ${s.unit(portuguese)}"
                        else -> habitTypeLabel(s.type)
                    }
                    ListItem(
                        leadingContent = { EmojiBadge(s.emoji, HabitColor.fromKey(s.colorKey).resolve(), 40) },
                        headlineContent = { Text(s.name(portuguese)) },
                        supportingContent = { Text(detail + " · " + frequencyLabel(s.frequency.type)) },
                        modifier = Modifier
                            .clickable { onPick(s.id) }
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
