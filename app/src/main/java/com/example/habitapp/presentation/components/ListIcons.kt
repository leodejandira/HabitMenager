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
    val all: List<Pair<String, Int>> = listOf(
        "checklist" to R.drawable.li_checklist,
        "inbox" to R.drawable.li_inbox,
        "work" to R.drawable.li_work,
        "school" to R.drawable.li_school,
        "home" to R.drawable.li_home,
        "shopping" to R.drawable.li_shopping,
        "fitness" to R.drawable.li_fitness,
        "heart" to R.drawable.li_heart,
        "build" to R.drawable.li_build,
        "code" to R.drawable.li_code,
        "savings" to R.drawable.li_savings,
        "money" to R.drawable.li_money,
        "pets" to R.drawable.li_pets,
        "flight" to R.drawable.li_flight,
        "restaurant" to R.drawable.li_restaurant,
        "music" to R.drawable.li_music,
        "book" to R.drawable.li_book,
        "brush" to R.drawable.li_brush,
        "games" to R.drawable.li_games,
        "health" to R.drawable.li_health,
        "run" to R.drawable.li_run,
        "spa" to R.drawable.li_spa,
        "eco" to R.drawable.li_eco,
        "star" to R.drawable.li_star,
        "idea" to R.drawable.li_idea,
        "groups" to R.drawable.li_groups,
        "family" to R.drawable.li_family,
        "child" to R.drawable.li_child,
        "laptop" to R.drawable.li_laptop,
        "phone" to R.drawable.li_phone,
        "celebration" to R.drawable.li_celebration,
        "science" to R.drawable.li_science,
        "psychology" to R.drawable.li_psychology,
        "meditation" to R.drawable.li_meditation,
        "water" to R.drawable.li_water,
        "sleep" to R.drawable.li_sleep,
        "coffee" to R.drawable.li_coffee,
        "palette" to R.drawable.li_palette,
        "camera" to R.drawable.li_camera,
        "car" to R.drawable.li_car,
        "cleaning" to R.drawable.li_cleaning,
        "person" to R.drawable.li_person,
        "calendar" to R.drawable.li_calendar,
        "rocket" to R.drawable.li_rocket,
    )
    private val byKey = all.toMap()

    /** Earlier versions stored emoji; anything unknown falls back to the checklist glyph. */
    @DrawableRes
    fun resFor(key: String?): Int = byKey[key] ?: when (key) {
        "📥" -> R.drawable.li_inbox
        "💼" -> R.drawable.li_work
        "🛒" -> R.drawable.li_shopping
        "🏃" -> R.drawable.li_run
        "🙂" -> R.drawable.li_person
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
            LazyVerticalGrid(columns = GridCells.Adaptive(52.dp), modifier = Modifier.heightIn(max = 380.dp)) {
                items(ListIcons.all) { (key, _) ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable {
                                onSelected(key)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) { ListBadge(key, colorKey, size = 40.dp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}
