package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.local.StoreMemberEntity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.i18n.AppLanguage
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LocalStoreStrings
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ThemePresets
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.StoreUiState
import com.example.ui.viewmodel.StoreViewModel
import com.example.ui.viewmodel.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminSystemScreen(
    state: StoreUiState,
    viewModel: StoreViewModel
) {
    val strings = LocalStoreStrings.current
    val isRtl = state.language == AppLanguage.AR
    val role = state.effectiveRole

    if (!role.canManageUsers() && !role.canAccessSystemAdmin()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(strings.accessDeniedMsg, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add(strings.usersAndRoles)
        add(strings.auditLogTitle)
        if (role.canAccessSystemAdmin()) {
            add(strings.storeCustomization)
            add(strings.updatesTitle)
        }
    }
    if (selectedTab >= tabs.size) selectedTab = 0

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title, maxLines = 1) })
            }
        }

        when (selectedTab) {
            0 -> UsersAndRolesTab(state, viewModel)
            1 -> AuditLogTab(state)
            2 -> if (role.canAccessSystemAdmin()) StoreCustomizationTab(state, viewModel, isRtl)
            3 -> if (role.canAccessSystemAdmin()) AppUpdatesAdminTab(state, viewModel)
        }
    }
}

@Composable
private fun UsersAndRolesTab(state: StoreUiState, viewModel: StoreViewModel) {
    val strings = LocalStoreStrings.current
    var editingUid by remember { mutableStateOf("") }
    var isEditingExisting by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var roleInput by remember { mutableStateOf(UserRole.SELLER) }
    var activeInput by remember { mutableStateOf(true) }
    var memberToDelete by remember { mutableStateOf<StoreMemberEntity?>(null) }

    LaunchedEffect(state.effectiveRole) {
        if (state.effectiveRole != UserRole.ADMIN && roleInput == UserRole.ADMIN) {
            roleInput = UserRole.SELLER
        }
        if (isEditingExisting && state.effectiveRole != UserRole.ADMIN) {
            val target = state.members.find { it.uid == editingUid }
            if (target != null && UserRole.fromKey(target.role) == UserRole.ADMIN) {
                isEditingExisting = false
                editingUid = ""
                usernameInput = ""
                nameInput = ""
                passwordInput = ""
                roleInput = UserRole.SELLER
                activeInput = true
            }
        }
    }

    val activeAdminCount = remember(state.members) {
        state.members.count { UserRole.fromKey(it.role) == UserRole.ADMIN && it.active }
    }
    val totalAdminCount = remember(state.members) {
        state.members.count { UserRole.fromKey(it.role) == UserRole.ADMIN }
    }
    val isEditingLastActiveAdmin = remember(isEditingExisting, editingUid, state.members, activeAdminCount) {
        if (!isEditingExisting) false
        else {
            val target = state.members.find { it.uid == editingUid }
            target != null && UserRole.fromKey(target.role) == UserRole.ADMIN && target.active && activeAdminCount <= 1
        }
    }

    val availableRoles = remember(state.effectiveRole, strings) {
        buildList {
            if (state.effectiveRole == UserRole.ADMIN) {
                add(UserRole.ADMIN to strings.roleAdmin)
            }
            add(UserRole.MANAGER to strings.roleManager)
            add(UserRole.CASHIER to strings.roleCashier)
            add(UserRole.SELLER to strings.roleSeller)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.actualRole == UserRole.ADMIN) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.previewRoleLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(
                                listOf(
                                    null to strings.roleAdmin,
                                    UserRole.MANAGER to strings.roleManager,
                                    UserRole.CASHIER to strings.roleCashier,
                                    UserRole.SELLER to strings.roleSeller
                                )
                            ) { (r, label) ->
                                FilterChip(
                                    selected = state.previewRole == r,
                                    onClick = { viewModel.setPreviewRole(r) },
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isEditingExisting) "${strings.editMember}: $usernameInput" else strings.addMember,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isEditingExisting) {
                            TextButton(onClick = {
                                isEditingExisting = false
                                editingUid = ""
                                usernameInput = ""
                                nameInput = ""
                                passwordInput = ""
                                roleInput = UserRole.SELLER
                                activeInput = true
                            }) {
                                Text(strings.cancelLabel)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(strings.usernameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("member_username_input")
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(strings.memberNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("member_displayname_input")
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(strings.passwordLabel) },
                        placeholder = if (isEditingExisting) {
                            { Text(strings.newPasswordHint) }
                        } else null,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = strings.passwordLabel
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("member_password_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableRoles) { (r, label) ->
                            FilterChip(
                                selected = roleInput == r,
                                enabled = !(isEditingLastActiveAdmin && r != UserRole.ADMIN),
                                onClick = { roleInput = r },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("member_role_${r.key}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.activeUserLabel, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = activeInput,
                            enabled = !isEditingLastActiveAdmin,
                            onCheckedChange = { activeInput = it }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.addOrUpdateMember(
                                uid = editingUid,
                                username = usernameInput,
                                displayName = nameInput,
                                password = passwordInput,
                                role = roleInput,
                                active = activeInput
                            )
                            isEditingExisting = false
                            editingUid = ""
                            usernameInput = ""
                            nameInput = ""
                            passwordInput = ""
                            roleInput = UserRole.SELLER
                            activeInput = true
                        },
                        enabled = usernameInput.isNotBlank() &&
                            nameInput.isNotBlank() &&
                            (isEditingExisting || passwordInput.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().testTag("save_member_button")
                    ) { Text(strings.saveLabel) }
                }
            }
        }

        items(state.members, key = { it.uid }) { m ->
            val memberRole = UserRole.fromKey(m.role)
            val canManageThisMember = state.effectiveRole == UserRole.ADMIN || memberRole != UserRole.ADMIN
            val isLastProtectedAdmin = memberRole == UserRole.ADMIN && ((m.active && activeAdminCount <= 1) || totalAdminCount <= 1)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "@${m.username.ifBlank { m.uid }} • ${if (m.active) "✓" else "✕"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusBadge(
                            label = m.role.uppercase(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (canManageThisMember) {
                            IconButton(
                                onClick = {
                                    isEditingExisting = true
                                    editingUid = m.uid
                                    usernameInput = m.username.ifBlank { m.displayName }
                                    nameInput = m.displayName
                                    passwordInput = ""
                                    roleInput = memberRole
                                    activeInput = m.active
                                },
                                modifier = Modifier.testTag("edit_member_${m.uid}")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = strings.editMember)
                            }
                            if (!isLastProtectedAdmin) {
                                IconButton(
                                    onClick = { memberToDelete = m },
                                    modifier = Modifier.testTag("delete_member_${m.uid}")
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = strings.deleteMember,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingDelete = memberToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text(strings.confirmDeleteTitle) },
            text = { Text("${strings.confirmDeleteMsg}\n\n${pendingDelete.displayName} (@${pendingDelete.username.ifBlank { pendingDelete.uid }})") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMember(pendingDelete.uid)
                        if (isEditingExisting && editingUid == pendingDelete.uid) {
                            isEditingExisting = false
                            editingUid = ""
                            usernameInput = ""
                            nameInput = ""
                            passwordInput = ""
                            roleInput = UserRole.SELLER
                            activeInput = true
                        }
                        memberToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_member_button")
                ) { Text(strings.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) { Text(strings.cancelLabel) }
            }
        )
    }
}

@Composable
private fun AuditLogTab(state: StoreUiState) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.auditLogs.take(100), key = { it.id }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.action, fontWeight = FontWeight.Bold, fontFamily = MonoFontFamily)
                        Text(fmt.format(Date(log.createdAtMillis)), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${log.actorName} (${log.actorRole}) • ${log.targetType}/${log.targetId}", style = MaterialTheme.typography.labelMedium)
                    Text(log.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StoreCustomizationTab(
    state: StoreUiState,
    viewModel: StoreViewModel,
    isRtl: Boolean
) {
    val strings = LocalStoreStrings.current
    val s = state.settings
    var nameAr by remember(s.storeNameAr) { mutableStateOf(s.storeNameAr) }
    var nameEn by remember(s.storeNameEn) { mutableStateOf(s.storeNameEn) }
    var primaryHex by remember(s.primaryColorHex) { mutableStateOf(s.primaryColorHex) }
    var secondaryHex by remember(s.secondaryColorHex) { mutableStateOf(s.secondaryColorHex) }
    var logoUri by remember(s.logoUri) { mutableStateOf(s.logoUri) }
    var timeoutStr by remember(s.sessionTimeoutMinutes) { mutableStateOf(s.sessionTimeoutMinutes.toString()) }
    var usdRateStr by remember(s.usdRateSsp, state.usdRateSsp) {
        val current = s.usdRateSsp.takeIf { it > 0.0 } ?: state.usdRateSsp
        mutableStateOf(
            if (current == current.toLong().toDouble()) current.toLong().toString()
            else String.format(Locale.US, "%.2f", current)
        )
    }
    var nLowStock by remember(s.notifyLowStock) { mutableStateOf(s.notifyLowStock) }
    var nDayNotClosed by remember(s.notifyDayNotClosed) { mutableStateOf(s.notifyDayNotClosed) }
    var nNewUser by remember(s.notifyNewUser) { mutableStateOf(s.notifyNewUser) }
    var nSyncErr by remember(s.notifySyncErrors) { mutableStateOf(s.notifySyncErrors) }
    var nNewVer by remember(s.notifyNewVersion) { mutableStateOf(s.notifyNewVersion) }
    var rolesSet by remember(s.notifyRolesCsv) {
        mutableStateOf(s.notifyRolesCsv.split(",").filter { it.isNotBlank() }.toSet())
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) logoUri = uri.toString() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(value = nameAr, onValueChange = { nameAr = it }, label = { Text(strings.storeNameArLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text(strings.storeNameEnLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedButton(
                onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(strings.pickImage) }
        }
        item {
            Text(strings.primaryColorLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemePresets.forEach { preset ->
                    val isSelected = primaryHex.equals(preset.primaryHex, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                primaryHex = preset.primaryHex
                                secondaryHex = preset.secondaryHex
                            }
                            .padding(4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(preset.primaryHex, MaterialTheme.colorScheme.primary))
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(preset.secondaryHex, MaterialTheme.colorScheme.secondary))
                            )
                        }
                        Text(
                            if (isRtl) preset.labelAr else preset.labelEn,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = timeoutStr,
                onValueChange = { timeoutStr = it },
                label = { Text(strings.sessionTimeoutLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = usdRateStr,
                onValueChange = { usdRateStr = it },
                label = { Text(strings.usdExchangeRateLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("settings_usd_rate_input")
            )
        }
        item {
            Text(strings.notificationSettings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ToggleRow(strings.notifyLowStock, nLowStock) { nLowStock = it }
            ToggleRow(strings.notifyDayNotClosed, nDayNotClosed) { nDayNotClosed = it }
            ToggleRow(strings.notifyNewUser, nNewUser) { nNewUser = it }
            ToggleRow(strings.notifySyncErrors, nSyncErr) { nSyncErr = it }
            ToggleRow(strings.notifyNewVersion, nNewVer) { nNewVer = it }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("admin" to strings.roleAdmin, "manager" to strings.roleManager, "cashier" to strings.roleCashier, "seller" to strings.roleSeller).forEach { (key, lbl) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in rolesSet,
                            onCheckedChange = { checked ->
                                rolesSet = if (checked) rolesSet + key else rolesSet - key
                            }
                        )
                        Text(lbl, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.saveSettings(
                        s.copy(
                            storeNameAr = nameAr.ifBlank { s.storeNameAr },
                            storeNameEn = nameEn.ifBlank { s.storeNameEn },
                            primaryColorHex = primaryHex,
                            secondaryColorHex = secondaryHex,
                            logoUri = logoUri,
                            sessionTimeoutMinutes = (timeoutStr.toIntOrNull() ?: 30).coerceIn(5, 1440),
                            notifyLowStock = nLowStock,
                            notifyDayNotClosed = nDayNotClosed,
                            notifyNewUser = nNewUser,
                            notifySyncErrors = nSyncErr,
                            notifyNewVersion = nNewVer,
                            notifyRolesCsv = rolesSet.joinToString(","),
                            usdRateSsp = usdRateStr.toDoubleOrNull()?.takeIf { it > 0.0 } ?: s.usdRateSsp
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_store_settings_btn")
            ) {
                Text(strings.saveLabel)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AppUpdatesAdminTab(state: StoreUiState, viewModel: StoreViewModel) {
    val strings = LocalStoreStrings.current
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    var pcUrlInput by remember(state.pcServerUrl) { mutableStateOf(state.pcServerUrl) }
    var versionCodeStr by remember { mutableStateOf((BuildConfig.VERSION_CODE + 1).toString()) }
    var versionName by remember { mutableStateOf("1.1.0") }
    var downloadUrl by remember { mutableStateOf("") }
    var notesAr by remember { mutableStateOf("") }
    var notesEn by remember { mutableStateOf("") }
    var mandatory by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        strings.pcSyncTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = pcUrlInput,
                        onValueChange = { pcUrlInput = it },
                        label = { Text(strings.pcServerAddressLabel) },
                        placeholder = { Text(strings.pcServerHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pc_server_url_input")
                    )
                    if (!state.pcConnectionStatus.isNullOrBlank()) {
                        Text(
                            state.pcConnectionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.lastPcSyncMillis > 0L) {
                        Text(
                            fmt.format(Date(state.lastPcSyncMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testPcConnection(pcUrlInput) },
                            enabled = pcUrlInput.isNotBlank() && !state.isPcSyncing,
                            modifier = Modifier.weight(1f).testTag("test_pc_connection_btn")
                        ) {
                            Text(strings.testPcConnection, maxLines = 1)
                        }
                        Button(
                            onClick = { viewModel.syncWithPc(pcUrlInput) },
                            enabled = pcUrlInput.isNotBlank() && !state.isPcSyncing,
                            modifier = Modifier.weight(1f).testTag("sync_with_pc_btn")
                        ) {
                            Text(strings.syncWithPcNow, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text(
                "${strings.updatesTitle} (Current: v${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            OutlinedTextField(
                value = versionCodeStr,
                onValueChange = { versionCodeStr = it },
                label = { Text(strings.versionCodeLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(value = versionName, onValueChange = { versionName = it }, label = { Text(strings.versionNameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = downloadUrl, onValueChange = { downloadUrl = it }, label = { Text(strings.downloadUrlLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = notesAr, onValueChange = { notesAr = it }, label = { Text(strings.releaseNotesArLabel) }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = notesEn, onValueChange = { notesEn = it }, label = { Text(strings.releaseNotesEnLabel) }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = mandatory, onCheckedChange = { mandatory = it })
                Text(strings.mandatoryUpdateLabel)
            }
        }
        item {
            Button(
                onClick = {
                    val code = versionCodeStr.toIntOrNull() ?: (BuildConfig.VERSION_CODE + 1)
                    viewModel.publishAppRelease(code, versionName, downloadUrl, notesAr, notesEn, mandatory)
                },
                enabled = downloadUrl.isNotBlank() && versionName.isNotBlank() && state.isOnline,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("publish_update_btn")
            ) {
                Text(strings.publishUpdate)
            }
        }
        items(state.releases, key = { it.id }) { rel ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${rel.platform.uppercase()} v${rel.versionName} (${rel.versionCode})", fontWeight = FontWeight.Bold)
                    Text(rel.downloadUrl, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
