package com.abk.kernel.data.model

import kotlin.math.abs

data class KernelSupportEntry(
    val androidVersion: String,
    val kernelVersion: String,
    val subLevel: String,
    val osPatchLevel: String
)

data class KernelVersionLine(
    val androidVersion: String,
    val kernelVersion: String
)

object KernelSupport {
    val lines = listOf(
        KernelVersionLine("android12", "5.10"),
        KernelVersionLine("android13", "5.15"),
        KernelVersionLine("android14", "6.1"),
        KernelVersionLine("android15", "6.6"),
        KernelVersionLine("android16", "6.12")
    )

    val entries = listOf(
        KernelSupportEntry("android12", "5.10", "43", "2021-08"),
        KernelSupportEntry("android12", "5.10", "43", "2021-09"),
        KernelSupportEntry("android12", "5.10", "43", "2021-10"),
        KernelSupportEntry("android12", "5.10", "66", "2021-11"),
        KernelSupportEntry("android12", "5.10", "66", "2021-12"),
        KernelSupportEntry("android12", "5.10", "66", "2022-01"),
        KernelSupportEntry("android12", "5.10", "81", "2022-02"),
        KernelSupportEntry("android12", "5.10", "81", "2022-03"),
        KernelSupportEntry("android12", "5.10", "101", "2022-04"),
        KernelSupportEntry("android12", "5.10", "101", "2022-05"),
        KernelSupportEntry("android12", "5.10", "110", "2022-06"),
        KernelSupportEntry("android12", "5.10", "110", "2022-07"),
        KernelSupportEntry("android12", "5.10", "117", "2022-08"),
        KernelSupportEntry("android12", "5.10", "117", "2022-09"),
        KernelSupportEntry("android12", "5.10", "136", "2022-10"),
        KernelSupportEntry("android12", "5.10", "136", "2022-11"),
        KernelSupportEntry("android12", "5.10", "149", "2022-12"),
        KernelSupportEntry("android12", "5.10", "149", "2023-01"),
        KernelSupportEntry("android12", "5.10", "160", "2023-02"),
        KernelSupportEntry("android12", "5.10", "160", "2023-03"),
        KernelSupportEntry("android12", "5.10", "168", "2023-04"),
        KernelSupportEntry("android12", "5.10", "168", "2023-05"),
        KernelSupportEntry("android12", "5.10", "177", "2023-06"),
        KernelSupportEntry("android12", "5.10", "177", "2023-07"),
        KernelSupportEntry("android12", "5.10", "185", "2023-09"),
        KernelSupportEntry("android12", "5.10", "198", "2023-11"),
        KernelSupportEntry("android12", "5.10", "198", "2024-01"),
        KernelSupportEntry("android12", "5.10", "205", "2024-03"),
        KernelSupportEntry("android12", "5.10", "209", "2024-05"),
        KernelSupportEntry("android12", "5.10", "218", "2024-08"),
        KernelSupportEntry("android12", "5.10", "226", "2024-11"),
        KernelSupportEntry("android12", "5.10", "233", "2025-02"),
        KernelSupportEntry("android12", "5.10", "236", "2025-05"),
        KernelSupportEntry("android12", "5.10", "237", "2025-06"),
        KernelSupportEntry("android12", "5.10", "240", "2025-09"),
        KernelSupportEntry("android12", "5.10", "246", "2025-12"),
        KernelSupportEntry("android13", "5.15", "41", "2022-06"),
        KernelSupportEntry("android13", "5.15", "41", "2022-07"),
        KernelSupportEntry("android13", "5.15", "41", "2022-08"),
        KernelSupportEntry("android13", "5.15", "41", "2022-09"),
        KernelSupportEntry("android13", "5.15", "41", "2022-10"),
        KernelSupportEntry("android13", "5.15", "41", "2022-11"),
        KernelSupportEntry("android13", "5.15", "74", "2022-12"),
        KernelSupportEntry("android13", "5.15", "74", "2023-01"),
        KernelSupportEntry("android13", "5.15", "78", "2023-02"),
        KernelSupportEntry("android13", "5.15", "78", "2023-03"),
        KernelSupportEntry("android13", "5.15", "94", "2023-04"),
        KernelSupportEntry("android13", "5.15", "94", "2023-05"),
        KernelSupportEntry("android13", "5.15", "104", "2023-06"),
        KernelSupportEntry("android13", "5.15", "104", "2023-07"),
        KernelSupportEntry("android13", "5.15", "119", "2023-08"),
        KernelSupportEntry("android13", "5.15", "119", "2023-09"),
        KernelSupportEntry("android13", "5.15", "123", "2023-10"),
        KernelSupportEntry("android13", "5.15", "123", "2023-11"),
        KernelSupportEntry("android13", "5.15", "137", "2023-12"),
        KernelSupportEntry("android13", "5.15", "137", "2024-01"),
        KernelSupportEntry("android13", "5.15", "144", "2024-02"),
        KernelSupportEntry("android13", "5.15", "144", "2024-03"),
        KernelSupportEntry("android13", "5.15", "148", "2024-04"),
        KernelSupportEntry("android13", "5.15", "148", "2024-05"),
        KernelSupportEntry("android13", "5.15", "149", "2024-06"),
        KernelSupportEntry("android13", "5.15", "149", "2024-07"),
        KernelSupportEntry("android13", "5.15", "151", "2024-08"),
        KernelSupportEntry("android13", "5.15", "153", "2024-09"),
        KernelSupportEntry("android13", "5.15", "167", "2024-11"),
        KernelSupportEntry("android13", "5.15", "170", "2025-01"),
        KernelSupportEntry("android13", "5.15", "178", "2025-03"),
        KernelSupportEntry("android13", "5.15", "180", "2025-05"),
        KernelSupportEntry("android13", "5.15", "185", "2025-07"),
        KernelSupportEntry("android13", "5.15", "189", "2025-09"),
        KernelSupportEntry("android13", "5.15", "194", "2025-12"),
        KernelSupportEntry("android14", "6.1", "25", "2023-06"),
        KernelSupportEntry("android14", "6.1", "25", "2023-07"),
        KernelSupportEntry("android14", "6.1", "25", "2023-08"),
        KernelSupportEntry("android14", "6.1", "25", "2023-09"),
        KernelSupportEntry("android14", "6.1", "25", "2023-10"),
        KernelSupportEntry("android14", "6.1", "43", "2023-11"),
        KernelSupportEntry("android14", "6.1", "57", "2023-12"),
        KernelSupportEntry("android14", "6.1", "57", "2024-01"),
        KernelSupportEntry("android14", "6.1", "68", "2024-02"),
        KernelSupportEntry("android14", "6.1", "68", "2024-03"),
        KernelSupportEntry("android14", "6.1", "75", "2024-04"),
        KernelSupportEntry("android14", "6.1", "75", "2024-05"),
        KernelSupportEntry("android14", "6.1", "78", "2024-06"),
        KernelSupportEntry("android14", "6.1", "84", "2024-07"),
        KernelSupportEntry("android14", "6.1", "90", "2024-08"),
        KernelSupportEntry("android14", "6.1", "93", "2024-09"),
        KernelSupportEntry("android14", "6.1", "99", "2024-10"),
        KernelSupportEntry("android14", "6.1", "112", "2024-11"),
        KernelSupportEntry("android14", "6.1", "115", "2024-12"),
        KernelSupportEntry("android14", "6.1", "118", "2025-01"),
        KernelSupportEntry("android14", "6.1", "124", "2025-02"),
        KernelSupportEntry("android14", "6.1", "128", "2025-03"),
        KernelSupportEntry("android14", "6.1", "129", "2025-04"),
        KernelSupportEntry("android14", "6.1", "134", "2025-05"),
        KernelSupportEntry("android14", "6.1", "138", "2025-06"),
        KernelSupportEntry("android14", "6.1", "141", "2025-07"),
        KernelSupportEntry("android14", "6.1", "145", "2025-08"),
        KernelSupportEntry("android14", "6.1", "145", "2025-09"),
        KernelSupportEntry("android14", "6.1", "157", "2025-12"),
        KernelSupportEntry("android14", "6.1", "162", "2026-03"),
        KernelSupportEntry("android15", "6.6", "50", "2024-10"),
        KernelSupportEntry("android15", "6.6", "56", "2024-11"),
        KernelSupportEntry("android15", "6.6", "57", "2024-12"),
        KernelSupportEntry("android15", "6.6", "58", "2025-01"),
        KernelSupportEntry("android15", "6.6", "66", "2025-02"),
        KernelSupportEntry("android15", "6.6", "77", "2025-03"),
        KernelSupportEntry("android15", "6.6", "82", "2025-04"),
        KernelSupportEntry("android15", "6.6", "87", "2025-05"),
        KernelSupportEntry("android15", "6.6", "89", "2025-06"),
        KernelSupportEntry("android15", "6.6", "92", "2025-07"),
        KernelSupportEntry("android15", "6.6", "98", "2025-08"),
        KernelSupportEntry("android15", "6.6", "98", "2025-09"),
        KernelSupportEntry("android15", "6.6", "102", "2025-10"),
        KernelSupportEntry("android15", "6.6", "118", "2026-01"),
        KernelSupportEntry("android15", "6.6", "127", "2026-04"),
        KernelSupportEntry("android16", "6.12", "23", "2025-06"),
        KernelSupportEntry("android16", "6.12", "30", "2025-07"),
        KernelSupportEntry("android16", "6.12", "38", "2025-08"),
        KernelSupportEntry("android16", "6.12", "38", "2025-09"),
        KernelSupportEntry("android16", "6.12", "58", "2025-12"),
        KernelSupportEntry("android16", "6.12", "69", "2026-03")
    )

    fun androidVersions(): List<String> = lines.map { it.androidVersion }

    fun kernelVersions(): List<String> = lines.map { it.kernelVersion }

    fun kernelForAndroid(androidVersion: String): String =
        lines.firstOrNull { it.androidVersion == androidVersion }?.kernelVersion ?: lines.first().kernelVersion

    fun androidForKernel(kernelVersion: String): String =
        lines.firstOrNull { it.kernelVersion == kernelVersion }?.androidVersion ?: lines.first().androidVersion

    fun normalize(config: KernelBuildConfig): KernelBuildConfig {
        val line = lineFor(config.androidVersion, config.kernelVersion)
        val subLevel = when {
            config.subLevel == "X" -> "X"
            subLevels(line).contains(config.subLevel) -> config.subLevel
            else -> latestEntry(line).subLevel
        }
        val patchOptions = patchLevels(line, subLevel)
        val osPatch = when {
            config.osPatchLevel in patchOptions -> config.osPatchLevel
            else -> patchOptions.maxByOrNull(::patchMonthIndex) ?: latestEntry(line).osPatchLevel
        }
        return config.copy(
            androidVersion = line.androidVersion,
            kernelVersion = line.kernelVersion,
            subLevel = subLevel,
            osPatchLevel = osPatch,
            droidspaces = normalizeDroidspaces(line.kernelVersion, config.droidspaces),
            customExternalModules = config.customExternalModules.orEmpty()
                .mapNotNull { module ->
                    val url = module.url.trim()
                    if (url.isBlank()) {
                        null
                    } else {
                        module.copy(
                            url = url,
                            stage = CustomExternalModuleStage.normalize(module.stage)
                        )
                    }
                }
        )
    }

    fun droidspacesOptions(kernelVersion: String): List<String> =
        if (kernelVersion == "6.12") listOf("off", "on") else listOf("off", "678", "123", "345")

    private fun normalizeDroidspaces(kernelVersion: String, value: String): String {
        val normalized = value.trim().lowercase()
        return when {
            normalized in droidspacesOptions(kernelVersion) -> normalized
            kernelVersion == "6.12" && normalized in setOf("678", "123", "345") -> "on"
            else -> "off"
        }
    }

    fun subLevelOptions(androidVersion: String, kernelVersion: String): List<String> =
        subLevels(lineFor(androidVersion, kernelVersion)) + "X"

    fun patchLevelOptions(androidVersion: String, kernelVersion: String, subLevel: String): List<String> =
        patchLevels(lineFor(androidVersion, kernelVersion), subLevel)

    fun recommendedFromKernel(kernelRelease: String): KernelBuildConfig {
        val lower = kernelRelease.lowercase()
        val line = detectLine(lower)
        val detectedSub = Regex("""\b${Regex.escape(line.kernelVersion)}\.(\d+)""")
            .find(lower)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val detectedPatch = Regex("""20\d{2}-\d{2}""").find(lower)?.value
        val chosenSub = chooseSubLevel(line, detectedSub)
        val chosenPatch = choosePatch(line, chosenSub, detectedPatch)
        return KernelBuildConfig(
            androidVersion = line.androidVersion,
            kernelVersion = line.kernelVersion,
            subLevel = chosenSub,
            osPatchLevel = chosenPatch,
            revision = revisionFor(line.kernelVersion, chosenSub).orEmpty()
        )
    }

    private fun lineFor(androidVersion: String, kernelVersion: String): KernelVersionLine {
        val byPair = lines.firstOrNull {
            it.androidVersion == androidVersion && it.kernelVersion == kernelVersion
        }
        if (byPair != null) return byPair
        return lines.firstOrNull { it.androidVersion == androidVersion }
            ?: lines.firstOrNull { it.kernelVersion == kernelVersion }
            ?: lines.first()
    }

    private fun detectLine(kernelRelease: String): KernelVersionLine {
        val byAndroid = lines.firstOrNull { kernelRelease.contains(it.androidVersion) }
        if (byAndroid != null) return byAndroid
        return lines.firstOrNull { kernelRelease.containsKernelVersion(it.kernelVersion) } ?: lines.first()
    }

    private fun String.containsKernelVersion(version: String): Boolean =
        Regex("""(^|[^0-9.])${Regex.escape(version)}($|[^0-9])""").containsMatchIn(this)

    private fun entriesFor(line: KernelVersionLine): List<KernelSupportEntry> =
        entries.filter { it.androidVersion == line.androidVersion && it.kernelVersion == line.kernelVersion }

    private fun subLevels(line: KernelVersionLine): List<String> =
        entriesFor(line).map { it.subLevel }.distinct().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

    private fun patchLevels(line: KernelVersionLine, subLevel: String): List<String> {
        if (subLevel == "X") return listOf("lts")
        return entriesFor(line)
            .filter { it.subLevel == subLevel }
            .map { it.osPatchLevel }
            .distinct()
            .sortedBy(::patchMonthIndex)
            .ifEmpty { listOf(latestEntry(line).osPatchLevel) }
    }

    private fun latestEntry(line: KernelVersionLine): KernelSupportEntry =
        entriesFor(line).maxWithOrNull(
            compareBy<KernelSupportEntry> { it.subLevel.toIntOrNull() ?: -1 }
                .thenBy { patchMonthIndex(it.osPatchLevel) }
        ) ?: entries.first()

    private fun chooseSubLevel(line: KernelVersionLine, detectedSub: Int?): String {
        val numeric = subLevels(line).mapNotNull { value -> value.toIntOrNull()?.let { value to it } }
        if (numeric.isEmpty()) return latestEntry(line).subLevel
        if (detectedSub == null) return numeric.maxBy { it.second }.first
        return numeric.minWith(
            compareBy<Pair<String, Int>> { abs(it.second - detectedSub) }
                .thenByDescending { it.second }
        ).first
    }

    private fun choosePatch(line: KernelVersionLine, subLevel: String, detectedPatch: String?): String {
        val patches = patchLevels(line, subLevel)
        val detectedMonth = detectedPatch?.let(::patchMonthIndex)
        if (detectedMonth == null) return patches.maxByOrNull(::patchMonthIndex) ?: patches.first()
        return patches.minWith(
            compareBy<String> { abs(patchMonthIndex(it) - detectedMonth) }
                .thenByDescending { patchMonthIndex(it) }
        )
    }

    private fun patchMonthIndex(patch: String): Int {
        if (patch == "lts") return Int.MAX_VALUE
        val parts = patch.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return year * 12 + month
    }

    private fun revisionFor(kernelVersion: String, subLevel: String): String? =
        kernelRevisionMap[kernelVersion]?.get(subLevel)
}

private val kernelRevisionMap = mapOf(
    "5.10" to mapOf(
        "43" to "r1",
        "66" to "r11",
        "81" to "r11",
        "101" to "r28",
        "110" to "r1",
        "117" to "r1",
        "136" to "r15",
        "149" to "r1",
        "160" to "r1",
        "168" to "r9",
        "177" to "r3",
        "185" to "r1",
        "198" to "r17",
        "205" to "r1",
        "209" to "r13",
        "218" to "r14",
        "226" to "r8",
        "233" to "r1",
        "236" to "r1",
        "237" to "r1",
        "240" to "r1",
        "246" to "r1"
    )
)
