package com.abk.kernel.miuix.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@Composable
@ReadOnlyComposable
fun isMiuixDarkTheme(): Boolean = isSystemInDarkTheme()

