package com.abk.kernel.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.abk.kernel.data.model.LspInstalledModule
import com.abk.kernel.data.model.LspScopeApp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties
import java.util.zip.ZipFile

object LspModuleUtils {
    private const val MODERN_ENTRY = "META-INF/xposed/java_init.list"
    private const val MODERN_PROP = "META-INF/xposed/module.prop"
    private const val MODERN_SCOPE = "META-INF/xposed/scope.list"
    private const val LEGACY_ENTRY = "assets/xposed_init"

    fun listInstalledModules(context: Context): List<LspInstalledModule> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { pkg ->
            toLspInstalledModule(pm, pkg)
        }.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    fun listScopeApps(context: Context): List<LspScopeApp> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }
        val modulePackages = packages.mapNotNull { pkg ->
            pkg.takeIf { toLspInstalledModule(pm, it) != null }?.packageName
        }.toSet()
        return packages.mapNotNull { pkg ->
            val info = pkg.applicationInfo ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(info.packageName)
            LspScopeApp(
                packageName = info.packageName,
                label = label,
                versionName = pkg.versionName.orEmpty(),
                installTime = pkg.firstInstallTime,
                updateTime = pkg.lastUpdateTime,
                isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isGame = info.category == ApplicationInfo.CATEGORY_GAME ||
                    (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0,
                isModule = info.packageName in modulePackages
            )
        }.sortedWith(
            compareBy<LspScopeApp> { it.isSystemApp }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName }
        )
    }

    private fun toLspInstalledModule(pm: PackageManager, pkg: PackageInfo): LspInstalledModule? {
        val app = pkg.applicationInfo ?: return null
        val modern = getModernModuleApk(app)
        val legacy = isLegacyModule(app)
        if (modern == null && !legacy) return null

        val label = runCatching {
            pm.getApplicationLabel(app).toString()
        }.getOrDefault(pkg.packageName)

        return if (modern != null) {
            modern.use { zip ->
                val props = Properties()
                zip.getEntry(MODERN_PROP)?.let { entry ->
                    zip.getInputStream(entry).use(props::load)
                }
                val scopeHints = readZipLines(zip, MODERN_SCOPE)
                val entryPoints = readZipLines(zip, MODERN_ENTRY)
                val compatEntryPoints = readZipLines(zip, LEGACY_ENTRY)
                LspInstalledModule(
                    packageName = pkg.packageName,
                    label = label,
                    versionName = pkg.versionName.orEmpty(),
                    versionCode = longVersionCodeCompat(pkg),
                    description = props.getProperty("description", ""),
                    modern = entryPoints.isNotEmpty(),
                    legacy = legacy,
                    minVersion = props.getProperty("minApiVersion", "0").toIntOrNull() ?: 0,
                    targetVersion = props.getProperty("targetApiVersion", "0").toIntOrNull() ?: 0,
                    staticScope = props.getProperty("staticScope") == "true",
                    installTime = pkg.firstInstallTime,
                    updateTime = pkg.lastUpdateTime,
                    sourceApk = app.sourceDir.orEmpty(),
                    scopeHints = scopeHints,
                    entryPoints = entryPoints,
                    compatEntryPoints = compatEntryPoints,
                    enabled = false
                )
            }
        } else {
            val metaData = app.metaData
            val scopeString = metaData?.getString("xposedscope").orEmpty()
            LspInstalledModule(
                packageName = pkg.packageName,
                label = label,
                versionName = pkg.versionName.orEmpty(),
                versionCode = longVersionCodeCompat(pkg),
                description = metaData?.getString("xposeddescription").orEmpty(),
                modern = false,
                legacy = true,
                minVersion = extractIntPart(metaData?.get("xposedminversion")?.toString().orEmpty()),
                targetVersion = 0,
                installTime = pkg.firstInstallTime,
                updateTime = pkg.lastUpdateTime,
                sourceApk = app.sourceDir.orEmpty(),
                scopeHints = scopeString.split(';').map { it.trim() }.filter { it.isNotBlank() },
                entryPoints = emptyList(),
                compatEntryPoints = getCompatEntryPoints(app),
                enabled = false
            )
        }
    }

    private fun getModernModuleApk(info: ApplicationInfo): ZipFile? {
        val splitSourceDirs = info.splitSourceDirs
        val apks = if (splitSourceDirs != null) {
            splitSourceDirs + info.sourceDir
        } else {
            arrayOf(info.sourceDir)
        }
        for (apk in apks) {
            try {
                val zip = ZipFile(apk)
                if (zip.getEntry(MODERN_ENTRY) != null || zip.getEntry(LEGACY_ENTRY) != null) {
                    return zip
                }
                zip.close()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isLegacyModule(info: ApplicationInfo): Boolean =
        info.metaData?.containsKey("xposedminversion") == true

    private fun getCompatEntryPoints(info: ApplicationInfo): List<String> {
        val splitSourceDirs = info.splitSourceDirs
        val apks = if (splitSourceDirs != null) {
            splitSourceDirs + info.sourceDir
        } else {
            arrayOf(info.sourceDir)
        }
        for (apk in apks) {
            try {
                ZipFile(apk).use { zip ->
                    val entries = readZipLines(zip, LEGACY_ENTRY)
                    if (entries.isNotEmpty()) return entries
                }
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun readZipLines(zip: ZipFile, path: String): List<String> {
        val entry = zip.getEntry(path) ?: return emptyList()
        return BufferedReader(InputStreamReader(zip.getInputStream(entry))).use { reader ->
            reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .toList()
        }
    }

    @Suppress("DEPRECATION")
    private fun longVersionCodeCompat(pkg: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            pkg.versionCode.toLong()
        }

    private fun extractIntPart(str: String): Int {
        var result = 0
        for (char in str) {
            if (char !in '0'..'9') break
            result = result * 10 + (char - '0')
        }
        return result
    }
}
