package com.nousresearch.hermes.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.AnalyticsDailyEntry
import com.nousresearch.hermes.protocol.AnalyticsModelEntry
import com.nousresearch.hermes.protocol.AnalyticsSkillEntry
import com.nousresearch.hermes.protocol.AnalyticsToolEntry
import com.nousresearch.hermes.protocol.ContextUsageCategory
import java.util.Locale
import com.nousresearch.hermes.R

@Composable
internal fun UsageScreen(
    state: HermesState,
    onRefresh: (Int) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh(state.usageDays) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.a11y_back_b52b36)) }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_usage_cf3bfa), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.hermes_profile, state.activeProfile), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onRefresh(state.usageDays) }, enabled = !state.usageLoading) {
                if (state.usageLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.a11y_refresh_usage_a4a94d))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(7, 30, 90).forEach { days ->
                if (state.usageDays == days) {
                    Button(onClick = { onRefresh(days) }, modifier = Modifier.weight(1f), enabled = !state.usageLoading) {
                        Text(pluralStringResource(R.plurals.days, days, days))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onRefresh(days) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.usageLoading,
                    ) { Text(pluralStringResource(R.plurals.days, days, days)) }
                }
            }
        }
        state.usageError?.let { UsageError(it) }
        val analytics = state.usageAnalytics
        if (analytics == null && state.usageLoading) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(48.dp))
            }
            return@Column
        }
        if (analytics == null) {
            Text(stringResource(R.string.ui_hermes_usage_data_is_unavailable_for_this_profile_ddbf4c),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { UsageTotals(state) }
            state.contextBreakdown?.let { breakdown ->
                item {
                    UsageSection("LIVE CONTEXT") {
                        val percent = breakdown.contextPercent.coerceIn(0.0, 100.0).toFloat()
                        Text(
                            "${breakdown.contextUsed.compact()} of ${breakdown.contextMax.compact()} tokens / ${percent.toInt()}% full",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth().semantics {
                                progressBarRangeInfo = ProgressBarRangeInfo(percent, 0f..100f)
                            },
                        )
                        if (breakdown.categories.isEmpty()) {
                            Text(stringResource(R.string.ui_hermes_did_not_report_a_category_breakdown_for_this_ses_e788ed), style = MaterialTheme.typography.bodySmall)
                        } else {
                            breakdown.categories.forEach { ContextCategoryRow(it, breakdown.contextUsed) }
                        }
                    }
                }
            }
            if (analytics.byModel.isNotEmpty()) {
                item { SectionHeading("MODELS") }
                items(analytics.byModel.take(MAX_USAGE_ROWS), key = AnalyticsModelEntry::model) { model ->
                    UsageModelRow(model)
                }
            }
            if (analytics.daily.isNotEmpty()) {
                item { SectionHeading("RECENT ACTIVITY") }
                items(analytics.daily.takeLast(MAX_USAGE_ROWS).asReversed(), key = AnalyticsDailyEntry::day) { day ->
                    UsageDailyRow(day)
                }
            }
            if (analytics.tools.isNotEmpty()) {
                item { SectionHeading("TOOLS") }
                items(analytics.tools.take(MAX_USAGE_ROWS), key = AnalyticsToolEntry::tool) { tool ->
                    UsageCountRow(tool.tool, "${tool.count.compact()} calls / ${tool.percentage.safePercent()}%")
                }
            }
            if (analytics.skills.topSkills.isNotEmpty()) {
                item { SectionHeading("SKILLS") }
                items(analytics.skills.topSkills.take(MAX_USAGE_ROWS), key = AnalyticsSkillEntry::skill) { skill ->
                    UsageCountRow(skill.skill, "${skill.totalCount.compact()} actions / ${skill.percentage.safePercent()}%")
                }
            }
        }
    }
}

@Composable
private fun UsageTotals(state: HermesState) {
    val analytics = requireNotNull(state.usageAnalytics)
    val totals = analytics.totals
    UsageSection("${analytics.periodDays}-DAY TOTAL") {
        UsageMetric("Sessions", totals.totalSessions.compact())
        UsageMetric("API calls", totals.totalApiCalls.compact())
        UsageMetric("Input / output", "${totals.totalInput.compact()} / ${totals.totalOutput.compact()}")
        UsageMetric("Cache read / reasoning", "${totals.totalCacheRead.compact()} / ${totals.totalReasoning.compact()}")
        UsageMetric("Actual cost", totals.totalActualCost.usd())
        UsageMetric("Estimated cost", totals.totalEstimatedCost.usd())
    }
}

@Composable
private fun UsageModelRow(model: AnalyticsModelEntry) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.model.take(MAX_USAGE_LABEL_CHARACTERS), style = MaterialTheme.typography.titleMedium)
            Text(
                "${model.inputTokens.compact()} input / ${model.outputTokens.compact()} output / ${model.apiCalls.compact()} calls",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.estimated_usage, model.estimatedCost.usd(), model.sessions.compact()), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun UsageDailyRow(day: AnalyticsDailyEntry) {
    UsageCountRow(
        day.day.take(MAX_USAGE_LABEL_CHARACTERS),
        "${day.inputTokens.compact()} in / ${day.outputTokens.compact()} out / ${day.sessions.compact()} sessions",
    )
}

@Composable
private fun ContextCategoryRow(category: ContextUsageCategory, total: Long) {
    val share = if (total > 0) (category.tokens.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0) else 0.0
    UsageMetric(
        category.label.ifBlank { category.id }.take(MAX_USAGE_LABEL_CHARACTERS),
        "${category.tokens.compact()} / ${share.safePercent()}%",
    )
}

@Composable
private fun UsageCountRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        UsageMetric(label.take(MAX_USAGE_LABEL_CHARACTERS), value, Modifier.padding(14.dp))
    }
}

@Composable
private fun UsageSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

@Composable
private fun UsageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun UsageError(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Text(message.take(MAX_USAGE_ERROR_CHARACTERS), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun Long?.compact(): String {
    val value = this?.coerceAtLeast(0) ?: return "—"
    return when {
        value >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun Double.usd(): String = if (isFinite() && this >= 0) String.format(Locale.getDefault(), "$%.4f", this) else "—"
private fun Double.safePercent(): String = if (isFinite()) String.format(Locale.getDefault(), "%.1f", coerceIn(0.0, 100.0)) else "0.0"

private const val MAX_USAGE_ROWS = 20
private const val MAX_USAGE_LABEL_CHARACTERS = 200
private const val MAX_USAGE_ERROR_CHARACTERS = 1_000
