package com.abk.kernel.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.viewmodel.MainUiState

@Composable
internal fun SusfsControlScreen(
    topBarHeight: Dp,
    state: MainUiState,
    showRefreshLoading: Boolean,
    onApply: (SusfsConfig) -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
) {
    SusfsControlContent(
        topBarHeight = topBarHeight,
        runtime = state.susfsRuntimeStatus,
        savedConfig = state.susfsConfig,
        saving = state.susfsSaving,
        error = state.susfsError,
        lastApplyOutput = state.susfsLastApplyOutput,
        showRefreshLoading = showRefreshLoading,
        onApply = onApply,
        onReset = onReset,
        onRefresh = onRefresh,
    )
}
