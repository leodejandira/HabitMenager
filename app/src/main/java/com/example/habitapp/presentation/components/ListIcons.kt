package com.example.habitapp.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.habitapp.R
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.OnPastel

/** Icons a task list can use; only the key is stored. */
object ListIcons {
    val all: List<Pair<String, Int>> = ListIconCatalog.categories.flatMap { it.second }
    private val byKey = all.toMap()

    /** Earlier versions stored emoji; anything unknown falls back to the checklist glyph. */
    @DrawableRes
    fun resFor(key: String?): Int = byKey[key] ?: when (key) {
        "📥" -> R.drawable.li_inbox
        "💼" -> R.drawable.li_work
        "🛒" -> R.drawable.li_shopping
        "🏃" -> R.drawable.li_run
        "🙂" -> R.drawable.li_person
        "🌅", "☀️" -> R.drawable.li_sun
        "🌙" -> R.drawable.li_moon
        "🧘" -> R.drawable.li_meditation
        "💪" -> R.drawable.li_fitness
        else -> R.drawable.li_checklist
    }
}

/** A list glyph on its colour, as a circle (menus) or rounded square (task cards). */
@Composable
fun ListBadge(
    iconKey: String?,
    colorKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(HabitColor.fromKey(colorKey).color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(ListIcons.resFor(iconKey)),
            contentDescription = null,
            tint = OnPastel,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

val TaskCardIconShape = RoundedCornerShape(10.dp)

@Composable
fun ListIconPickerDialog(colorKey: String, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_choose_icon)) },
        text = {
            LazyVerticalGrid(columns = GridCells.Adaptive(52.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                ListIconCatalog.categories.forEach { (category, icons) ->
                    item(key = "h_$category", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(iconCategoryLabel(category)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(icons, key = { it.first }) { (key, _) ->
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .testTag("list_icon_$key")
                                .clickable {
                                    onSelected(key)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center,
                        ) { ListBadge(key, colorKey, size = 40.dp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

private fun iconCategoryLabel(category: String): Int = when (category) {
    "work" -> R.string.icons_work
    "study" -> R.string.icons_study
    "home" -> R.string.icons_home
    "health" -> R.string.icons_health
    "sport" -> R.string.icons_sport
    "food" -> R.string.icons_food
    "money" -> R.string.icons_money
    "travel" -> R.string.icons_travel
    "leisure" -> R.string.icons_leisure
    "people" -> R.string.icons_people
    "nature" -> R.string.icons_nature
    "tech" -> R.string.icons_tech
    else -> R.string.icons_general
}
