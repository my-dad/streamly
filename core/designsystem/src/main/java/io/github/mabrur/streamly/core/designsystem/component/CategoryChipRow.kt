package io.github.mabrur.streamly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

/**
 * Plain pills, not `FilterChip`. The Material chip draws a border and a leading check icon
 * when selected; the design has neither, and fills the whole pill with the accent instead.
 */
@Composable
fun CategoryChipRow(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = categories, key = { it }) { category ->
            val isSelected = category == selected
            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) StreamlyColors.Surface else StreamlyColors.Ink,
                modifier = Modifier
                    .clip(StreamlyShapes.Pill)
                    .background(
                        if (isSelected) StreamlyColors.Accent else StreamlyColors.ChipFill,
                    )
                    .clickable(
                        role = Role.Tab,
                        onClick = { onSelect(category) },
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}
