package com.abk.kernel.ui.screens.miuix.liquid

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun isInDarkTheme(): Boolean {
    val surface = MiuixTheme.colorScheme.surface
    // Heuristic: if surface is dark, we're in dark theme
    return surface.red < 0.5f && surface.green < 0.5f && surface.blue < 0.5f
}
