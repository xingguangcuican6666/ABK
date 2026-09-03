package com.abk.kernel.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Simple navigation helper that owns a back stack and result channels.
 * Supports push/replace/pop/popUntil and result APIs: navigateForResult/setResult/observeResult/clearResult.
 */
class Navigator(
    initialKey: NavKey
) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(initialKey)

    private val resultBus = mutableMapOf<String, MutableSharedFlow<Any>>()

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    fun replaceAll(keys: List<NavKey>) {
        if (keys.isEmpty()) return
        if (backStack.isNotEmpty()) {
            backStack.clear()
            backStack.addAll(keys)
        }
    }

    fun pop() {
        backStack.removeLastOrNull()
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.isNotEmpty() && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun navigateForResult(route: Route, requestKey: String) {
        ensureChannel(requestKey)
        push(route)
    }

    fun <T : Any> setResult(requestKey: String, value: T) {
        ensureChannel(requestKey).tryEmit(value)
        pop()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observeResult(requestKey: String): SharedFlow<T> {
        return ensureChannel(requestKey) as SharedFlow<T>
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearResult(requestKey: String) {
        ensureChannel(requestKey).resetReplayCache()
    }

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size

    private fun ensureChannel(key: String): MutableSharedFlow<Any> =
        resultBus.getOrPut(key) { MutableSharedFlow(replay = 1, extraBufferCapacity = 0) }

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { navigator -> navigator.backStack.toList() },
            restore = { savedList ->
                val initialKey = savedList.firstOrNull() ?: Route.Main
                Navigator(initialKey).apply {
                    backStack.clear()
                    backStack.addAll(savedList)
                }
            }
        )
    }
}

@Composable
fun rememberNavigator(startRoute: NavKey = Route.Main): Navigator =
    rememberSaveable(startRoute, saver = Navigator.Saver) {
        Navigator(startRoute)
    }

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
