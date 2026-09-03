package com.abk.kernel.miuix.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Source
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.MainUiState
import top.yukonga.miuix.kmp.blur.layerBackdrop

private data class MiuixOpenSourceNotice(
    val name: String,
    val license: String,
    val source: String,
    val url: String? = null
)

private data class MiuixOpenSourceNoticeGroup(
    val titleRes: Int,
    val items: List<MiuixOpenSourceNotice>
)

@Composable
fun OpenSourceLicensesScreenMiuix(vm: MainViewModel) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    var selectedNotice by remember { mutableStateOf<MiuixOpenSourceNotice?>(null) }
    val state by vm.uiState.collectAsState()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings_open_source_licenses),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        val groups = remember { miuixOpenSourceNoticeGroups() }
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical()
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null
            ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Text(
                    text = stringResource(R.string.settings_open_source_licenses_intro),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            groups.forEach { group ->
                item { SmallTitle(stringResource(group.titleRes)) }
                item {
                    Card {
                        group.items.forEach { notice ->
                            val subtitle = listOfNotNull(
                                notice.license,
                                notice.source.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            val startSlot = @Composable {
                                Icon(
                                    imageVector = Icons.Default.Source,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                            if (notice.url != null) {
                                ArrowPreference(
                                    title = notice.name,
                                    summary = subtitle,
                                    startAction = startSlot,
                                    onClick = { selectedNotice = notice }
                                )
                            } else {
                                BasicComponent(
                                    title = notice.name,
                                    summary = subtitle,
                                    startAction = startSlot
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
    }

    selectedNotice?.let { notice ->
        WindowDialog(
            show = true,
            title = notice.name,
            onDismissRequest = { selectedNotice = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = notice.license,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.primary
                        )
                        if (notice.source.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${stringResource(R.string.settings_oss_source_label)}: ${notice.source}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!notice.url.isNullOrBlank()) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { openUrl(notice.url) },
                            text = stringResource(R.string.settings_oss_visit_homepage)
                        )
                    }

                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { selectedNotice = null },
                        text = stringResource(R.string.close),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

private fun sourceRepoUrl(): String =
    "https://github.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"

private fun miuixOpenSourceNoticeGroups(): List<MiuixOpenSourceNoticeGroup> = listOf(
    MiuixOpenSourceNoticeGroup(
        R.string.settings_license_group_repository,
        listOf(
            MiuixOpenSourceNotice("AnyBase Kernel", "GPL-3.0", "LICENSE", sourceRepoUrl()),
            MiuixOpenSourceNotice("ABK Control native bridge", "GPL-2.0", "app/src/main/cpp/uapi/abk_control.h"),
            MiuixOpenSourceNotice("xingguang DDK module", "GPL", "ddk/xingguang-ddk/xingguang_ddk.c"),
            MiuixOpenSourceNotice("DDK kernel API patch", "GPL-2.0", "ddk/patches/xingguang-ddk/0001-xingguang-ddk-api.patch"),
            MiuixOpenSourceNotice("ZRAM LZ4 kernel glue", "GPL-2.0-only", "zram/lz4/Makefile"),
            MiuixOpenSourceNotice("LZ4 sources and headers", "BSD-2-Clause", "zram/lz4, zram/include/linux/lz4.h")
        )
    ),
    MiuixOpenSourceNoticeGroup(
        R.string.settings_license_group_upstream,
        listOf(
            MiuixOpenSourceNotice("zzh20188/GKI_KernelSU_SUSFS", "Upstream repository license", "BuildConfig.UPSTREAM_REPO_URL", BuildConfig.UPSTREAM_REPO_URL),
            MiuixOpenSourceNotice("WildKernels/GKI_KernelSU_SUSFS", "Upstream repository license", "BuildConfig.TOP_LEVEL_REPO_URL", BuildConfig.TOP_LEVEL_REPO_URL),
            MiuixOpenSourceNotice("CodeLinaro CLO LA", "Top-level upstream project licenses", "git.codelinaro.org/clo/la", "https://git.codelinaro.org/clo/la"),
            MiuixOpenSourceNotice("OnePlusOSS/kernel_manifest", "Upstream repository license", "OnePlus manifest parent", "https://github.com/OnePlusOSS/kernel_manifest"),
            MiuixOpenSourceNotice("Xiaomichael/kernel_manifest", "Upstream repository license", "OnePlus manifest branch source", "https://github.com/Xiaomichael/kernel_manifest"),
            MiuixOpenSourceNotice("Xiaomichael/kernel_patches", "Upstream repository license", "OnePlus patch source", "https://github.com/Xiaomichael/kernel_patches"),
            MiuixOpenSourceNotice("KernelSU", "GPL-3.0", "workflow setup.sh source", "https://github.com/tiann/KernelSU"),
            MiuixOpenSourceNotice("KernelSU Next", "GPL-3.0", "workflow setup.sh source", "https://github.com/KernelSU-Next/KernelSU-Next"),
            MiuixOpenSourceNotice("SukiSU Ultra", "GPL-3.0", "kernel setup, ksud, android_bootimg", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
            MiuixOpenSourceNotice("ReSukiSU", "GPL-3.0", "workflow setup.sh source", "https://github.com/ReSukiSU/ReSukiSU"),
            MiuixOpenSourceNotice("SUSFS", "GPL-2.0", "kernel patches and module integration", "https://gitlab.com/simonpunk/susfs4ksu"),
            MiuixOpenSourceNotice("ShirkNeko/susfs4ksu", "GPL-2.0", "GitHub mirror / patch source", "https://github.com/ShirkNeko/susfs4ksu"),
            MiuixOpenSourceNotice("SukiSU_patch", "GPL-2.0", "workflow patch source", "https://github.com/ShirkNeko/SukiSU_patch"),
            MiuixOpenSourceNotice("AnyKernel3", "GPL-2.0", "flashable kernel packaging", "https://github.com/WildKernels/AnyKernel3"),
            MiuixOpenSourceNotice("Xiaomichael/AnyKernel3", "Upstream repository license", "OnePlus flashable packaging", "https://github.com/Xiaomichael/AnyKernel3"),
            MiuixOpenSourceNotice("WildKernels/kernel_patches", "GPL-2.0", "NTsync, IPSet, BBR patches", "https://github.com/WildKernels/kernel_patches"),
            MiuixOpenSourceNotice("cctv18/susfs4oki", "GPL-3.0", "OnePlus/OPPO/Realme SUSFS", "https://github.com/cctv18/susfs4oki"),
            MiuixOpenSourceNotice("SukiSU_KernelPatch_patch", "Upstream repository license", "KPM patch source", "https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch"),
            MiuixOpenSourceNotice("Action-Build", "Upstream repository license", "workflow integration", "https://github.com/Numbersf/Action-Build"),
            MiuixOpenSourceNotice("sidex15/susfs4ksu-module", "Upstream repository license", "SUSFS module build source", "https://github.com/sidex15/susfs4ksu-module"),
            MiuixOpenSourceNotice("LineageOS GCC prebuilts", "GPL-family toolchain notices", "workflow toolchain", "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"),
            MiuixOpenSourceNotice("Baseband Guard", "Upstream repository license", "workflow setup", "https://github.com/vc-teahouse/Baseband-guard"),
            MiuixOpenSourceNotice("Re-Kernel", "Upstream repository license", "workflow patch", "https://github.com/Sakion-Team/Re-Kernel"),
            MiuixOpenSourceNotice("Droidspaces-OSS", "Upstream repository license", "virtualization patches", "https://github.com/ravindu644/Droidspaces-OSS"),
            MiuixOpenSourceNotice("ABK_repo module catalog", "Upstream repository license", "official module catalog", "https://github.com/xingguangcuican6666/ABK_repo")
        )
    ),
    MiuixOpenSourceNoticeGroup(
        R.string.settings_license_group_embedded,
        listOf(
            MiuixOpenSourceNotice("AOSP kernel/common", "GPL-2.0 WITH Linux-syscall-note", "android.googlesource.com/kernel/common", "https://android.googlesource.com/kernel/common"),
            MiuixOpenSourceNotice("AOSP kernel manifest", "AOSP project notices", "android.googlesource.com/kernel/manifest", "https://android.googlesource.com/kernel/manifest"),
            MiuixOpenSourceNotice("AOSP mkbootimg", "Apache-2.0", "system/tools/mkbootimg", "https://android.googlesource.com/platform/system/tools/mkbootimg"),
            MiuixOpenSourceNotice("AOSP kernel build-tools", "AOSP project notices", "kernel/prebuilts/build-tools", "https://android.googlesource.com/kernel/prebuilts/build-tools"),
            MiuixOpenSourceNotice("Android GKI certified boot images", "Android image distribution terms", "dl.google.com/android/gki"),
            MiuixOpenSourceNotice("Android command line tools", "Android SDK License", "Dockerfile.test", "https://developer.android.com/studio")
        )
    ),
    MiuixOpenSourceNoticeGroup(
        R.string.settings_license_group_android,
        miuixAndroidDependencyNotices()
    ),
    MiuixOpenSourceNoticeGroup(
        R.string.settings_license_group_web,
        miuixWebDependencyNotices()
    )
)

private fun miuixAndroidDependencyNotices(): List<MiuixOpenSourceNotice> = listOf(
    MiuixOpenSourceNotice("compose-miuix-ui (MIUIX) 0.9.2", "Apache-2.0", "top.yukonga.miuix.kmp", "https://github.com/compose-miuix-ui/miuix"),
    MiuixOpenSourceNotice("Kyant0 Backdrop (AndroidLiquidGlass) 2.0.0", "Apache-2.0", "io.github.kyant0:backdrop", "https://github.com/Kyant0/AndroidLiquidGlass"),
    MiuixOpenSourceNotice("Android Gradle Plugin 9.1.1", "Apache-2.0", "com.android.application"),
    MiuixOpenSourceNotice("Kotlin Gradle/Compose plugin 2.4.0", "Apache-2.0", "org.jetbrains.kotlin.plugin.compose"),
    MiuixOpenSourceNotice("androidx.core:core-ktx 1.15.0", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.lifecycle:lifecycle-runtime-ktx 2.8.7", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.lifecycle:lifecycle-viewmodel-compose 2.8.7", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.lifecycle:lifecycle-process 2.8.7", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.lifecycle:lifecycle-viewmodel-navigation3 2.10.0", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.activity:activity-compose 1.9.3", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.compose:compose-bom 2026.05.00", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.compose.ui:ui", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.compose.ui:ui-graphics", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.compose.ui:ui-tooling-preview", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.compose.material3:material3 1.5.0-alpha19", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.compose.material:material-icons-extended", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("com.google.android.material:material 1.12.0", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.navigation:navigation-compose 2.8.5", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("androidx.navigation3:navigation3-runtime 1.1.2", "Apache-2.0", "Gradle direct dependency"),
    MiuixOpenSourceNotice("Retrofit 2.11.0", "Apache-2.0", "com.squareup.retrofit2:retrofit"),
    MiuixOpenSourceNotice("Retrofit Gson converter 2.11.0", "Apache-2.0", "com.squareup.retrofit2:converter-gson"),
    MiuixOpenSourceNotice("OkHttp 4.12.0", "Apache-2.0", "com.squareup.okhttp3:okhttp"),
    MiuixOpenSourceNotice("OkHttp logging-interceptor 4.12.0", "Apache-2.0", "com.squareup.okhttp3:logging-interceptor"),
    MiuixOpenSourceNotice("Gson 2.11.0", "Apache-2.0", "com.google.code.gson:gson"),
    MiuixOpenSourceNotice("kotlinx-serialization-json 1.7.3", "Apache-2.0", "org.jetbrains.kotlinx:kotlinx-serialization-json"),
    MiuixOpenSourceNotice("libsu core 5.2.2", "Apache-2.0", "com.github.topjohnwu.libsu:core"),
    MiuixOpenSourceNotice("libsu io 5.2.2", "Apache-2.0", "com.github.topjohnwu.libsu:io"),
    MiuixOpenSourceNotice("Coil Compose 2.7.0", "Apache-2.0", "io.coil-kt:coil-compose"),
    MiuixOpenSourceNotice("WorkManager runtime-ktx 2.10.0", "Apache-2.0", "androidx.work:work-runtime-ktx"),
    MiuixOpenSourceNotice("DataStore preferences 1.1.2", "Apache-2.0", "androidx.datastore:datastore-preferences"),
    MiuixOpenSourceNotice("JUnit 4.13.2", "EPL-1.0", "testImplementation")
)

private fun miuixWebDependencyNotices(): List<MiuixOpenSourceNotice> = listOf(
    MiuixOpenSourceNotice("@discoveryjs/json-ext 0.6.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("@jridgewell/* 0.3.x", "MIT", "source-map tooling"),
    MiuixOpenSourceNotice("@parcel/watcher 2.5.6", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("@types/* (eslint, estree, json-schema, node)", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("@webassemblyjs/* 1.13.2-1.14.1", "MIT / Apache-2.0", "web/package-lock.json"),
    MiuixOpenSourceNotice("@xtuc/ieee754, @xtuc/long", "BSD-3-Clause / Apache-2.0", "web/package-lock.json"),
    MiuixOpenSourceNotice("acorn, acorn-import-attributes", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("ajv, ajv-formats, ajv-keywords", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("browserslist, caniuse-lite, electron-to-chromium", "MIT / CC-BY-4.0", "web/package-lock.json"),
    MiuixOpenSourceNotice("chokidar 4.0.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("chrome-trace-event 1.0.4", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("clone-deep 4.0.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("colorette 2.0.20", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("commander 2.20.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("cross-spawn 7.0.6", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("css-loader 7.1.2, cssesc 3.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("electron-to-chromium 2.2.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("enhanced-resolve 5.18.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("envinfo 7.21.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("es-module-lexer 1.7.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("eslint-scope, esrecurse, estraverse", "BSD-2-Clause", "web/package-lock.json"),
    MiuixOpenSourceNotice("events 3.3.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("fast-deep-equal, fast-uri", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("fastest-levenshtein 1.0.16", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("fdir 6.5.0, picomatch 4.0.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("find-up 4.1.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("flat 5.0.2", "BSD-3-Clause", "web/package-lock.json"),
    MiuixOpenSourceNotice("function-bind 1.1.2", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("glob-to-regexp 0.4.1", "BSD-2-Clause", "web/package-lock.json"),
    MiuixOpenSourceNotice("graceful-fs 4.2.11", "ISC", "web/package-lock.json"),
    MiuixOpenSourceNotice("has-flag 4.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("hasown 2.0.2", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("icss-utils 5.1.0", "ISC", "web/package-lock.json"),
    MiuixOpenSourceNotice("immutable 5.1.4", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("import-local 3.2.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("interpret 3.1.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("is-core-module 1.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("is-plain-object 2.0.4", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("isexe 2.0.0", "ISC", "web/package-lock.json"),
    MiuixOpenSourceNotice("isobject 3.0.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("jest-worker 27.5.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("json-parse-even-better-errors 2.3.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("json-schema-traverse 1.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("kind-of 6.0.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("loader-runner 4.3.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("locate-path 5.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("merge-stream 2.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("mime-db 1.52.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("mime-types 2.1.35", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("mini-css-extract-plugin 2.9.4", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("nanoid 3.3.11", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("neo-async 2.6.2", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("node-addon-api, node-releases", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("p-limit 2.3.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("p-locate 4.1.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("p-try 2.2.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("path-exists 4.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("path-key 3.1.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("path-parse 1.0.7", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("picocolors 1.1.1", "ISC", "web/package-lock.json"),
    MiuixOpenSourceNotice("pkg-dir 4.2.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("postcss 8.5.6", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("postcss-modules-* 5.x/6.x", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("randombytes 2.1.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("readdirp 4.1.2", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("rechoir 0.8.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("require-from-string 2.0.2", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("resolve 1.22.10, resolve-cwd 3.0.0, resolve-from 5.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("safe-buffer 5.2.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("sass 1.97.0, sass-loader 16.0.6", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("schema-utils 4.3.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("semver 7.7.3", "ISC", "web/package-lock.json"),
    MiuixOpenSourceNotice("serialize-javascript 6.0.2", "BSD-3-Clause", "web/package-lock.json"),
    MiuixOpenSourceNotice("shallow-clone 3.0.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("shebang-command, shebang-regex", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("source-map 0.6.1, source-map-js 1.2.1", "BSD-3-Clause", "web/package-lock.json"),
    MiuixOpenSourceNotice("source-map-support 0.5.21", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("string-hash 1.1.3", "CC0-1.0", "web/package-lock.json"),
    MiuixOpenSourceNotice("supports-color 8.1.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("supports-preserve-symlinks-flag 1.0.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("tapable 2.3.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("terser 5.44.0, terser-webpack-plugin 5.3.14", "BSD-2-Clause / MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("tinyglobby 0.2.15", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("to-regex-range 5.0.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("undici-types 7.16.0", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("update-browserslist-db 1.2.1", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("watchpack 2.4.4", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("webpack 5.107.0, webpack-cli 6.0.2, webpack-sources 3.3.3", "MIT", "web/package-lock.json"),
    MiuixOpenSourceNotice("which 2.0.2", "ISC", "web/package-lock.json"),
    MiuixOpenSourceNotice("wildcard 2.0.1", "MIT", "web/package-lock.json")
)
