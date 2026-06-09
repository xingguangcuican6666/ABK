package com.abk.kernel.ui.screens.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixRootAuthScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues
) {
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(outerPadding)
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.nav_root_auth),
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Superuser count
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.status_root_authorized),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = if (state.rootGranted) "OK" else "N/A",
                    style = MiuixTheme.textStyles.body1,
                    color = if (state.rootGranted) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // SELinux toggle placeholder
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELinux Hide",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Switch(
                    checked = false,
                    onCheckedChange = { /* vm.toggleSelinuxHide() */ }
                )
            }
        }
    }
}
