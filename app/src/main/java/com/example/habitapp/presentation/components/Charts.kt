package com.example.habitapp.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class BarEntry(val label: String, val value: Float)

/** Simple vertical bar chart. Values are drawn relative to [maxValue] (defaults to the largest value). */
@Composable
fun BarChart(
    entries: List<BarEntry>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    targetLine: Float? = null,
    chartHeight: Dp = 140.dp,
    description: String = "",
) {
    val max = (maxValue ?: entries.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(targetLine ?: 0f).coerceAtLeast(1e-6f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val lineColor = MaterialTheme.colorScheme.outline
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            if (entries.isEmpty()) return@Canvas
            val slot = size.width / entries.size
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            entries.forEachIndexed { index, entry ->
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 3),
                )
                val h = (entry.value / max).coerceIn(0f, 1f) * size.height
                if (h > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 3),
                    )
                }
            }
            targetLine?.let { target ->
                val y = size.height - (target / max).coerceIn(0f, 1f) * size.height
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            entries.forEach { entry ->
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * GitHub-style calendar heatmap: one column per week, rows ordered from [firstDayOfWeek].
 * [intensity] returns 0..1 for a date, or null for dates outside the range.
 */
@Composable
fun CalendarHeatmap(
    endDate: LocalDate,
    weeks: Int,
    firstDayOfWeek: DayOfWeek,
    color: Color,
    intensity: (LocalDate) -> Float?,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val lastWeekStart = endDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val firstWeekStart = lastWeekStart.minusWeeks((weeks - 1).toLong())
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(((7 * 14) + 6 * 3).dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 3.dp.toPx()
        val cell = ((size.width - gap * (weeks - 1)) / weeks).coerceAtMost((size.height - gap * 6) / 7)
        for (w in 0 until weeks) {
            for (d in 0 until 7) {
                val date = firstWeekStart.plusWeeks(w.toLong()).plusDays(d.toLong())
                if (date.isAfter(endDate)) continue
                val value = intensity(date)
                val fill = when {
                    value == null -> Color.Transparent
                    value <= 0f -> empty
                    else -> color.copy(alpha = 0.25f + 0.75f * value.coerceIn(0f, 1f))
                }
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(w * (cell + gap), d * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell / 4),
                )
            }
        }
    }
}

@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    stroke: Dp = 9.dp,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "ring")
    val track = color.copy(alpha = 0.16f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            drawArc(
                color,
                -90f,
                360f * animated,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
