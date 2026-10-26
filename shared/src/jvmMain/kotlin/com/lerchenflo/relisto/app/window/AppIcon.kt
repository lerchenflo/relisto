package com.lerchenflo.relisto.app.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.app_icon

/** Window and taskbar icon; the installers use the files in desktopApp/icons. */
@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)
