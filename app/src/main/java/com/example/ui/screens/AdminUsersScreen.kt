package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.NflRepository
import com.example.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AdminNavyBg = Color(0xFF0A0F1D)
private val AdminCardBg = Color(0xFF111827)
private val AdminSecondaryBg = Color(0xFF1E293B)
private val AdminBorderCol = Color(0xFF334155)
private val AdminTextPrimary = Color(0xFFF8FAFC)
private val AdminTextSecondary = Color(0xFF94A3B8)
private val AdminCyan = Color(0xFF06B6D4)
private val AdminGreen = Color(0xFF10B981)
private val AdminAmber = Color(0xFFF59E0B)
private val AdminRed = Color(0xFFEF4444)

private enum class AdminSubTab(val label: String) {
    USERS("Users & Approvals"),
    DEVICE_REQUESTS("Device Requests"),
    ROLE_MATRIX("Role & Designation Matrix"),
    SECURITY_LOGS("Security Audit Log")
}

private enum class UserStatusFilter(val label: String) {
    ALL("All Users"),
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    SUSPENDED("Suspended")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    repository: NflRepository,
    onNavigateBack: () -> Unit = {}
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val users by repository.users.collectAsStateWithLifecycle()
    val currentUser by repository.currentUser.collectAsStateWithLifecycle()
    val deviceRequests by repository.deviceApprovalRequests.collectAsStateWithLifecycle()
    val auditLogs by repository.auditLogs.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(AdminSubTab.USERS) }
    var statusFilter by remember { mutableStateOf(UserStatusFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    var editingUser by remember { mutableStateOf<UserAccount?>(null) }
    var showCreateOrEditDialog by remember { mutableStateOf(false) }
    var rejectingUser by remember { mutableStateOf<UserAccount?>(null) }
    var rejectionReasonInput by remember { mutableStateOf("") }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var showClearDemoDialog by remember { mutableStateOf(false) }
    var showFactoryResetDialog by remember { mutableStateOf(false) }

    val pendingUsersCount = remember(users) {
        users.count { it.accountStatus == AccountStatus.PENDING_APPROVAL }
    }
    val approvedUsersCount = remember(users) {
        users.count { it.accountStatus == AccountStatus.APPROVED && it.isActive }
    }
    val rejectedOrSuspendedCount = remember(users) {
        users.count {
            it.accountStatus == AccountStatus.REJECTED ||
                it.accountStatus == AccountStatus.SUSPENDED ||
                !it.isActive
        }
    }
    val pendingDeviceRequests = remember(deviceRequests) {
        deviceRequests.filter { it.status.equals("PENDING", ignoreCase = true) }
    }

    val filteredUsers = remember(users, statusFilter, searchQuery) {
        users.filter { u ->
            val matchesStatus = when (statusFilter) {
                UserStatusFilter.ALL -> true
                UserStatusFilter.PENDING -> u.accountStatus == AccountStatus.PENDING_APPROVAL
                UserStatusFilter.APPROVED -> u.accountStatus == AccountStatus.APPROVED && u.isActive
                UserStatusFilter.REJECTED -> u.accountStatus == AccountStatus.REJECTED
                UserStatusFilter.SUSPENDED -> u.accountStatus == AccountStatus.SUSPENDED || !u.isActive
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                u.name.contains(q, ignoreCase = true) ||
                u.id.contains(q, ignoreCase = true) ||
                u.employeeId.contains(q, ignoreCase = true) ||
                u.designation.contains(q, ignoreCase = true) ||
                u.department.contains(q, ignoreCase = true) ||
                u.floor.displayName.contains(q, ignoreCase = true) ||
                u.authorizedFloorScope.displayName.contains(q, ignoreCase = true)
            matchesStatus && matchesQuery
        }.sortedWith(
            compareBy<UserAccount> {
                when (it.accountStatus) {
                    AccountStatus.PENDING_APPROVAL -> 0
                    AccountStatus.APPROVED -> 1
                    AccountStatus.SUSPENDED -> 2
                    AccountStatus.REJECTED -> 3
                }
            }.thenBy { it.name.lowercase(Locale.US) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminNavyBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AdminCardBg),
                    border = BorderStroke(1.dp, AdminBorderCol)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.testTag("admin_back_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = AdminTextPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(AdminCyan.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AdminPanelSettings,
                                        contentDescription = "Admin Panel",
                                        tint = AdminCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "ADMIN USER & ACCESS CONTROL",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AdminTextPrimary
                                    )
                                    Text(
                                        text = "Employee Approvals • Designation Mapping • Single-Device Security",
                                        fontSize = 11.sp,
                                        color = AdminTextSecondary
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    editingUser = null
                                    showCreateOrEditDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AdminCyan),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("admin_add_user_button")
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "Add User",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Add Employee",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Summary KPI Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AdminStatTile(
                                title = "Pending",
                                value = pendingUsersCount.toString(),
                                accent = AdminAmber,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTab = AdminSubTab.USERS
                                        statusFilter = UserStatusFilter.PENDING
                                    }
                            )
                            AdminStatTile(
                                title = "Approved",
                                value = approvedUsersCount.toString(),
                                accent = AdminGreen,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTab = AdminSubTab.USERS
                                        statusFilter = UserStatusFilter.APPROVED
                                    }
                            )
                            AdminStatTile(
                                title = "Suspended/Rej",
                                value = rejectedOrSuspendedCount.toString(),
                                accent = AdminRed,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTab = AdminSubTab.USERS
                                        statusFilter = UserStatusFilter.SUSPENDED
                                    }
                            )
                            AdminStatTile(
                                title = "Device Req",
                                value = pendingDeviceRequests.size.toString(),
                                accent = AdminCyan,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTab = AdminSubTab.DEVICE_REQUESTS
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    repository.applyManagementStrictPolicy()
                                    statusBanner =
                                        "Enforced view-only & designation policy across Management accounts."
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AdminCyan.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = "Apply Policy",
                                    tint = AdminCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Enforce Management Strict View-Only Policy",
                                    fontSize = 11.sp,
                                    color = AdminCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { showClearDemoDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AdminAmber.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("admin_clear_demo_button")
                            ) {
                                Icon(
                                    Icons.Default.CleaningServices,
                                    contentDescription = "Clear Demo Data",
                                    tint = AdminAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Clear Demo Data",
                                    fontSize = 11.sp,
                                    color = AdminAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { showFactoryResetDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AdminRed),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("admin_factory_reset_button")
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Factory Reset All Records",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Factory Reset All Records",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!statusBanner.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = AdminGreen.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AdminGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = statusBanner.orEmpty(),
                                        fontSize = 11.sp,
                                        color = AdminGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { statusBanner = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = AdminGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sub-Navigation Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminSubTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val badgeCount = when (tab) {
                            AdminSubTab.USERS -> pendingUsersCount
                            AdminSubTab.DEVICE_REQUESTS -> pendingDeviceRequests.size
                            else -> 0
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tab.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (badgeCount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.Black else AdminAmber)
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = badgeCount.toString(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isSelected) AdminAmber else Color.Black
                                            )
                                        }
                                    }
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AdminCyan,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }

            when (selectedTab) {
                AdminSubTab.USERS -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AdminCardBg),
                            border = BorderStroke(1.dp, AdminBorderCol)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search by Name, Employee ID, Designation, Floor") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Search Users",
                                            tint = AdminCyan
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Clear Search"
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_user_search_input")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UserStatusFilter.entries.forEach { filter ->
                                        val selected = statusFilter == filter
                                        FilterChip(
                                            selected = selected,
                                            onClick = { statusFilter = filter },
                                            label = {
                                                Text(
                                                    filter.label,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AdminCyan.copy(alpha = 0.22f),
                                                selectedLabelColor = AdminCyan
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredUsers.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = AdminCardBg),
                                border = BorderStroke(1.dp, AdminBorderCol)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.GroupOff,
                                        contentDescription = "No matching users",
                                        tint = AdminTextSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No employee accounts match the selected filter.",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AdminTextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredUsers, key = { it.id }) { user ->
                            AdminUserCard(
                                user = user,
                                currentAdminId = currentUser?.id.orEmpty(),
                                onEdit = {
                                    editingUser = user
                                    showCreateOrEditDialog = true
                                },
                                onApprove = {
                                    val res = repository.adminApproveUserRegistration(user.id)
                                    statusBanner = res.message
                                },
                                onReject = {
                                    rejectingUser = user
                                    rejectionReasonInput = ""
                                },
                                onSuspend = {
                                    val res = repository.adminSuspendUser(
                                        user.id,
                                        "Suspended by Administrator"
                                    )
                                    if (res.success) {
                                        statusBanner = res.message
                                    } else {
                                        Toast.makeText(
                                            context,
                                            res.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onReactivate = {
                                    val res = repository.adminReactivateUser(user.id)
                                    statusBanner = res.message
                                },
                                onRevokeDevice = {
                                    val res = repository.adminRevokeUserDevice(user.id)
                                    statusBanner = res.message
                                },
                                onDelete = {
                                    if (user.id.equals(INITIAL_ADMIN_EMPLOYEE_ID, ignoreCase = true)) {
                                        Toast.makeText(
                                            context,
                                            "Initial Admin account (1015198) cannot be deleted.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        repository.deleteUser(user.id)
                                        statusBanner =
                                            "Removed employee account ${user.name} (${user.id})."
                                    }
                                }
                            )
                        }
                    }
                }

                AdminSubTab.DEVICE_REQUESTS -> {
                    item {
                        AdminDeviceRequestsSection(
                            requests = deviceRequests,
                            onApproveRequest = { reqId ->
                                val res = repository.adminApproveDeviceOrAccountRequest(reqId)
                                statusBanner = res.message
                            },
                            onRejectRequest = { reqId ->
                                val res = repository.adminRejectDeviceOrAccountRequest(reqId)
                                statusBanner = res.message
                            }
                        )
                    }
                }

                AdminSubTab.ROLE_MATRIX -> {
                    item {
                        AdminDesignationMatrixCard()
                    }
                }

                AdminSubTab.SECURITY_LOGS -> {
                    item {
                        AdminSecurityLogsSection(logs = auditLogs)
                    }
                }
            }
        }
    }

    if (showCreateOrEditDialog) {
        CreateOrEditEmployeeDialog(
            existingUser = editingUser,
            onDismiss = {
                showCreateOrEditDialog = false
                editingUser = null
            },
            onSave = { updatedAccount, newPasswordPlain ->
                val isNew = editingUser == null
                val accountWithPass = if (newPasswordPlain.isNotBlank()) {
                    updatedAccount.copy(password = newPasswordPlain)
                } else {
                    updatedAccount
                }
                val res = repository.saveUser(accountWithPass, isNewAccount = isNew)
                statusBanner = res.message
                showCreateOrEditDialog = false
                editingUser = null
            }
        )
    }

    if (rejectingUser != null) {
        val target = rejectingUser!!
        AlertDialog(
            onDismissRequest = { rejectingUser = null },
            title = {
                Text(
                    "Reject Registration — ${target.name}",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Employee ID: ${target.id} • Designation: ${target.designation.ifBlank { target.role.displayName }}",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = rejectionReasonInput,
                        onValueChange = { rejectionReasonInput = it },
                        label = { Text("Reason for Rejection") },
                        placeholder = { Text("e.g., Incorrect employee ID or unauthorized floor") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = repository.adminRejectUserRegistration(
                            employeeId = target.id,
                            reason = rejectionReasonInput.ifBlank { "Rejected by Administrator" }
                        )
                        statusBanner = res.message
                        rejectingUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminRed)
                ) {
                    Text("Reject Account", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDemoDialog) {
        AlertDialog(
            onDismissRequest = { showClearDemoDialog = false },
            title = {
                Text(
                    "Clear Demo / Sample Data?",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "This removes all sample/demo records and disables auto-generated chart numbers. Real operational records are preserved. To wipe all operational records (including Cloud Firestore test records), use 'Factory Reset All Records'.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.removeAllDemoData()
                        statusBanner = "✓ All sample/demo data cleared and saved."
                        showClearDemoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminAmber)
                ) {
                    Text("Clear Demo Data", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDemoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFactoryResetDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetDialog = false },
            title = {
                Text(
                    "Factory Reset All Operational Records?",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = AdminRed
                )
            },
            text = {
                Text(
                    "This will permanently clear ALL Production, Quality, NPT Incidents, and Daily Line Setups from both this device AND Cloud Firestore so your factory starts clean at 0.\n\nEmployee Accounts, Roles, and Factory Line Master will be preserved.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFactoryResetDialog = false
                        statusBanner = "Purging local & Cloud Firestore operational records..."
                        repository.resetAllOperationalData(clearCloud = true) { deletedCount, _ ->
                            statusBanner = "✓ Factory Reset Complete! Local & $deletedCount Cloud Firestore records cleared."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminRed)
                ) {
                    Text("Confirm Factory Reset", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminStatTile(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = accent
            )
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AdminTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdminUserCard(
    user: UserAccount,
    currentAdminId: String,
    onEdit: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSuspend: () -> Unit,
    onReactivate: () -> Unit,
    onRevokeDevice: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (user.accountStatus) {
        AccountStatus.APPROVED -> if (user.isActive) AdminGreen else AdminRed
        AccountStatus.PENDING_APPROVAL -> AdminAmber
        AccountStatus.REJECTED -> AdminRed
        AccountStatus.SUSPENDED -> AdminRed
    }
    val statusLabel = when (user.accountStatus) {
        AccountStatus.APPROVED -> if (user.isActive) "APPROVED" else "SUSPENDED"
        AccountStatus.PENDING_APPROVAL -> "PENDING APPROVAL"
        AccountStatus.REJECTED -> "REJECTED"
        AccountStatus.SUSPENDED -> "SUSPENDED"
    }

    val boundDeviceLabel = remember(user.authorizedDeviceName, user.authorizedDeviceId, user.approvedDevices) {
        when {
            user.authorizedDeviceName.isNotBlank() ->
                "${user.authorizedDeviceName} (${user.authorizedDeviceId.takeLast(6)})"
            user.approvedDevices.isNotEmpty() -> {
                val dev = user.approvedDevices.first()
                "${dev.deviceName} (${dev.deviceId.takeLast(6)})"
            }
            else -> "No Device Bound Yet"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_user_card_${user.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdminCardBg),
        border = BorderStroke(1.dp, AdminBorderCol)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = AdminTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = statusColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Employee ID: ${user.id} • Role: ${user.role.displayName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminCyan
                    )
                    Text(
                        text = "Designation: ${user.designation.ifBlank { user.role.displayName }} • Dept: ${user.department.ifBlank { "Production" }}",
                        fontSize = 11.sp,
                        color = AdminTextSecondary
                    )
                    Text(
                        text = "Assigned Floor: ${user.floor.displayName} • Authorized Scope: ${user.authorizedFloorScope.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AdminTextPrimary
                    )
                    Text(
                        text = "Authorized Device (Max 1): $boundDeviceLabel",
                        fontSize = 11.sp,
                        color = AdminTextSecondary
                    )
                    if (user.rejectionReason.isNotBlank() && user.accountStatus != AccountStatus.APPROVED) {
                        Text(
                            text = "Note: ${user.rejectionReason}",
                            fontSize = 11.sp,
                            color = AdminRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_user_${user.id}")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Employee",
                        tint = AdminCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (user.accountStatus == AccountStatus.PENDING_APPROVAL) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("approve_user_${user.id}")
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Approve",
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Approve",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }

                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AdminRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("reject_user_${user.id}")
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Reject",
                            tint = AdminRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Reject",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminRed
                        )
                    }
                }

                if (user.accountStatus == AccountStatus.APPROVED && user.isActive &&
                    !user.id.equals(INITIAL_ADMIN_EMPLOYEE_ID, ignoreCase = true) &&
                    !user.id.equals(currentAdminId, ignoreCase = true)
                ) {
                    OutlinedButton(
                        onClick = onSuspend,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AdminAmber),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = "Suspend",
                            tint = AdminAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Suspend",
                            fontSize = 11.sp,
                            color = AdminAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (user.accountStatus == AccountStatus.SUSPENDED ||
                    user.accountStatus == AccountStatus.REJECTED ||
                    !user.isActive
                ) {
                    Button(
                        onClick = onReactivate,
                        colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "Reactivate",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Approve / Reactivate",
                            fontSize = 11.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (user.authorizedDeviceId.isNotBlank() || user.approvedDevices.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onRevokeDevice,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AdminCyan.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.PhonelinkErase,
                            contentDescription = "Reset Device",
                            tint = AdminCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Reset Bound Device",
                            fontSize = 11.sp,
                            color = AdminCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AdminBorderCol),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Permissions",
                        tint = AdminTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Role & Scope",
                        fontSize = 11.sp,
                        color = AdminTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!user.id.equals(INITIAL_ADMIN_EMPLOYEE_ID, ignoreCase = true) &&
                    !user.id.equals(currentAdminId, ignoreCase = true)
                ) {
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = AdminRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Delete", fontSize = 11.sp, color = AdminRed)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOrEditEmployeeDialog(
    existingUser: UserAccount?,
    onDismiss: () -> Unit,
    onSave: (UserAccount, String) -> Unit
) {
    val isEditing = existingUser != null
    var name by remember { mutableStateOf(existingUser?.name.orEmpty()) }
    var employeeId by remember { mutableStateOf(existingUser?.id.orEmpty()) }
    var newPassword by remember { mutableStateOf("") }

    var selectedDesignation by remember {
        mutableStateOf(
            if (existingUser != null) {
                EmployeeDesignation.fromDisplayName(existingUser.designation)
            } else {
                EmployeeDesignation.PRODUCTION_SUPERVISOR
            }
        )
    }
    var physicalFloor by remember {
        mutableStateOf(
            existingUser?.floor?.takeIf { it != FloorScope.ALL } ?: FloorScope.SEW_1
        )
    }
    var authorizedScope by remember {
        mutableStateOf(
            existingUser?.authorizedFloorScope
                ?: DesignationPolicy.resolveAuthorizedScope(selectedDesignation, physicalFloor)
        )
    }
    var accountStatus by remember {
        mutableStateOf(existingUser?.accountStatus ?: AccountStatus.APPROVED)
    }

    // Individual permission flags
    var canViewDashboard by remember { mutableStateOf(existingUser?.canViewDashboard ?: true) }
    var canViewTvDashboard by remember { mutableStateOf(existingUser?.canViewTvDashboard ?: true) }
    var canViewMonthlyEfficiency by remember { mutableStateOf(existingUser?.canViewMonthlyEfficiency ?: true) }
    var canViewMonthlyDhu by remember { mutableStateOf(existingUser?.canViewMonthlyDhu ?: true) }
    var canViewMonthlyOql by remember { mutableStateOf(existingUser?.canViewMonthlyOql ?: true) }
    var canViewLineDeepDive by remember { mutableStateOf(existingUser?.canViewLineDeepDive ?: true) }
    var canViewAuditLogs by remember { mutableStateOf(existingUser?.canViewAuditLogs ?: false) }
    var canExportReports by remember { mutableStateOf(existingUser?.canExportReports ?: false) }

    var canProductionWrite by remember { mutableStateOf(existingUser?.canProductionWrite ?: true) }
    var canFinishingWrite by remember { mutableStateOf(existingUser?.canFinishingWrite ?: false) }
    var canQualityWrite by remember { mutableStateOf(existingUser?.canQualityWrite ?: false) }
    var canOqlWrite by remember { mutableStateOf(existingUser?.canOqlWrite ?: false) }
    var canNptWrite by remember { mutableStateOf(existingUser?.canNptWrite ?: true) }
    var canMorningTargetWrite by remember { mutableStateOf(existingUser?.canMorningTargetWrite ?: true) }
    var canMonthlyTargetWrite by remember { mutableStateOf(existingUser?.canMonthlyTargetWrite ?: false) }
    var canTargetRevisionWrite by remember { mutableStateOf(existingUser?.canTargetRevisionWrite ?: false) }

    fun syncTemplatePermissions(desig: EmployeeDesignation, flr: FloorScope, scopeOverride: FloorScope? = null) {
        val sample = DesignationPolicy.applyDesignationTemplate(
            base = UserAccount(id = employeeId.ifBlank { "TEMP" }, name = name.ifBlank { "Employee" }),
            designation = desig,
            physicalFloor = flr,
            overrideScope = scopeOverride
        )
        physicalFloor = sample.floor
        authorizedScope = sample.authorizedFloorScope
        canViewDashboard = sample.canViewDashboard
        canViewTvDashboard = sample.canViewTvDashboard
        canViewMonthlyEfficiency = sample.canViewMonthlyEfficiency
        canViewMonthlyDhu = sample.canViewMonthlyDhu
        canViewMonthlyOql = sample.canViewMonthlyOql
        canViewLineDeepDive = sample.canViewLineDeepDive
        canViewAuditLogs = sample.canViewAuditLogs
        canExportReports = sample.canExportReports
        canProductionWrite = sample.canProductionWrite
        canFinishingWrite = sample.canFinishingWrite
        canQualityWrite = sample.canQualityWrite
        canOqlWrite = sample.canOqlWrite
        canNptWrite = sample.canNptWrite
        canMorningTargetWrite = sample.canMorningTargetWrite
        canMonthlyTargetWrite = sample.canMonthlyTargetWrite
        canTargetRevisionWrite = sample.canTargetRevisionWrite
    }

    var designationMenuExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = AdminCardBg,
            border = BorderStroke(1.dp, AdminBorderCol)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEditing) "EDIT EMPLOYEE & ACCESS SCOPE" else "ADD NEW EMPLOYEE ACCOUNT",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = AdminTextPrimary
                        )
                        Text(
                            text = "Designation automatically maps Department, Role, and Floor Scope",
                            fontSize = 11.sp,
                            color = AdminTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AdminTextPrimary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AdminBorderCol)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Employee Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = employeeId,
                        onValueChange = { employeeId = it.trim() },
                        label = { Text("Employee ID") },
                        enabled = !isEditing || !existingUser?.id.equals(INITIAL_ADMIN_EMPLOYEE_ID, ignoreCase = true),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = {
                            Text(
                                if (isEditing) "Reset Password (leave blank to keep existing)"
                                else "Initial Password (min 6 chars)"
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Designation Selector
                    ExposedDropdownMenuBox(
                        expanded = designationMenuExpanded,
                        onExpandedChange = { designationMenuExpanded = !designationMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = "${selectedDesignation.displayName} (${selectedDesignation.department})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Official Designation") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = designationMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = designationMenuExpanded,
                            onDismissRequest = { designationMenuExpanded = false }
                        ) {
                            EmployeeDesignation.adminOptions().forEach { desig ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                desig.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                "Dept: ${desig.department} • Scope: ${
                                                    desig.defaultAuthorizedScope?.displayName ?: "Assigned Floor"
                                                }",
                                                fontSize = 11.sp,
                                                color = AdminCyan
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedDesignation = desig
                                        syncTemplatePermissions(desig, physicalFloor)
                                        designationMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Physical Floor Selection (Sew-1, Sew-2, Sew-3, Finishing)
                    Text(
                        "Assigned Physical Floor",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminTextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DesignationPolicy.validPhysicalFloorsWithFinishing.forEach { fl ->
                            FilterChip(
                                selected = physicalFloor == fl,
                                onClick = {
                                    physicalFloor = fl
                                    authorizedScope =
                                        DesignationPolicy.resolveAuthorizedScope(selectedDesignation, fl)
                                },
                                label = {
                                    Text(fl.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            )
                        }
                    }

                    // Authorized Floor Scope Override (Admin only)
                    Text(
                        "Authorized Floor Access Scope (Admin Override)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminTextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FloorScope.entries.forEach { scope ->
                            FilterChip(
                                selected = authorizedScope == scope,
                                onClick = { authorizedScope = scope },
                                label = {
                                    Text(
                                        scope.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }

                    // Account Status Selector
                    Text(
                        "Account Approval Status",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminTextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccountStatus.entries.forEach { st ->
                            FilterChip(
                                selected = accountStatus == st,
                                onClick = { accountStatus = st },
                                label = {
                                    Text(
                                        st.name.replace("_", " "),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }

                    // Granular Permissions Checkboxes
                    Text(
                        "Data Entry & Operational Permissions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminTextPrimary
                    )
                    PermissionToggleRow("Production Data Entry", canProductionWrite) { canProductionWrite = it }
                    PermissionToggleRow("Finishing Output Entry", canFinishingWrite) { canFinishingWrite = it }
                    PermissionToggleRow("Quality & Defect Entry", canQualityWrite) { canQualityWrite = it }
                    PermissionToggleRow("BS OQL Entry", canOqlWrite) { canOqlWrite = it }
                    PermissionToggleRow("NPT & Maintenance Logging", canNptWrite) { canNptWrite = it }
                    PermissionToggleRow("Morning Target & SMV Setup", canMorningTargetWrite) { canMorningTargetWrite = it }
                    PermissionToggleRow("Monthly Target Setup", canMonthlyTargetWrite) { canMonthlyTargetWrite = it }
                    PermissionToggleRow("Target Revision Override", canTargetRevisionWrite) { canTargetRevisionWrite = it }

                    Text(
                        "Dashboard & Reporting Permissions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminTextPrimary
                    )
                    PermissionToggleRow("Live Floor Dashboard", canViewDashboard) { canViewDashboard = it }
                    PermissionToggleRow("TV Command Center View", canViewTvDashboard) { canViewTvDashboard = it }
                    PermissionToggleRow("Line Deep-Dive Analytics", canViewLineDeepDive) { canViewLineDeepDive = it }
                    PermissionToggleRow("Monthly Efficiency / DHU / OQL", canViewMonthlyEfficiency) {
                        canViewMonthlyEfficiency = it
                        canViewMonthlyDhu = it
                        canViewMonthlyOql = it
                    }
                    PermissionToggleRow("Security Audit Logs", canViewAuditLogs) { canViewAuditLogs = it }
                    PermissionToggleRow("Export CSV / Reports", canExportReports) { canExportReports = it }

                    if (!errorMsg.isNullOrBlank()) {
                        Text(
                            text = errorMsg.orEmpty(),
                            fontSize = 12.sp,
                            color = AdminRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AdminBorderCol)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = AdminTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || employeeId.isBlank()) {
                                errorMsg = "Employee Name and Employee ID are required."
                                return@Button
                            }
                            if (!isEditing && newPassword.length < 4) {
                                errorMsg = "Initial password must be at least 4 characters."
                                return@Button
                            }
                            val isInitialAdmin = employeeId.equals(INITIAL_ADMIN_EMPLOYEE_ID, ignoreCase = true)
                            val resolvedRole = if (isInitialAdmin) UserRole.ADMIN else selectedDesignation.defaultRole
                            val resolvedScope = if (isInitialAdmin) FloorScope.ALL else authorizedScope
                            val resolvedAdminFlag = isInitialAdmin || resolvedRole == UserRole.ADMIN

                            val base = existingUser ?: UserAccount(
                                id = employeeId.trim(),
                                employeeId = employeeId.trim(),
                                name = name.trim()
                            )
                            val updated = base.copy(
                                id = employeeId.trim(),
                                employeeId = employeeId.trim(),
                                name = name.trim(),
                                designation = selectedDesignation.displayName,
                                department = selectedDesignation.department,
                                role = resolvedRole,
                                floor = physicalFloor,
                                authorizedFloorScope = resolvedScope,
                                isAdmin = resolvedAdminFlag,
                                accountStatus = accountStatus,
                                isActive = (accountStatus == AccountStatus.APPROVED),
                                canViewDashboard = canViewDashboard,
                                canViewTvDashboard = canViewTvDashboard,
                                canViewMonthlyEfficiency = canViewMonthlyEfficiency,
                                canViewMonthlyDhu = canViewMonthlyDhu,
                                canViewMonthlyOql = canViewMonthlyOql,
                                canViewLineDeepDive = canViewLineDeepDive,
                                canViewAuditLogs = canViewAuditLogs,
                                canExportReports = canExportReports,
                                canProductionWrite = canProductionWrite,
                                canFinishingWrite = canFinishingWrite,
                                canQualityWrite = canQualityWrite,
                                canOqlWrite = canOqlWrite,
                                canNptWrite = canNptWrite,
                                canMorningTargetWrite = canMorningTargetWrite,
                                canMonthlyTargetWrite = canMonthlyTargetWrite,
                                canTargetRevisionWrite = canTargetRevisionWrite,
                                updatedAtMillis = System.currentTimeMillis()
                            )
                            onSave(updated, newPassword)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AdminCyan)
                    ) {
                        Text(
                            "Save Employee",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AdminTextPrimary
        )
    }
}

@Composable
private fun AdminDeviceRequestsSection(
    requests: List<DeviceApprovalRequest>,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdminCardBg),
        border = BorderStroke(1.dp, AdminBorderCol)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "DEVICE REPLACEMENT & SECURITY REQUESTS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = AdminTextPrimary
            )
            Text(
                text = "Each Employee ID is restricted to 1 authorized phone/device. Approving a replacement binds the new device and revokes the old device immediately.",
                fontSize = 11.sp,
                color = AdminTextSecondary
            )

            if (requests.isEmpty()) {
                Text(
                    text = "No device replacement requests found.",
                    fontSize = 12.sp,
                    color = AdminTextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                requests.forEach { req ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AdminSecondaryBg,
                        border = BorderStroke(1.dp, AdminBorderCol),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${req.userName} (ID: ${req.userId})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdminTextPrimary
                                )
                                Text(
                                    text = req.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when (req.status.uppercase(Locale.US)) {
                                        "PENDING" -> AdminAmber
                                        "APPROVED" -> AdminGreen
                                        else -> AdminRed
                                    }
                                )
                            }
                            Text(
                                text = "New Device: ${req.deviceName} (${req.deviceId.takeLast(8)})",
                                fontSize = 11.sp,
                                color = AdminCyan
                            )
                            Text(
                                text = "Type: ${req.requestType} • ${sdf.format(Date(req.requestedAtMillis))}",
                                fontSize = 11.sp,
                                color = AdminTextSecondary
                            )
                            if (req.status.equals("PENDING", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onApproveRequest(req.requestId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AdminGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "Approve Device Replacement",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onRejectRequest(req.requestId) },
                                        border = BorderStroke(1.dp, AdminRed),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "Reject",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AdminRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminDesignationMatrixCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdminCardBg),
        border = BorderStroke(1.dp, AdminBorderCol)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "OFFICIAL DESIGNATION & FLOOR SCOPE MATRIX",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = AdminTextPrimary
            )
            Text(
                text = "Automatic Department, Role, and Floor Scope mapping applied upon registration and Admin approval:",
                fontSize = 11.sp,
                color = AdminTextSecondary
            )

            EmployeeDesignation.entries.forEach { desig ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AdminSecondaryBg,
                    border = BorderStroke(1.dp, AdminBorderCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = desig.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AdminTextPrimary
                            )
                            Text(
                                text = "Department: ${desig.department} • Role: ${desig.defaultRole.displayName}",
                                fontSize = 11.sp,
                                color = AdminTextSecondary
                            )
                        }
                        Surface(
                            color = AdminCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = desig.defaultAuthorizedScope?.displayName ?: "Assigned Floor",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AdminCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSecurityLogsSection(
    logs: List<AuditLogEntry>
) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdminCardBg),
        border = BorderStroke(1.dp, AdminBorderCol)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SECURITY & ACCESS AUDIT LOG (${logs.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = AdminTextPrimary
            )

            if (logs.isEmpty()) {
                Text(
                    text = "No security audit events recorded yet.",
                    fontSize = 12.sp,
                    color = AdminTextSecondary
                )
            } else {
                logs.take(60).forEach { log ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AdminSecondaryBg,
                        border = BorderStroke(1.dp, AdminBorderCol),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${log.action} — ${log.changedBy} (${log.entityId})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdminCyan
                                )
                                Text(
                                    text = sdf.format(Date(log.timestamp)),
                                    fontSize = 10.sp,
                                    color = AdminTextSecondary
                                )
                            }
                            if (log.deviceId.isNotBlank()) {
                                Text(
                                    text = "Device ID: ${log.deviceId.takeLast(8)}",
                                    fontSize = 10.sp,
                                    color = AdminTextSecondary
                                )
                            }
                            Text(
                                text = log.reason.ifBlank { log.details },
                                fontSize = 11.sp,
                                color = AdminTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
