package com.example.habitapp.presentation.reports

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.habitapp.R
import com.example.habitapp.domain.model.GoalType
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.home.BigButton
import com.example.habitapp.presentation.home.TileIconButton
import com.example.habitapp.presentation.theme.HabitColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

enum class ReportMode { WEEK, MONTH, YEAR }

/** One cell of a habit's grid; [fraction] null means outside the habit's active range. */
data class ReportCell(val date: LocalDate?, val fraction: Float?)

data class HabitColumn(val habit: Habit, val rate: Int, val cells: List<ReportCell>, val columns: Int)

data class ReportData(val mode: ReportMode, val start: LocalDate, val end: LocalDate, val today: LocalDate, val columns: List<HabitColumn>)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    habits: HabitRepository,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val mode = savedStateHandle.getStateFlow(KEY_MODE, ReportMode.MONTH.name)
    private val anchor = savedStateHandle.getStateFlow(KEY_ANCHOR, time.today().toEpochDay())

    val data: StateFlow<ReportData?> = combine(
        combine(mode, anchor) { m, a -> ReportMode.valueOf(m) to LocalDate.ofEpochDay(a) },
        habits.observeHabits(),
        habits.observeAllActivities(),
        preferences.preferences,
    ) { (m, a), habitList, activities, prefs ->
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val today = time.today()
        val (start, end) = range(m, a, firstDay)
        val byHabit = activities.groupBy { it.habitId }
        ReportData(
            mode = m,
            start = start,
            end = end,
            today = today,
            columns = habitList.filter { !it.archived }.sortedBy { it.sortOrder }.map { h ->
                val progress = HabitCalculator.progressByDay(h, byHabit[h.id].orEmpty())
                fun fraction(d: LocalDate): Float? = when {
                    d.isBefore(h.startDate) || d.isAfter(today) -> null
                    h.type == HabitType.QUIT -> if ((progress[d]?.total ?: 0.0) > 0) 0.2f else 1f
                    h.goalType == GoalType.AT_MOST -> if (progress[d]?.completed != false) 1f else 0.2f
                    else -> progress[d]?.fraction ?: 0f
                }
                val gridStart = when (m) {
                    ReportMode.YEAR -> start.with(TemporalAdjusters.previousOrSame(firstDay))
                    else -> start.with(TemporalAdjusters.previousOrSame(firstDay))
                }
                val gridEnd = end.with(TemporalAdjusters.nextOrSame(firstDay.minus(1)))
                val cells = generateSequence(gridStart) { it.plusDays(1) }.takeWhile { !it.isAfter(gridEnd) }
                    .map { d -> if (d.isBefore(start) || d.isAfter(end)) ReportCell(null, null) else ReportCell(d, fraction(d)) }.toList()
                val evaluated = cells.mapNotNull { c -> c.date?.takeIf { HabitCalculator.isScheduledOn(h, it) && !it.isAfter(today) } }
                val done = evaluated.count { (fraction(it) ?: 0f) >= 1f }
                HabitColumn(h, if (evaluated.isEmpty()) 0 else done * 100 / evaluated.size, cells, columns = 7)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun range(m: ReportMode, a: LocalDate, firstDay: DayOfWeek): Pair<LocalDate, LocalDate> = when (m) {
        ReportMode.WEEK -> a.with(TemporalAdjusters.previousOrSame(firstDay)).let { it to it.plusDays(6) }
        ReportMode.MONTH -> a.withDayOfMonth(1) to a.withDayOfMonth(a.lengthOfMonth())
        ReportMode.YEAR -> a.withDayOfYear(1) to a.withDayOfYear(a.lengthOfYear())
    }

    fun setMode(m: ReportMode) {
        savedStateHandle[KEY_MODE] = m.name
    }

    fun shift(direction: Long) {
        val a = LocalDate.ofEpochDay(anchor.value)
        savedStateHandle[KEY_ANCHOR] = when (ReportMode.valueOf(mode.value)) {
            ReportMode.WEEK -> a.plusWeeks(direction)
            ReportMode.MONTH -> a.plusMonths(direction)
            ReportMode.YEAR -> a.plusYears(direction)
        }.toEpochDay()
    }

    fun goToToday() {
        savedStateHandle[KEY_ANCHOR] = time.today().toEpochDay()
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_ANCHOR = "anchor"
    }
}

fun periodTitle(data: ReportData, locale: Locale): String = when (data.mode) {
    ReportMode.WEEK -> "${data.start.format(DateTimeFormatter.ofPattern("d MMM", locale))} – ${data.end.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
    ReportMode.MONTH -> data.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    ReportMode.YEAR -> data.start.year.toString()
}

@Composable
fun ReportsScreen(onBack: () -> Unit, onStatistics: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.report_saved)
    val errorText = stringResource(R.string.report_error)
    val shareTitle = stringResource(R.string.report_share)
    val bg = MaterialTheme.colorScheme.background.toArgb()
    val fg = MaterialTheme.colorScheme.onSurface.toArgb()
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                TileIconButton(R.drawable.ic_arrow_back, stringResource(R.string.navigate_back), onBack)
                Text(
                    stringResource(R.string.reports_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
                TileIconButton(R.drawable.ic_spa, stringResource(R.string.statistics_title), onStatistics, Modifier.align(Alignment.CenterEnd))
            }
            val d = data
            if (d == null) {
                FullScreenLoading()
                return@Column
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ReportMode.WEEK to (R.string.period_week to R.drawable.ic_view_week),
                    ReportMode.MONTH to (R.string.period_month to R.drawable.ic_view_month),
                    ReportMode.YEAR to (R.string.period_year to R.drawable.ic_view_year),
                ).forEach { (mode, res) ->
                    val selected = d.mode == mode
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { viewModel.setMode(mode) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val c = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(painterResource(res.second), null, tint = c, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(res.first), color = c)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(periodTitle(d, locale))
                }
                Spacer(Modifier.width(8.dp))
                TileIconButton(R.drawable.ic_chevron_left, stringResource(R.string.calendar_previous), { viewModel.shift(-1) })
                Box(
                    Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { viewModel.goToToday() }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) { Text(stringResource(R.string.date_today)) }
                TileIconButton(R.drawable.ic_chevron_right, stringResource(R.string.calendar_next), { viewModel.shift(1) })
            }
            HorizontalDivider()
            if (d.columns.isEmpty()) {
                Text(stringResource(R.string.habits_empty_title), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(d.columns, key = { it.habit.id }) { column -> HabitGrid(column) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(stringResource(R.string.report_save_image), R.drawable.ic_image, primary = false, modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val ok = runCatching { saveToGallery(context, renderReport(d, locale, bg, fg, empty)) }
                            .onFailure { Log.e("Reports", "save failed", it) }.getOrDefault(false)
                        snackbar.showSnackbar(if (ok) savedText else errorText)
                    }
                })
                BigButton(shareTitle, R.drawable.ic_share, primary = true, modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        runCatching {
                            val file = withContext(Dispatchers.IO) { writeShareFile(context, renderReport(d, locale, bg, fg, empty)) }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }.onFailure {
                            Log.e("Reports", "share failed", it)
                            snackbar.showSnackbar(errorText)
                        }
                    }
                })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

@Composable
private fun HabitGrid(column: HabitColumn) {
    val color = HabitColor.fromKey(column.habit.colorKey).color
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(6.dp)) {
        Text(column.habit.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
            Spacer(Modifier.width(4.dp))
            Text("${column.rate}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(4.dp))
        column.cells.chunked(column.columns).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { cell ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(
                            when {
                                cell.date == null -> androidx.compose.ui.graphics.Color.Transparent
                                cell.fraction == null || cell.fraction <= 0f -> MaterialTheme.colorScheme.surfaceContainerHighest
                                else -> color.copy(alpha = 0.3f + 0.7f * cell.fraction)
                            },
                        ),
                    )
                }
            }
        }
        Text("", textAlign = TextAlign.Center)
    }
}

/** Draws the report into a bitmap with the platform canvas (no view capture needed). */
private suspend fun renderReport(data: ReportData, locale: Locale, bg: Int, fg: Int, empty: Int): Bitmap = withContext(Dispatchers.Default) {
    val cols = 3
    val colWidth = 340f
    val gap = 24f
    val cell = (colWidth - 6 * 4f) / 7
    val heights = data.columns.map { 90f + (it.cells.size / 7) * (cell + 4f) }
    val rows = data.columns.chunked(cols)
    val rowHeights = heights.chunked(cols).map { it.maxOrNull() ?: 0f }
    val width = (cols * colWidth + (cols + 1) * gap).toInt()
    val height = (140f + rowHeights.sum() + rows.size * gap + gap).toInt().coerceAtLeast(300)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(bg)
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = 48f; typeface = Typeface.DEFAULT_BOLD }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = 30f; typeface = Typeface.DEFAULT_BOLD }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; alpha = 170; textSize = 26f }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawText(periodTitle(data, locale).replaceFirstChar { it.titlecase(locale) }, gap, 80f, title)
    var y = 140f
    rows.forEachIndexed { r, row ->
        row.forEachIndexed { c, column ->
            val x = gap + c * (colWidth + gap)
            val name = column.habit.name.let { if (it.length > 18) it.take(17) + "…" else it }
            canvas.drawText(name, x, y + 30f, text)
            canvas.drawText("${column.rate}%", x, y + 66f, small)
            val color = HabitColor.fromKey(column.habit.colorKey).color.toArgb()
            column.cells.forEachIndexed { i, cellData ->
                if (cellData.date == null) return@forEachIndexed
                val cx = x + (i % 7) * (cell + 4f)
                val cy = y + 84f + (i / 7) * (cell + 4f)
                val f = cellData.fraction
                fill.color = if (f == null || f <= 0f) empty else color
                fill.alpha = if (f == null || f <= 0f) 255 else (255 * (0.3f + 0.7f * f)).toInt()
                canvas.drawRoundRect(RectF(cx, cy, cx + cell, cy + cell), 6f, 6f, fill)
            }
        }
        y += rowHeights[r] + gap
    }
    bitmap
}

private fun writeShareFile(context: Context, bitmap: Bitmap): File {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val file = File(dir, "steady-report.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

/** Saves to Pictures/Steady (MediaStore on Android 10+, app pictures folder before that). */
private suspend fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    val name = "steady-report-${System.currentTimeMillis()}.png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Steady")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return@withContext false
        true
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return@withContext false
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }
}
