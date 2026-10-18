package com.lerchenflo.willhabenupdater.sharedUi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Area that accepts files dropped from the operating system (desktop only).
 * [content] gets true while files are dragged over the area.
 */
@Composable
expect fun ImageDropArea(
    onFilesDropped: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (isDraggingOver: Boolean) -> Unit,
)
