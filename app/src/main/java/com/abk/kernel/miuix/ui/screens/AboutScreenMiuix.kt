package com.abk.kernel.miuix.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
fun AboutScreenMiuix(vm: MainViewModel) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    val repoUrl = remember {
        "https://github.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"
    }

    val contributors = remember {
        listOf(
            "Akuma-Noko", "DebugBoard", "DreamFerry", "elysias123", "Fede2782",
            "FixeQyt", "FunLay123", "gsf114", "guruji-byte", "huime180",
            "liqideqq", "LX200944", "Mazha0309", "MiRinChan", "prpjzz",
            "ReeViiS69", "ShirkNeko", "Starsun", "TheSillyOk", "TheWildJames",
            "Tools-cx-app", "ukriu", "wrnxr233", "Xiaomichael", "xingguangcuican6666",
            "yx1234587", "zzh20188"
        )
    }

    val scrollBehavior = MiuixScrollBehavior()
    val state by vm.uiState.collectAsState()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings_about_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        MiuixIconButton(onClick = { navigator.pop() }) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.settings_back)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 16.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.app_full_name),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.app_full_name),
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.settings_about_intro),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }

            item {
                SmallTitle(stringResource(R.string.settings_repository_info))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_source_repository),
                        summary = repoUrl,
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        onClick = { openUrl(repoUrl) }
                    )
                    ArrowPreference(
                        title = "Releases",
                        summary = "$repoUrl/releases",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        onClick = { openUrl("$repoUrl/releases") }
                    )
                    ArrowPreference(
                        title = "Actions",
                        summary = "$repoUrl/actions",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        onClick = { openUrl("$repoUrl/actions") }
                    )
                    ArrowPreference(
                        title = "Pages",
                        summary = "https://${BuildConfig.SOURCE_REPO_OWNER}.github.io/${BuildConfig.SOURCE_REPO_NAME}/",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        onClick = { openUrl("https://${BuildConfig.SOURCE_REPO_OWNER}.github.io/${BuildConfig.SOURCE_REPO_NAME}/") }
                    )
                    ArrowPreference(
                        title = "README",
                        summary = "$repoUrl/blob/main/README.md",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        onClick = { openUrl("$repoUrl/blob/main/README.md") }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_third_party_notices),
                        summary = "$repoUrl/blob/main/THIRD_PARTY_NOTICES.md",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        onClick = { openUrl("$repoUrl/blob/main/THIRD_PARTY_NOTICES.md") }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_open_source_licenses),
                        summary = stringResource(R.string.settings_open_source_licenses_desc),
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        onClick = { navigator.push(Route.OpenSourceLicenses) }
                    )
                }
            }

            item {
                SmallTitle(stringResource(R.string.settings_contributors))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_contributors_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp)
                    )
                    contributors.forEach { username ->
                        ArrowPreference(
                            title = "@$username",
                            startAction = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            },
                            onClick = { openUrl("https://github.com/$username") }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.size(80.dp))
            }
        }
    }
    }
}
