package com.abk.kernel.miuix.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Short snackbar duration (matching ABK_SNACKBAR_SHORT_MS). */
const val ABK_MIUIX_SNACKBAR_SHORT_MS = 3_500L

/** Long snackbar duration (matching ABK_SNACKBAR_LONG_MS). */
const val ABK_MIUIX_SNACKBAR_LONG_MS = 6_000L

/**
 * Extension on MIUIX's [SnackbarHostState] mirroring [com.abk.kernel.ui.components.showAbkSnackbar].
 * Routes to the built-in MIUIX Short/Long durations, or uses Indefinite + manual dismiss
 * for intermediate durations so the custom millisecond value is honored.
 */
suspend fun SnackbarHostState.showAbkMiuixSnackbar(
    message: String,
    longDuration: Boolean
) {
    showAbkMiuixSnackbar(
        message = message,
        durationMs = if (longDuration) ABK_MIUIX_SNACKBAR_LONG_MS else ABK_MIUIX_SNACKBAR_SHORT_MS
    )
}

suspend fun SnackbarHostState.showAbkMiuixSnackbar(
    message: String,
    durationMs: Long
) {
    when {
        durationMs <= ABK_MIUIX_SNACKBAR_SHORT_MS + 250L -> {
            showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
        durationMs >= 9_500L -> {
            showSnackbar(message = message, duration = SnackbarDuration.Long)
        }
        else -> {
            coroutineScope {
                launch {
                    delay(durationMs)
                    newestSnackbarData()?.dismiss()
                }
                showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
            }
        }
    }
}

/**
 * MIUIX-themed replacement for [com.abk.kernel.ui.components.AbkSnackbarHost].
 * Uses `top.yukonga.miuix.kmp.basic.SnackbarHost` with the MIUIX base surface color
 * ([top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface]) as container background,
 * which is cleaner (near-pure surface in light mode) than the library's default
 * `surfaceContainerHighest` (which is noticeably gray).
 */
@Composable
fun AbkMiuixSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        state = hostState,
        modifier = modifier.padding(horizontal = 20.dp),
        content = { data ->
            Snackbar(
                data = data,
                colors = SnackbarDefaults.snackbarColors(
                    containerColor = MiuixTheme.colorScheme.surface,
                ),
            )
        },
    )
}
