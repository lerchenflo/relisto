package com.lerchenflo.relisto.sharedUi

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android has no file drag and drop from other apps here; the content is shown as is. */
@Composable
actual fun ImageDropArea(
    onFilesDropped: (List<String>) -> Unit,
    modifier: Modifier,
    content: @Composable (isDraggingOver: Boolean) -> Unit,
) {
    Box(modifier = modifier) {
        content(false)
    }
}
