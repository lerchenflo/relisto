package com.lerchenflo.relisto.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Teal10 = Color(0xFF00201C)
private val Teal20 = Color(0xFF003731)
private val Teal30 = Color(0xFF005048)
private val Teal40 = Color(0xFF006A60)
private val Teal80 = Color(0xFF53DBC9)
private val Teal90 = Color(0xFF74F8E5)

private val Amber30 = Color(0xFF5C4300)
private val Amber40 = Color(0xFF7A5900)
private val Amber80 = Color(0xFFF8BD2A)
private val Amber90 = Color(0xFFFFDF9E)

private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)

private val Neutral10 = Color(0xFF191C1B)
private val Neutral20 = Color(0xFF2D3130)
private val Neutral90 = Color(0xFFE0E3E1)
private val Neutral95 = Color(0xFFEFF1EF)
private val Neutral99 = Color(0xFFFAFDFB)

internal val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber30,
    error = Red40,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceContainer = Neutral95,
)

internal val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Amber80,
    onSecondary = Amber30,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    error = Red80,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceContainer = Neutral20,
)
