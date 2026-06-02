from pathlib import Path

vm = Path("app/src/main/java/com/abk/kernel/viewmodel/MainViewModel.kt")
lines = vm.read_text(encoding="utf-8").splitlines()
ranges = [(643, 647), (654, 995), (3802, 3878), (3984, 3988), (4141, 4206)]
body = []
for s, e in ranges:
    body.extend(lines[s - 1 : e])
text = "\n".join(body)
text = text.replace("_uiState.value", "readState()")
text = text.replace("_uiState.update", "updateState")
text = text.replace("viewModelScope", "scope")
text = text.replace("getApplication<Application>()", "app")
text = text.replace("private fun ", "fun ")
text = text.replace("private suspend fun ", "suspend fun ")
text = text.replace("Quadruple(", "RuntimeQuadruple(")

header = """package com.abk.kernel.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import com.abk.kernel.R
import com.abk.kernel.data.model.*
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val OFFICIAL_RUNTIME_MODULE_REPOSITORY_ID = "official-runtime-module-repository"
private const val OFFICIAL_RUNTIME_MODULE_REPOSITORY_URL =
    "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json-v2/refs/heads/main/json/modules.json"

private data class RuntimeQuadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

class RuntimeCoordinator(
    private val scope: CoroutineScope,
    private val app: Application,
    private val github: GitHubRepository,
    private val prefs: PreferencesRepository,
    private val gson: Gson,
    private val ksuModuleListType: Type,
    private val readState: () -> MainUiState,
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val resolveManagerAccess: (Boolean) -> RootUtils.ManagerAccessInfo,
    private val managerAccessErrorMessage: (RootUtils.ManagerAccessInfo, Boolean) -> String,
    private val str: (Int, Array<out Any>) -> String,
) {
    private fun text(@StringRes resId: Int, vararg args: Any): String =
        if (args.isEmpty()) str(resId, emptyArray()) else str(resId, args)

    private fun localizedRuntimeModuleRepoTitle(): String =
        when (LocaleHelper.getLanguage(app)) {
            LocaleHelper.LANG_ZH -> "普通模块仓库"
            LocaleHelper.LANG_RU -> "Репозиторий обычных модулей"
            else -> "Standard Module Repo"
        }

    fun onRuntimeRepositoriesJsonChanged(json: String?) {
        val repositories = parseRuntimeModuleRepositories(json)
        updateState { it.copy(runtimeModuleRepositories = repositories) }
        refreshStaleRuntimeModuleRepositories(repositories)
    }

"""

footer = """
}

private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component1() = first
private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component2() = second
private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component3() = third
private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component4() = fourth
"""

indented = []
for line in text.splitlines():
    if line.strip():
        if not line.startswith("    "):
            line = "    " + line
    indented.append(line)

out_path = Path("app/src/main/java/com/abk/kernel/viewmodel/RuntimeCoordinator.kt")
out_path.write_text(header + "\n".join(indented) + footer, encoding="utf-8")
print("wrote", out_path, "lines", len((header + "\n".join(indented) + footer).splitlines()))
