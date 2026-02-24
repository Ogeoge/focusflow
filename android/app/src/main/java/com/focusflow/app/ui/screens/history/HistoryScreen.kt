package com.focusflow.app.ui.screens.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.app.domain.history.HistoryCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    // Keep HistoryScreen UI compiling even if HistoryViewModel implementation is still evolving.
    // The ViewModel can be wired to real data later.
    var selectedRange by remember { mutableStateOf(HistoryRange.DAY) }

    val today = LocalDate.now()
    val dateLabel = today.format(DateTimeFormatter.ofPattern("MMM d"))

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header(
                selectedRange = selectedRange,
                onSelectRange = { selectedRange = it },
            )
        }

        item {
            SummaryCard(
                title = if (selectedRange == HistoryRange.DAY) "Today" else "This week",
                focusedMinutes = 0,
                totalMinutes = 0,
                pomodoros = 0,
                tasksCompleted = 0,
                streakDays = 0,
                dateLabel = dateLabel,
            )
        }

        item {
            ChartCard(
                title = if (selectedRange == HistoryRange.DAY) "Last 7 days" else "This week (Mon–Sun)",
                bars = emptyList(),
            )
        }

        item {
            Text(
                text = "Recent sessions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            Text(
                text = "No sessions yet. Complete a work/break segment to see it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = { /* TODO: wire to exporter */ }) {
                    Text("Export CSV")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = { /* TODO: wire to share intent */ }) {
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun Header(
    selectedRange: HistoryRange,
    onSelectRange: (HistoryRange) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedRange == HistoryRange.DAY,
                onClick = { onSelectRange(HistoryRange.DAY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Day")
            }
            SegmentedButton(
                selected = selectedRange == HistoryRange.WEEK,
                onClick = { onSelectRange(HistoryRange.WEEK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Week")
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    focusedMinutes: Long,
    totalMinutes: Long,
    pomodoros: Int,
    tasksCompleted: Int,
    streakDays: Int,
    dateLabel: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (dateLabel.isNotBlank()) {
                        Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    text = "Streak: $streakDays day${if (streakDays == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat(label = "Focused", value = "${focusedMinutes}m")
                Stat(label = "Total", value = "${totalMinutes}m")
                Stat(label = "Pomodoros", value = pomodoros.toString())
                Stat(label = "Tasks", value = tasksCompleted.toString())
            }

            Text(
                text = "Streak counts a day only if it includes 1 completed ‘work’ session (breaks don’t count).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartCard(
    title: String,
    bars: List<HistoryCalculator.ChartBar>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Work minutes per day (placeholder chart)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (bars.isEmpty()) {
                Text(
                    text = "No data for this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SimpleBarChart(bars = bars)

                val fmt = DateTimeFormatter.ofPattern("MM/dd")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bars.first().date.format(fmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bars.last().date.format(fmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SimpleBarChart(
    bars: List<HistoryCalculator.ChartBar>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(120.dp),
) {
    val maxWork = max(1L, bars.maxOf { it.workDurationMs })
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        // simple 2-line grid
        val w = size.width
        val h = size.height

        drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(0f, h), end = androidx.compose.ui.geometry.Offset(w, h), strokeWidth = 1f)
        drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.5f), end = androidx.compose.ui.geometry.Offset(w, h * 0.5f), strokeWidth = 1f)
        drawRect(color = gridColor, style = Stroke(width = 1f))

        val count = bars.size
        val gap = 6.dp.toPx()
        val totalGap = gap * (count + 1)
        val barWidth = (w - totalGap) / count.toFloat()

        bars.forEachIndexed { idx, bar ->
            val ratio = (bar.workDurationMs.toFloat() / maxWork.toFloat()).coerceIn(0f, 1f)
            val barHeight = h * ratio
            val left = gap + idx * (barWidth + gap)
            val top = h - barHeight

            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            )
        }
    }
}

@Composable
private fun SessionRow(row: HistorySessionRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = row.typeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = row.durationLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = row.timeRangeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val secondary = buildString {
                if (!row.planLabel.isNullOrBlank()) append("Plan: ${row.planLabel}")
                if (!row.linkedTaskTitle.isNullOrBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append("Task: ${row.linkedTaskTitle}")
                }
            }
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// UI models kept minimal; ViewModel is responsible for mapping from contract/domain models.
enum class HistoryRange { DAY, WEEK }

data class HistorySessionRow(
    val id: String,
    val typeLabel: String,
    val durationLabel: String,
    val timeRangeLabel: String,
    val linkedTaskTitle: String?,
    val planLabel: String?,
)

data class HistoryUiState(
    val selectedRange: HistoryRange,
    val date: LocalDate,
    val dateLabel: String,
    val focusedMinutes: Long,
    val totalMinutes: Long,
    val workSessions: Int,
    val tasksCompleted: Int,
    val streakDays: Int,
    val chartBars: List<HistoryCalculator.ChartBar>,
    val recentSessions: List<HistorySessionRow>,
)
