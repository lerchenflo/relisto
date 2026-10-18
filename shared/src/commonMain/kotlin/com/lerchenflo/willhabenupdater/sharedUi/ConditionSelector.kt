package com.lerchenflo.willhabenupdater.sharedUi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lerchenflo.willhabenupdater.listings.domain.Condition
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.condition_defective
import willhabenupdater.shared.generated.resources.condition_like_new
import willhabenupdater.shared.generated.resources.condition_new
import willhabenupdater.shared.generated.resources.condition_used
import willhabenupdater.shared.generated.resources.editor_condition_none

fun Condition.label(): StringResource = when (this) {
    Condition.NEW -> Res.string.condition_new
    Condition.LIKE_NEW -> Res.string.condition_like_new
    Condition.USED -> Res.string.condition_used
    Condition.DEFECTIVE -> Res.string.condition_defective
}

/** Chips for "not set" plus every [Condition]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionSelector(
    label: String,
    selected: Condition?,
    onSelect: (Condition?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.editor_condition_none)) },
            )
            Condition.entries.forEach { condition ->
                FilterChip(
                    selected = selected == condition,
                    onClick = { onSelect(condition) },
                    label = { Text(stringResource(condition.label())) },
                )
            }
        }
    }
}
