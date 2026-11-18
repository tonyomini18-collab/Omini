package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.i18n.AppLanguage
import com.example.ui.components.StatusBadge
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.LocalStoreStrings
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessTeal
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.StoreUiState
import com.example.ui.viewmodel.StoreViewModel
import com.example.ui.viewmodel.UserRole
import kotlinx.coroutines.launch

private data class NavDestination(
    val tab: MainTab,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val strings = LocalStoreStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val prefs = remember { com.example.data.sync.PreferencesManager(context.applicationContext) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val isRtl = state.language == AppLanguage.AR
    val role = state.effectiveRole

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    if (showNotificationsDialog) {
        BackHandler { showNotificationsDialog = false }
    } else if (state.activeTab != MainTab.POS && !state.sessionLocked) {
        BackHandler { viewModel.selectTab(MainTab.POS) }
    }

    val destinations = remember(role, strings) {
        buildList {
            add(NavDestination(MainTab.POS, strings.navPos, Icons.Filled.PointOfSale))
            add(NavDestination(MainTab.INVENTORY, strings.navInventory, Icons.Filled.Inventory2))
            add(NavDestination(MainTab.DAY_CONTROL, strings.navDayControl, Icons.Filled.CalendarMonth))
            add(NavDestination(MainTab.DASHBOARD, strings.navDashboard, Icons.Filled.Dashboard))
            if (role.canViewProfitAndReports()) {
                add(NavDestination(MainTab.REPORTS, strings.navReports, Icons.Filled.Assessment))
            }
            if (role.canManageUsers() || role.canAccessSystemAdmin()) {
                add(NavDestination(MainTab.ADMIN, strings.navAdmin, Icons.Filled.AdminPanelSettings))
            }
        }
    }

    val roleLabel = when (role) {
        UserRole.ADMIN -> strings.roleAdmin
        UserRole.MANAGER -> strings.roleManager
        UserRole.CASHIER -> strings.roleCashier
    }
    val storeTitle = if (isRtl) state.settings.storeNameAr else state.settings.storeNameEn

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 760.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.settings.logoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = state.settings.logoUri,
                                        contentDescription = storeTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Storefront,
                                        contentDescription = storeTitle,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = storeTitle.ifBlank { strings.appTitle },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${state.userName} • $roleLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        // Online/Offline + Pending Sync Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StatusBadge(
                                label = if (state.isOnline) strings.onlineLabel else strings.offlineLabel,
                                containerColor = if (state.isOnline) {
                                    SuccessTeal.copy(alpha = 0.14f)
                                } else {
                                    DangerCrimson.copy(alpha = 0.14f)
                                },
                                contentColor = if (state.isOnline) SuccessTeal else DangerCrimson,
                                dotColor = if (state.isOnline) SuccessTeal else DangerCrimson
                            )

                            if (state.pendingSyncCount > 0 || state.isSyncing) {
                                Surface(
                                    color = WarningAmber.copy(alpha = 0.16f),
                                    contentColor = WarningAmber,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(enabled = state.isOnline && !state.isSyncing) {
                                            viewModel.triggerManualSync()
                                        }
                                        .testTag("pending_sync_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (state.isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 2.dp,
                                                color = WarningAmber
                                            )
                                        } else {
                                            Icon(
                                                Icons.Filled.Sync,
                                                contentDescription = strings.syncNow,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = state.pendingSyncCount.toString(),
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily)
                                        )
                                    }
                                }
                            }
                        }

                        if (state.pcServerUrl.isNotBlank()) {
                            IconButton(
                                onClick = { viewModel.syncWithPc() },
                                enabled = !state.isPcSyncing,
                                modifier = Modifier.testTag("quick_pc_sync_button")
                            ) {
                                if (state.isPcSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Computer,
                                        contentDescription = strings.syncWithPcNow,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showNotificationsDialog = true },
                            modifier = Modifier.testTag("notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.notifications.isNotEmpty()) {
                                        Badge {
                                            Text(state.notifications.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = strings.notificationsTitle
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier.testTag("main_language_toggle")
                        ) {
                            Icon(
                                Icons.Filled.Language,
                                contentDescription = strings.languageToggle
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.testTag("main_theme_toggle")
                        ) {
                            Icon(
                                if (state.darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (state.darkMode) strings.lightModeLabel else strings.darkModeLabel
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch { prefs.clearLocalSession() }
                                performSignOut(credentialManager, scope)
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = strings.signOutLabel
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWide) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            NavigationBarItem(
                                selected = state.activeTab == dest.tab,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = {
                                    Text(
                                        dest.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(8.dp))
                        destinations.forEach { dest ->
                            NavigationRailItem(
                                selected = state.activeTab == dest.tab,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = {
                                    Text(
                                        dest.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (state.activeTab) {
                        MainTab.POS -> PosScreen(state = state, viewModel = viewModel)
                        MainTab.INVENTORY -> InventoryScreen(state = state, viewModel = viewModel)
                        MainTab.DAY_CONTROL -> DayControlScreen(state = state, viewModel = viewModel)
                        MainTab.DASHBOARD -> DashboardScreen(state = state, viewModel = viewModel)
                        MainTab.REPORTS -> ReportsScreen(state = state, viewModel = viewModel)
                        MainTab.ADMIN -> AdminSystemScreen(state = state, viewModel = viewModel)
                    }
                }
            }
        }

        // Session Lock Overlay
        if (state.sessionLocked) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = true, onClick = {}),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = strings.sessionLockedTitle,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        strings.sessionLockedTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.userName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.unlockSession() },
                        modifier = Modifier.width(220.dp).height(50.dp).testTag("unlock_session_button")
                    ) {
                        Text(strings.unlockSession)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch { prefs.clearLocalSession() }
                            performSignOut(credentialManager, scope)
                        },
                        modifier = Modifier.width(220.dp).height(48.dp).testTag("locked_sign_out_button")
                    ) {
                        Text(strings.signOutLabel)
                    }
                }
            }
        }
    }

    // Notifications Dialog
    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = { Text(strings.notificationsTitle) },
            text = {
                if (state.notifications.isEmpty()) {
                    Text(
                        strings.noNotifications,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.notifications, key = { it.id }) { notif ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        if (isRtl) notif.titleAr else notif.titleEn,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isRtl) notif.bodyAr else notif.bodyEn,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text(strings.closeLabel)
                }
            }
        )
    }

    // Non-interruptive App Update Dialog (never shown while POS cart has active items)
    val update = state.availableUpdate
    if (update != null && state.cartItems.isEmpty() && !state.sessionLocked) {
        AlertDialog(
            onDismissRequest = {
                if (!update.isMandatory) {
                    viewModel.dismissUpdateDialog(update.versionCode)
                }
            },
            icon = {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = strings.updateAvailableTitle,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("${strings.updateAvailableTitle} (${update.versionName})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isRtl) update.releaseNotesAr else update.releaseNotesEn,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (update.isMandatory) {
                        Text(
                            strings.mandatoryUpdateNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.testTag("update_download_button")
                ) {
                    Text(strings.downloadAndInstall)
                }
            },
            dismissButton = {
                if (!update.isMandatory) {
                    TextButton(
                        onClick = { viewModel.dismissUpdateDialog(update.versionCode) },
                        modifier = Modifier.testTag("update_later_button")
                    ) {
                        Text(strings.laterLabel)
                    }
                }
            }
        )
    }
}
