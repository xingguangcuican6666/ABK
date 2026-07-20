package com.abk.kernel.miuix.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
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

@Composable
fun AbkMiuixSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        state = hostState,
        modifier = modifier.padding(horizontal = 20.dp),
        content = { data ->
            Surface(
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .widthIn(min = 96.dp, max = 336.dp),
                shape = RoundedCornerShape(12.dp),
                color = MiuixTheme.colorScheme.surfaceContainerHighest,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = data.visuals.message,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}
