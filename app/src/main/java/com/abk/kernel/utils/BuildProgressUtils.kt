package com.abk.kernel.utils
import com.abk.kernel.tr
import com.abk.kernel.R

import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStepProgress
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import kotlin.math.roundToInt

object BuildProgressUtils {

    /**
     * Per-run formatting hint for [merge]. When populated, [merge] uses a
     * compact technical format instead of the old "Объединённый прогресс по
     * N workflow…" wall of text:
     *   single:   "#42 SukiSU SUSFS 6.6.89-android15-2025-06"
     *   multiple: "#42 SukiSU SUSFS 6.6.89-… · Manager Dev"
     *
     * Sourced from BuildQueueItem.config in the ViewModel — runs without a
     * descriptor fall back to the older "#N {step.name}" rendering.
     */
    data class RunDescriptor(
        val isManager: Boolean = false,
        val managerIsDev: Boolean = false,
        val ksuVariant: String = "",
        val susfs: Boolean = false,
        val kernelLabel: String = ""
    )

    fun from(run: WorkflowRun, jobs: List<WorkflowJob>): BuildProgress {
        val steps = jobs.flatMapIndexed { jobIndex, job ->
            val jobSteps = job.steps.orEmpty()
            val jobName = translateWorkflowStepName(job.name)
            if (jobSteps.isEmpty()) {
                listOf(
                    BuildStepProgress(
                        name = jobName,
                        status = job.status ?: run.status,
                        conclusion = job.conclusion,
                        index = jobIndex + 1
                    )
                )
            } else {
                jobSteps.sortedBy { it.number }.map { step ->
                    BuildStepProgress(
                        name = "$jobName / ${translateWorkflowStepName(step.name)}",
                        status = step.status ?: job.status ?: run.status,
                        conclusion = step.conclusion,
                        index = step.number
                    )
                }
            }
        }

        if (steps.isEmpty()) {
            return when (run.status) {
                "completed" -> BuildProgress(
                    percent = 100,
                    currentStep = if (run.conclusion == "success") tr(R.string.bp_all_steps_done) else tr(R.string.bp_build_finished),
                    completedSteps = 1,
                    totalSteps = 1
                )
                "in_progress" -> BuildProgress(percent = 5, currentStep = tr(R.string.bp_waiting_steps))
                else -> BuildProgress(percent = 0, currentStep = tr(R.string.bp_build_queued))
            }
        }

        val total = steps.size
        val completed = steps.count { it.status == "completed" || it.conclusion != null }
        val active = steps.firstOrNull { it.status == "in_progress" }
        val next = steps.firstOrNull { it.status != "completed" && it.conclusion == null }
        val current = active ?: next ?: steps.last()
        val percent = when (run.status) {
            "completed" -> 100
            "queued", "waiting", "requested", "pending" -> 0
            else -> ((completed * 100f) / total).toInt().coerceIn(1, 99)
        }

        return BuildProgress(
            percent = percent,
            currentStep = current.name,
            completedSteps = completed,
            totalSteps = total,
            steps = steps
        )
    }

    fun defaultFor(run: WorkflowRun): BuildProgress = when (run.status) {
        "completed" -> BuildProgress(
            percent = 100,
            currentStep = if (run.conclusion == "success") tr(R.string.bp_all_steps_done) else tr(R.string.bp_build_finished),
            completedSteps = 1,
            totalSteps = 1
        )
        "in_progress" -> BuildProgress(
            percent = 5,
            currentStep = tr(R.string.bp_run_waiting_steps, runDisplayLabel(run)),
            completedSteps = 0,
            totalSteps = 1
        )
        "queued", "waiting", "requested", "pending" -> BuildProgress(
            percent = 0,
            currentStep = tr(R.string.bp_run_queued, runDisplayLabel(run)),
            completedSteps = 0,
            totalSteps = 1
        )
        else -> BuildProgress(
            percent = 0,
            currentStep = tr(R.string.bp_run_waiting_sync, runDisplayLabel(run)),
            completedSteps = 0,
            totalSteps = 1
        )
    }

    fun merge(
        runs: List<WorkflowRun>,
        progressByRunId: Map<Long, BuildProgress>,
        descriptors: Map<Long, RunDescriptor> = emptyMap()
    ): BuildProgress {
        val activeRuns = runs
            .filter { it.status in ACTIVE_RUN_STATUSES }
            .distinctBy { it.id }
            .sortedByDescending { it.id }
        if (activeRuns.isEmpty()) return BuildProgress()

        val pairs = activeRuns.map { run -> run to (progressByRunId[run.id] ?: defaultFor(run)) }
        val percent = (pairs.sumOf { it.second.percent.coerceIn(0, 100) } / pairs.size.toFloat())
            .roundToInt()
            .coerceIn(0, 99)
        val totalSteps = pairs.sumOf { (_, progress) -> progress.totalSteps.takeIf { it > 0 } ?: 1 }
        val completedSteps = pairs.sumOf { (_, progress) ->
            if (progress.totalSteps > 0) {
                progress.completedSteps.coerceIn(0, progress.totalSteps)
            } else if (progress.percent >= 100) {
                1
            } else {
                0
            }
        }
        // Both branches use the compact "#65 SukiSU SUSFS 6.6.89-…"-style
        // chip output. The descriptor map is normally populated from the VM
        // (buildQueue → KernelBuildConfig); when empty (e.g. notification
        // service in a process that lacks queue context) the helper falls
        // back to "#N {step.name}" per run — still without the old
        // "Объединённый прогресс по N workflow" prefix.
        val currentStep = buildCompactMergedStep(activeRuns, pairs.toMap(), descriptors)
        val steps = pairs.flatMap { (run, progress) ->
            progress.steps.map { step ->
                step.copy(name = "${runDisplayLabel(run)} ${step.name}")
            }
        }

        return BuildProgress(
            percent = percent,
            currentStep = currentStep,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            steps = steps
        )
    }

    /**
     * "#42 SukiSU SUSFS 6.6.89-android15-2025-06" for one run,
     * "2 Workflows · #42 SukiSU … · Manager Dev" for many.
     */
    private fun buildCompactMergedStep(
        activeRuns: List<WorkflowRun>,
        progressMap: Map<WorkflowRun, BuildProgress>,
        descriptors: Map<Long, RunDescriptor>
    ): String {
        val entries = activeRuns.map { run ->
            val desc = descriptors[run.id]
            when {
                desc?.isManager == true -> buildString {
                    append(runDisplayLabel(run))
                    append(' ')
                    append(if (desc.managerIsDev) "Manager Dev" else "Manager")
                }
                desc != null && desc.kernelLabel.isNotBlank() -> buildString {
                    append(runDisplayLabel(run))
                    if (desc.ksuVariant.isNotBlank()) append(' ').append(desc.ksuVariant)
                    if (desc.susfs) append(" SUSFS")
                    append(' ').append(desc.kernelLabel)
                }
                else -> {
                    // No descriptor — fall back to the step name but stripped of
                    // the noisy "{job}/{step}" prefixes that the old format used.
                    val progress = progressMap[run] ?: defaultFor(run)
                    "${runDisplayLabel(run)} ${progress.currentStep}"
                }
            }
        }
        // Keep the same middle-dot separator the card already uses between
        // percent and the first workflow label so multi-run rows read
        // consistently for both kernel and manager sections.
        return entries.joinToString(" · ")
    }

    private fun runDisplayLabel(run: WorkflowRun): String =
        if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"

    private val ACTIVE_RUN_STATUSES = setOf("queued", "waiting", "requested", "pending", "in_progress")

    /**
     * The kernel/manager workflow YAMLs upstream use Chinese names for their
     * steps (e.g. "编译内核"). We translate them client-side based on the
     * app's chosen UI locale; the YAML files stay 1:1 with upstream so they
     * keep working when the fork syncs. Unknown names pass through verbatim.
     */
    private fun translateWorkflowStepName(name: String): String {
        if (name.isBlank()) return name
        val trimmed = name.trim()
        val table = when (LocaleHelper.currentUiLanguage()) {
            LocaleHelper.LANG_RU -> WORKFLOW_STEP_RU
            LocaleHelper.LANG_EN -> WORKFLOW_STEP_EN
            else -> return name // zh (base): keep upstream YAML names for summary fetch
        }
        return table[trimmed] ?: name
    }

    private val WORKFLOW_STEP_RU: Map<String, String> = mapOf(
        "编译内核" to "Сборка ядра",
        "构建信息摘要" to "Сводка о сборке",
        "最大化构建空间" to "Расширение места на диске",
        "清理磁盘空间" to "Очистка диска",
        "安装依赖" to "Установка зависимостей",
        "安装编译依赖" to "Установка зависимостей для компиляции",
        "下载工具链" to "Загрузка тулчейна",
        "缓存工具链" to "Кэширование тулчейна",
        "克隆依赖仓库" to "Клонирование репозиториев",
        "检出代码仓库" to "Чекаут репозитория",
        "检出 ABK 补丁仓库" to "Чекаут репозитория патчей ABK",
        "初始化并同步内核源码" to "Инициализация и синхронизация исходников ядра",
        "同步 OnePlus 内核源码" to "Синхронизация исходников ядра OnePlus",
        "初始化构建环境" to "Инициализация окружения сборки",
        "添加 KernelSU" to "Добавление KernelSU",
        "应用 SUSFS 补丁" to "Применение патча SUSFS",
        "注入 SukiSU SUSFS Kconfig" to "Инжект Kconfig SukiSU SUSFS",
        "添加 SUSFS 配置" to "Добавление конфига SUSFS",
        "应用 Kernel 特定补丁" to "Применение патчей под версию ядра",
        "应用 KPM 补丁 (SukiSU/ReSukiSU)" to "Применение патча KPM (SukiSU/ReSukiSU)",
        "应用 NTsync 补丁" to "Применение патча NTsync",
        "应用 Networking 增强 (IPSet + BBR)" to "Сетевые улучшения (IPSet + BBR)",
        "应用 Re-Kernel" to "Применение Re-Kernel",
        "应用 Stock Config 伪装" to "Маскировка Stock Config",
        "添加 BBG 防格机补丁" to "Патч BBG (защита от форматирования)",
        "添加 DDK 防格机 LSM" to "DDK LSM (защита от форматирования)",
        "集成虚拟化支持" to "Интеграция поддержки виртуализации",
        "配置 defconfig" to "Конфигурация defconfig",
        "配置内核选项" to "Настройка опций ядра",
        "配置内核名称" to "Настройка имени ядра",
        "配置 ZRAM 选项" to "Настройка ZRAM",
        "配置 ZRAM LZ4 补丁栈" to "Настройка стека ZRAM LZ4",
        "配置 KPM 超级密码" to "Настройка пароля KPM",
        "配置 SukiSU 管理器信息" to "Настройка менеджера SukiSU",
        "配置 Git" to "Настройка Git",
        "配置 ccache" to "Настройка ccache",
        "恢复 ccache" to "Восстановление ccache",
        "恢复 ccache 缓存" to "Восстановление кэша ccache",
        "构建 Boot 镜像 (Android 12)" to "Сборка Boot-образа (Android 12)",
        "构建 Boot 镜像 (Android 13+)" to "Сборка Boot-образа (Android 13+)",
        "准备 Boot 镜像" to "Подготовка Boot-образа",
        "打包 AnyKernel3" to "Упаковка AnyKernel3",
        "创建 AnyKernel3 压缩包" to "Создание архива AnyKernel3",
        "上传内核 Image" to "Загрузка Image ядра",
        "上传 AnyKernel3 产物" to "Загрузка AnyKernel3",
        "上传构建产物" to "Загрузка артефактов сборки",
        "上传补丁冲突文件" to "Загрузка файлов конфликтов патчей",
        "收集补丁冲突文件" to "Сбор файлов конфликтов патчей",
        "克隆自定义外部模块" to "Клонирование внешних модулей",
        "克隆虚拟化支持补丁仓库" to "Клонирование репо патчей виртуализации",
        "执行自定义外部模块 (after_patch)" to "Внешние модули (after_patch)",
        "执行自定义外部模块 (before_build)" to "Внешние модули (before_build)",
        "提取实际子版本号" to "Извлечение фактического sub_level",
        "显示配置信息" to "Показ конфигурации",
        "设置自定义构建时间" to "Установка времени сборки",
        "生成签名密钥" to "Генерация подписного ключа",
        "准备 KernelSU 和补丁" to "Подготовка KernelSU и патчей",
        "确定 KernelSU 分支" to "Определение ветки KernelSU",
        "准备 ABK 管理器证书元数据" to "Подготовка сертификата ABK manager",
        "校验 ABK 管理器桥接" to "Проверка моста ABK manager",
        "校验 OnePlus 输入" to "Проверка входных данных OnePlus",
        "校验自定义内核版本组合" to "Проверка комбинации версий ядра",
        "备份基准 defconfig" to "Бэкап базового defconfig",
        "添加一加 8E 处理器支持" to "Поддержка процессора OnePlus 8E",
        "修复 6.6 WiFi/蓝牙兼容性（三星 + 小米）" to "Фикс совместимости WiFi/BT 6.6 (Samsung + Xiaomi)",
        "修复 Official SUSFS 源码兼容" to "Фикс совместимости исходников Official SUSFS",
        "修复 ReSukiSU SUSFS 源码兼容" to "Фикс совместимости исходников ReSukiSU SUSFS",
        "修复 SukiSU/ReSukiSU android16-6.12 源码兼容" to "Фикс совместимости SukiSU/ReSukiSU для android16-6.12",
        "修复 SukiSU/ReSukiSU sulog 兼容" to "Фикс совместимости sulog в SukiSU/ReSukiSU",
        "修复 glibc 2.38 兼容性" to "Фикс совместимости с glibc 2.38",
        "应用 Unicode 绕过修复" to "Применение Unicode bypass",
        "补齐 SukiSU SUSFS 内联 hook 符号" to "Доcбор символов inline-хуков SukiSU SUSFS",
        "最终修复 SukiSU/ReSukiSU 源码兼容" to "Финальный фикс совместимости SukiSU/ReSukiSU"
    )

    private val WORKFLOW_STEP_EN: Map<String, String> = mapOf(
        "编译内核" to "Compile kernel",
        "构建信息摘要" to "Build info summary",
        "最大化构建空间" to "Maximize build space",
        "清理磁盘空间" to "Clean disk space",
        "安装依赖" to "Install dependencies",
        "安装编译依赖" to "Install build dependencies",
        "下载工具链" to "Download toolchain",
        "缓存工具链" to "Cache toolchain",
        "克隆依赖仓库" to "Clone dependency repos",
        "检出代码仓库" to "Checkout code repository",
        "检出 ABK 补丁仓库" to "Checkout ABK patches repository",
        "初始化并同步内核源码" to "Init & sync kernel source",
        "同步 OnePlus 内核源码" to "Sync OnePlus kernel source",
        "初始化构建环境" to "Init build environment",
        "添加 KernelSU" to "Add KernelSU",
        "应用 SUSFS 补丁" to "Apply SUSFS patch",
        "注入 SukiSU SUSFS Kconfig" to "Inject SukiSU SUSFS Kconfig",
        "添加 SUSFS 配置" to "Add SUSFS config",
        "应用 Kernel 特定补丁" to "Apply kernel-specific patches",
        "应用 KPM 补丁 (SukiSU/ReSukiSU)" to "Apply KPM patch (SukiSU/ReSukiSU)",
        "应用 NTsync 补丁" to "Apply NTsync patch",
        "应用 Networking 增强 (IPSet + BBR)" to "Networking enhancements (IPSet + BBR)",
        "应用 Re-Kernel" to "Apply Re-Kernel",
        "应用 Stock Config 伪装" to "Apply Stock Config disguise",
        "添加 BBG 防格机补丁" to "Add BBG anti-format patch",
        "添加 DDK 防格机 LSM" to "Add DDK anti-format LSM",
        "集成虚拟化支持" to "Integrate virtualization support",
        "配置 defconfig" to "Configure defconfig",
        "配置内核选项" to "Configure kernel options",
        "配置内核名称" to "Configure kernel name",
        "配置 ZRAM 选项" to "Configure ZRAM options",
        "配置 ZRAM LZ4 补丁栈" to "Configure ZRAM LZ4 patch stack",
        "配置 KPM 超级密码" to "Configure KPM password",
        "配置 SukiSU 管理器信息" to "Configure SukiSU manager info",
        "配置 Git" to "Configure Git",
        "配置 ccache" to "Configure ccache",
        "恢复 ccache" to "Restore ccache",
        "恢复 ccache 缓存" to "Restore ccache cache",
        "构建 Boot 镜像 (Android 12)" to "Build Boot image (Android 12)",
        "构建 Boot 镜像 (Android 13+)" to "Build Boot image (Android 13+)",
        "准备 Boot 镜像" to "Prepare Boot image",
        "打包 AnyKernel3" to "Package AnyKernel3",
        "创建 AnyKernel3 压缩包" to "Create AnyKernel3 zip",
        "上传内核 Image" to "Upload kernel Image",
        "上传 AnyKernel3 产物" to "Upload AnyKernel3 artifact",
        "上传构建产物" to "Upload build artifacts",
        "上传补丁冲突文件" to "Upload patch-conflict files",
        "收集补丁冲突文件" to "Collect patch-conflict files",
        "克隆自定义外部模块" to "Clone custom external modules",
        "克隆虚拟化支持补丁仓库" to "Clone virtualization patch repo",
        "执行自定义外部模块 (after_patch)" to "Run custom modules (after_patch)",
        "执行自定义外部模块 (before_build)" to "Run custom modules (before_build)",
        "提取实际子版本号" to "Extract actual sub-level",
        "显示配置信息" to "Show configuration",
        "设置自定义构建时间" to "Set custom build time",
        "生成签名密钥" to "Generate signing key",
        "准备 KernelSU 和补丁" to "Prepare KernelSU & patches",
        "确定 KernelSU 分支" to "Determine KernelSU branch",
        "准备 ABK 管理器证书元数据" to "Prepare ABK manager cert metadata",
        "校验 ABK 管理器桥接" to "Verify ABK manager bridge",
        "校验 OnePlus 输入" to "Verify OnePlus inputs",
        "校验自定义内核版本组合" to "Verify custom kernel version combo",
        "备份基准 defconfig" to "Backup baseline defconfig",
        "添加一加 8E 处理器支持" to "Add OnePlus 8E SoC support",
        "修复 6.6 WiFi/蓝牙兼容性（三星 + 小米）" to "Fix 6.6 WiFi/BT compatibility (Samsung + Xiaomi)",
        "修复 Official SUSFS 源码兼容" to "Fix Official SUSFS source compatibility",
        "修复 ReSukiSU SUSFS 源码兼容" to "Fix ReSukiSU SUSFS source compatibility",
        "修复 SukiSU/ReSukiSU android16-6.12 源码兼容" to "Fix SukiSU/ReSukiSU android16-6.12 compatibility",
        "修复 SukiSU/ReSukiSU sulog 兼容" to "Fix SukiSU/ReSukiSU sulog compatibility",
        "修复 glibc 2.38 兼容性" to "Fix glibc 2.38 compatibility",
        "应用 Unicode 绕过修复" to "Apply Unicode bypass fix",
        "补齐 SukiSU SUSFS 内联 hook 符号" to "Complete SukiSU SUSFS inline-hook symbols",
        "最终修复 SukiSU/ReSukiSU 源码兼容" to "Final SukiSU/ReSukiSU source compatibility fix"
    )
}
