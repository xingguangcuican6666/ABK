package com.abk.kernel.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalMiuixSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

suspend fun SnackbarHostState.showAbkSnackbar(
    message: String,
    longDuration: Boolean
) {
    showAbkSnackbar(
        message = message,
        durationMs = if (longDuration) ABK_SNACKBAR_LONG_MS else ABK_SNACKBAR_SHORT_MS
    )
}

suspend fun SnackbarHostState.showAbkSnackbar(
    message: String,
    durationMs: Long
) {
    when {
        durationMs <= ABK_SNACKBAR_SHORT_MS + 250L -> {
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
fun MiuixAbkSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        state = hostState,
        modifier = modifier.padding(horizontal = 20.dp),
        content = { data ->
            Snackbar(
                data = data,
                modifier = Modifier.padding(vertical = 4.dp),
                cornerRadius = 16.dp,
                insideMargin = top.yukonga.miuix.kmp.basic.SnackbarDefaults.InsideMargin
            )
        }
    )
}
