package com.example.habitapp.presentation.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.habitapp.R
import com.example.habitapp.domain.model.HabitSuggestion
import com.example.habitapp.domain.model.HabitSuggestions
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.SuggestionCategory
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.home.BigButton
import com.example.habitapp.presentation.home.TileIconButton
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.OnPastel

@Composable
fun StepHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
        TileIconButton(R.drawable.ic_arrow_back, stringResource(R.string.navigate_back), onBack)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.titleSmall)
        }
    }
    HorizontalDivider()
}

/** Step 1: browse suggestions by tracking type and category, or start a custom habit. */
@Composable
fun HabitCreateScreen(onBack: () -> Unit, onSuggestion: (String) -> Unit, onCustom: () -> Unit) {
    val pt = currentLocale().language == "pt"
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StepHeader(stringResource(R.string.habit_new), stringResource(R.string.habit_try_suggestion), onBack)
        LazyColumn(Modifier.weight(1f)) {
            if (searching) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(40) },
                        placeholder = { Text(stringResource(R.string.habit_search_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
                val results = HabitSuggestions.all.filter { it.name(pt).contains(query.trim(), ignoreCase = true) }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { results.forEach { SuggestionChip(it, pt) { onSuggestion(it.id) } } }
                }
            } else {
                item { TypeHeader(R.drawable.ic_today, stringResource(R.string.tracking_count), stringResource(R.string.tracking_count_desc)) }
                SuggestionCategory.entries.filter { !it.quit }.forEach { category ->
                    item(key = category.name) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                            Text(
                                if (pt) category.namePt else category.nameEn,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp).width(84.dp),
                            )
                            LazyRow(contentPadding = PaddingValues(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(HabitSuggestions.all.filter { it.category == category }, key = { it.id }) { s ->
                                    SuggestionChip(s, pt) { onSuggestion(s.id) }
                                }
                            }
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { TypeHeader(R.drawable.ic_block, stringResource(R.string.tracking_quit), stringResource(R.string.tracking_quit_desc)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(HabitSuggestions.all.filter { it.category == SuggestionCategory.QUIT }, key = { it.id }) { s ->
                            SuggestionChip(s, pt) { onSuggestion(s.id) }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                stringResource(if (searching) R.string.habit_show_suggestions else R.string.habit_search),
                R.drawable.ic_search,
                primary = false,
                onClick = { searching = !searching },
                modifier = Modifier.weight(1f),
            )
            BigButton(stringResource(R.string.habit_custom), R.drawable.ic_chevron_right, primary = true, onClick = onCustom, modifier = Modifier.weight(1f).testTag("habit_custom"))
        }
    }
}

@Composable
private fun TypeHeader(icon: Int, title: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
        ) { Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SuggestionChip(s: HabitSuggestion, pt: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .widthIn(min = 90.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(HabitColor.fromKey(s.colorKey).color)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("suggestion_${s.id}"),
        contentAlignment = Alignment.Center,
    ) { Text(s.name(pt), color = OnPastel, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
}

/** Step 2 (custom habit): choose how the habit is tracked. */
@Composable
fun HabitTypeScreen(onBack: () -> Unit, onSelected: (HabitType) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StepHeader(stringResource(R.string.habit_new), stringResource(R.string.tracking_select), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.tracking_manual),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
            )
            Column(Modifier.padding(horizontal = 12.dp)) {
                TrackingOption(R.drawable.ic_today, stringResource(R.string.tracking_count), stringResource(R.string.tracking_count_desc), Modifier.testTag("type_count")) {
                    onSelected(HabitType.COUNT)
                }
                TrackingOption(R.drawable.ic_block, stringResource(R.string.tracking_quit), stringResource(R.string.tracking_quit_desc), Modifier.testTag("type_quit")) {
                    onSelected(HabitType.QUIT)
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            BigButton(stringResource(R.string.action_back), R.drawable.ic_chevron_left, primary = false, onClick = onBack, modifier = Modifier.weight(1f))
        }
    }
}
