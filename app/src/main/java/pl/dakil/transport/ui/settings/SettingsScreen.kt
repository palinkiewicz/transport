package pl.dakil.transport.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.R

/**
 * The settings index: one row per [SettingsSection], each opening a screen of its own, plus the
 * app-info dialog and the app-wide reset.
 *
 * The sections themselves live in [SettingsSectionScreen] — this screen deliberately holds no
 * controls, so the list stays short enough to read at a glance however much tuning is added below
 * it. Ordered by how many people touch them: everyday choices first, the deep interpolation tuning
 * last.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAppInfo by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Bottom inset intentionally excluded: the app-level bottom navigation bar shown for
        // this route already clears the navigation bar inset.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                actions = {
                    TextButton(
                        enabled = !settings.isDefault,
                        onClick = viewModel::resetAll,
                    ) { Text(stringResource(R.string.action_reset_all)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection.entries.forEach { section ->
                SettingsRow(
                    title = stringResource(section.titleRes),
                    summary = stringResource(section.summaryRes),
                    icon = section.icon,
                    onClick = { onOpenSection(section) },
                    opensScreen = true,
                )
            }
            // A dialog rather than a screen of its own: five lines and four links are not a
            // destination, and it is the one row here that changes nothing — hence no chevron.
            SettingsRow(
                title = stringResource(R.string.settings_app_info),
                summary = stringResource(R.string.settings_app_info_summary),
                icon = Icons.Outlined.Info,
                onClick = { showAppInfo = true },
                opensScreen = false,
            )
        }
    }

    if (showAppInfo) {
        AppInfoDialog(onDismiss = { showAppInfo = false })
    }
}

/**
 * A plain [ListItem] row, transparent so it sits on the screen's own background. [opensScreen] adds
 * the trailing chevron: it marks the rows that navigate, which is the one thing the app-info row
 * does not do.
 */
@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    opensScreen: Boolean,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (opensScreen) {
            {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
    )
}
