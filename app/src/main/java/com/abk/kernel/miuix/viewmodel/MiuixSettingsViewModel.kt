package com.abk.kernel.miuix.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abk.kernel.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MiuixSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)

    val state = combine(
        combine(
            prefs.uiStyle,
            prefs.miuixThemeColorArgb,
            prefs.miuixAccentColorArgb
        ) { uiStyle, miuixTheme, miuixAccent -> Triple(uiStyle, miuixTheme, miuixAccent) },
        combine(
            prefs.miuixDynamicColorEnabled,
            prefs.miuixColorStyle,
            prefs.miuixColorSpec
        ) { dynamicColor, colorStyle, colorSpec -> Triple(dynamicColor, colorStyle, colorSpec) }
    ) { (uiStyle, miuixTheme, miuixAccent), (dynamicColor, colorStyle, colorSpec) ->
        MiuixUiState(
            uiStyle = uiStyle,
            miuixThemeColorArgb = miuixTheme,
            miuixAccentColorArgb = miuixAccent,
            miuixDynamicColorEnabled = dynamicColor,
            miuixColorStyle = colorStyle,
            miuixColorSpec = colorSpec,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiuixUiState())

    fun setUiStyle(style: String) = viewModelScope.launch { prefs.setUiStyle(style) }
    fun setMiuixThemeColor(argb: Int) = viewModelScope.launch { prefs.setMiuixThemeColor(argb) }
    fun setMiuixAccentColor(argb: Int) = viewModelScope.launch { prefs.setMiuixAccentColor(argb) }
    fun setMiuixDynamicColorEnabled(v: Boolean) = viewModelScope.launch { prefs.setMiuixDynamicColorEnabled(v) }
    fun setMiuixColorStyle(name: String) = viewModelScope.launch { prefs.setMiuixColorStyle(name) }
    fun setMiuixColorSpec(name: String) = viewModelScope.launch { prefs.setMiuixColorSpec(name) }
}

data class MiuixUiState(
    val uiStyle: String = "material",
    val miuixThemeColorArgb: Int? = null,
    val miuixAccentColorArgb: Int? = null,
    val miuixDynamicColorEnabled: Boolean = false,
    val miuixColorStyle: String = "TonalSpot",
    val miuixColorSpec: String = "Spec2021",
)
