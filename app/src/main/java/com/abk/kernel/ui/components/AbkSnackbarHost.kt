package com.abk.kernel.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val ABK_SNACKBAR_SHORT_MS = 3_500L
const val ABK_SNACKBAR_LONG_MS = 6_000L

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
                    currentSnackbarData?.dismiss()
                }
                showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
            }
        }
    }
}

@Composable
fun AbkSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(horizontal = 20.dp),
        snackbar = { data ->
            Snackbar(
                modifier = Modifier.padding(vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionContentColor = MaterialTheme.colorScheme.primary,
                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    )
}
