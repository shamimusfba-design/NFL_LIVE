package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.AppFunctionCategory
import com.example.model.FloorScope
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.ui.theme.NflRoyalBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val users by repository.users.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val deviceRequests by repository.deviceApprovalRequests.collectAsState()
  val pendingDeviceRequests = remember(deviceRequests) { deviceRequests.filter { it.status == "PENDING" } }
  var userActionBanner by remember { mutableStateOf<String?>(null) }
  var dialogErrorMessage by remember { mutableStateOf<String?>(null) }

  // Selected tab: 0 = Function Access Matrix (Role-based), 1 = User Accounts & Assignment
  var selectedTab by remember { mutableStateOf(0) }

  // State for Role Access Matrix
  var matrixRole by remember { mutableStateOf(UserRole.MANAGEMENT_VIEW) }
  val targetRoleUsers = remember(users, matrixRole) { users.filter { it.role == matrixRole && !it.isAdmin } }
  val sampleUserForRole = targetRoleUsers.firstOrNull() ?: users.firstOrNull { it.role == matrixRole }

  var roleCanViewDashboard by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canViewDashboard ?: true) }
  var roleCanViewTvDashboard by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canViewTvDashboard ?: true) }
  var roleCanViewMonthlyEff by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canViewMonthlyEfficiency ?: true) }
  var roleCanViewMonthlyDhu by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canViewMonthlyDhu ?: true) }
  var roleCanViewMonthlyOql by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canViewMonthlyOql ?: true) }
  var roleCanViewDeepDive by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canViewLineDeepDive ?: true) }
  var roleCanViewAuditLogs by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canViewAuditLogs ?: true) }
  var roleCanExportReports by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canExportReports ?: true) }

  var roleCanProductionWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canProductionWrite ?: false) }
  var roleCanFinishingWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canFinishingWrite ?: false) }
  var roleCanQualityWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canQualityWrite ?: false) }
  var roleCanOqlWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canOqlWrite ?: false) }
  var roleCanNptWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canNptWrite ?: false) }
  var roleCanMorningTargetWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canMorningTargetWrite ?: false) }
  var roleCanMonthlyTargetWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canMonthlyTargetWrite ?: false) }
  var roleCanTargetRevisionWrite by remember(matrixRole, sampleUserForRole) { mutableStateOf(sampleUserForRole?.canTargetRevisionWrite ?: false) }

  var roleSaveSuccessMessage by remember { mutableStateOf<String?>(null) }

  fun autoSaveRoleMatrix(
    viewDashboard: Boolean = roleCanViewDashboard,
    viewTv: Boolean = roleCanViewTvDashboard,
    viewMonthlyEff: Boolean = roleCanViewMonthlyEff,
    viewMonthlyDhu: Boolean = roleCanViewMonthlyDhu,
    viewMonthlyOql: Boolean = roleCanViewMonthlyOql,
    viewDeepDive: Boolean = roleCanViewDeepDive,
    viewAuditLogs: Boolean = roleCanViewAuditLogs,
    exportReports: Boolean = roleCanExportReports,
    prodWrite: Boolean = roleCanProductionWrite,
    finishingWrite: Boolean = roleCanFinishingWrite,
    qualityWrite: Boolean = roleCanQualityWrite,
    oqlWrite: Boolean = roleCanOqlWrite,
    nptWrite: Boolean = roleCanNptWrite,
    morningTargetWrite: Boolean = roleCanMorningTargetWrite,
    monthlyTargetWrite: Boolean = roleCanMonthlyTargetWrite,
    targetRevisionWrite: Boolean = roleCanTargetRevisionWrite
  ) {
    repository.applyRolePolicy(
      role = matrixRole,
      canViewDashboard = viewDashboard,
      canViewTvDashboard = viewTv,
      canViewMonthlyEfficiency = viewMonthlyEff,
      canViewMonthlyDhu = viewMonthlyDhu,
      canViewMonthlyOql = viewMonthlyOql,
      canViewLineDeepDive = viewDeepDive,
      canViewAuditLogs = viewAuditLogs,
      canExportReports = exportReports,
      canProductionWrite = prodWrite,
      canFinishingWrite = finishingWrite,
      canQualityWrite = qualityWrite,
      canOqlWrite = oqlWrite,
      canNptWrite = nptWrite,
      canMorningTargetWrite = morningTargetWrite,
      canMonthlyTargetWrite = monthlyTargetWrite,
      canTargetRevisionWrite = targetRevisionWrite
    )
    roleSaveSuccessMessage = "✓ ${matrixRole.displayName} রোলের পারমিশন সংরক্ষিত হয়েছে।"
  }

  // State for User Account Dialog (Add / Edit)
  var showUserDialog by remember { mutableStateOf(false) }
  var editingUser by remember { mutableStateOf<UserAccount?>(null) }
  var userSearchQuery by remember { mutableStateOf("") }

  // Form State
  var formId by remember { mutableStateOf("") }
  var formName by remember { mutableStateOf("") }
  var formPassword by remember { mutableStateOf("123") }
  var formRole by remember { mutableStateOf(UserRole.SUPERVISOR) }
  var formFloor by remember { mutableStateOf(FloorScope.SEW_2) }
  var formLine by remember { mutableStateOf("K1") }

  // Individual Form Toggles
  var formViewDashboard by remember { mutableStateOf(true) }
  var formViewTv by remember { mutableStateOf(true) }
  var formViewMonthlyEff by remember { mutableStateOf(true) }
  var formViewMonthlyDhu by remember { mutableStateOf(true) }
  var formViewMonthlyOql by remember { mutableStateOf(true) }
  var formViewDeepDive by remember { mutableStateOf(true) }
  var formViewAuditLogs by remember { mutableStateOf(false) }
  var formExportReports by remember { mutableStateOf(false) }

  var formProdWrite by remember { mutableStateOf(false) }
  var formFinishingWrite by remember { mutableStateOf(false) }
  var formQualityWrite by remember { mutableStateOf(false) }
  var formOqlWrite by remember { mutableStateOf(false) }
  var formNptWrite by remember { mutableStateOf(false) }
  var formMorningTargetWrite by remember { mutableStateOf(false) }
  var formMonthlyTargetWrite by remember { mutableStateOf(false) }
  var formTargetRevisionWrite by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Admin Access Control Panel", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text("Northern Fashions Ltd. • Role & Function Permissions", fontSize = 10.sp, color = Color(0xFFBAE6FD))
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("admin_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        actions = {
          IconButton(onClick = {
            editingUser = null
            dialogErrorMessage = null
            formId = ""
            formName = ""
            formPassword = ""
            formRole = UserRole.PRODUCTION_REPORTER
            formFloor = FloorScope.SEW_2
            formLine = "K1"
            formViewDashboard = true
            formViewTv = false
            formViewMonthlyEff = true
            formViewMonthlyDhu = false
            formViewMonthlyOql = false
            formViewDeepDive = true
            formViewAuditLogs = false
            formExportReports = false
            formProdWrite = true
            formFinishingWrite = true
            formQualityWrite = false
            formOqlWrite = false
            formNptWrite = false
            formMorningTargetWrite = false
            formMonthlyTargetWrite = false
            formTargetRevisionWrite = false
            showUserDialog = true
          }) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Add User", tint = Color(0xFFFDE047))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
      )
    },
    containerColor = Color(0xFFF8FAFC)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // 1. Prominent Developer Credit Banner
      Surface(
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              color = Color(0xFF0284C7),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.size(24.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              }
            }
            Column {
              Text(
                text = "This app is developed by Shamim",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "IPE and Planning • Northern Fashions Ltd.",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      // 2. Navigation Tab Bar (Matrix vs Users)
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Function Access Matrix\n(রোল পারমিশন)", fontSize = 11.5.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("User Accounts\n(ইউজার তালিকা ও লাইন)", fontSize = 11.5.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
        )
      }

      // 3. Tab Content
      when (selectedTab) {
        0 -> {
          // TAB 0: FUNCTION ACCESS MATRIX
          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Explanation Header
            item {
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(24.dp))
                  Column {
                    Text(
                      text = "সেকশন ভিত্তিক অ্যাক্সেস কন্ট্রোল (View vs Data Entry)",
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = Color(0xFF1E3A8A)
                    )
                    Text(
                      text = "এখানে অ্যাডমিন নির্ধারণ করবেন কোন রোল দেখতে পারবে (View) এবং কোন রোল এন্ট্রি দিতে পারবে (Data Entry)। ম্যানেজমেন্টের ক্ষেত্রে ডাটা এন্ট্রি সম্পূর্ণ বন্ধ রাখা হয়েছে।",
                      fontSize = 10.5.sp,
                      color = Color(0xFF334155),
                      lineHeight = 14.sp
                    )
                  }
                }
              }
            }

            // Quick Role Enforce Buttons
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    repository.applyManagementStrictPolicy()
                    roleSaveSuccessMessage = "ম্যানেজমেন্টের জন্য View-Only পলিসি সফলভাবে প্রয়োগ করা হয়েছে (ডাটা এন্ট্রি বন্ধ)।"
                    // Update current states if viewing management
                    if (matrixRole in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER)) {
                      roleCanProductionWrite = false
                      roleCanFinishingWrite = false
                      roleCanQualityWrite = false
                      roleCanOqlWrite = false
                      roleCanNptWrite = false
                      roleCanMorningTargetWrite = false
                      roleCanMonthlyTargetWrite = false
                      roleCanTargetRevisionWrite = false
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("ম্যানেজমেন্ট View-Only Enforce", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }

            // Role Selector Chips
            item {
              Text("Select Role to Configure:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
              Spacer(Modifier.height(4.dp))
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                listOf(
                  UserRole.MANAGEMENT_VIEW,
                  UserRole.PRODUCTION_REPORTER,
                  UserRole.SUPERVISOR,
                  UserRole.QUALITY_REPORTER,
                  UserRole.AUDITOR,
                  UserRole.IE,
                  UserRole.MECHANIC,
                  UserRole.PRODUCTION_MANAGER,
                  UserRole.QUALITY_MANAGER
                ).forEach { role ->
                  val isSelected = matrixRole == role
                  FilterChip(
                    selected = isSelected,
                    onClick = { matrixRole = role },
                    label = { Text(role.displayName, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                      selectedContainerColor = NflRoyalBlue,
                      selectedLabelColor = Color.White
                    )
                  )
                }
              }
            }

            // Active Role Summary Card
            item {
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
              ) {
                Column(Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(
                        text = "Configuring Role: ${matrixRole.displayName}",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.5.sp,
                        color = Color(0xFF0F172A)
                      )
                      Text(
                        text = "Group: ${matrixRole.group} • Active users with this role: ${targetRoleUsers.size}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                      )
                    }
                    if (matrixRole in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER)) {
                      Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Text(
                          text = "🔒 View-Only Role",
                          color = Color(0xFFB45309),
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                }
              }
            }

            // SECTION 1: DATA ENTRY FUNCTIONS (ডাটা এন্ট্রি ফাংশনসমূহ)
            item {
              Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                  Column {
                    Text(
                      text = "১. ডাটা এন্ট্রি ফাংশনসমূহ (Data Entry Controls)",
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.5.sp,
                      color = Color(0xFF991B1B)
                    )
                    Text(
                      text = "কে কোন বিষয়ে ডাটা ইনপুট বা এন্ট্রি দিতে পারবে তা টগল করুন",
                      fontSize = 10.sp,
                      color = Color(0xFFB91C1C)
                    )
                  }
                }
              }
            }

            item {
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
              ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  PermToggleRow(
                    title = "ঘণ্টাওয়ারী সুইং প্রোডাকশন আউটপুট এন্ট্রি",
                    subtitle = "Hourly Sewing Production Output Entry (Sew-1, Sew-2, Sew-3)",
                    checked = roleCanProductionWrite,
                    onCheckedChange = {
                      roleCanProductionWrite = it
                      autoSaveRoleMatrix(prodWrite = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "আইরনিং ও প্যাকিং ফিনিশিং এন্ট্রি",
                    subtitle = "Ironing & Packing Finishing Entry (Iron 1-3, Packing 1-3)",
                    checked = roleCanFinishingWrite,
                    onCheckedChange = {
                      roleCanFinishingWrite = it
                      autoSaveRoleMatrix(finishingWrite = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "কোয়ালিটি ডিএইচইউ ইন্সপেকশন এন্ট্রি",
                    subtitle = "Quality Endline DHU Inspection & Defect Counter",
                    checked = roleCanQualityWrite,
                    onCheckedChange = {
                      roleCanQualityWrite = it
                      autoSaveRoleMatrix(qualityWrite = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "ওকেএল / বায়ার অডিট এন্ট্রি",
                    subtitle = "OQL / Buyer Final Audit Inspection Record",
                    checked = roleCanOqlWrite,
                    onCheckedChange = {
                      roleCanOqlWrite = it
                      autoSaveRoleMatrix(oqlWrite = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "এনপিটি ও মেশিন ব্রেকডাউন এন্ট্রি",
                    subtitle = "NPT Breakdown, Stoppage & Mechanic Call",
                    checked = roleCanNptWrite,
                    onCheckedChange = {
                      roleCanNptWrite = it
                      autoSaveRoleMatrix(nptWrite = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "সকালের লাইন টার্গেট ও ম্যানপাওয়ার এন্ট্রি",
                    subtitle = "Morning Target, Operator/Helper Count & SMV",
                    checked = roleCanMorningTargetWrite,
                    onCheckedChange = {
                      roleCanMorningTargetWrite = it
                      autoSaveRoleMatrix(morningTargetWrite = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "মান্থলি ফ্যাক্টরি টার্গেট সেটআপ",
                    subtitle = "Monthly Factory Target Configuration (Eff %, DHU %, OQL %)",
                    checked = roleCanMonthlyTargetWrite,
                    onCheckedChange = {
                      roleCanMonthlyTargetWrite = it
                      autoSaveRoleMatrix(monthlyTargetWrite = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "টার্গেট সংশোধন ও ডাটা এডিট",
                    subtitle = "Target Revision & Hourly Output Data Edit",
                    checked = roleCanTargetRevisionWrite,
                    onCheckedChange = {
                      roleCanTargetRevisionWrite = it
                      autoSaveRoleMatrix(targetRevisionWrite = it)
                    }
                  )
                }
              }
            }

            // SECTION 2: VISUAL & REPORTING FUNCTIONS (ভিজ্যুয়াল ও রিপোর্ট ফাংশনসমূহ)
            item {
              Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                  Column {
                    Text(
                      text = "২. ভিজ্যুয়াল ও রিপোর্ট দেখার অনুমতি (Visual / View Access)",
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.5.sp,
                      color = Color(0xFF1E3A8A)
                    )
                    Text(
                      text = "কে কোন ড্যাশবোর্ড বা রিপোর্ট দেখতে পারবে তা নির্ধারণ করুন",
                      fontSize = 10.sp,
                      color = Color(0xFF2563EB)
                    )
                  }
                }
              }
            }

            item {
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
              ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  PermToggleRow(
                    title = "লাইভ প্রোডাকশন ড্যাশবোর্ড",
                    subtitle = "Live Factory Hourly Matrix, Speedometers & Line Gauges",
                    checked = roleCanViewDashboard,
                    onCheckedChange = {
                      roleCanViewDashboard = it
                      autoSaveRoleMatrix(viewDashboard = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "স্মার্ট টিভি ফুলস্ক্রিন ডিসপ্লে মোড",
                    subtitle = "16:9 Smart TV Auto-scroll Overhead Display",
                    checked = roleCanViewTvDashboard,
                    onCheckedChange = {
                      roleCanViewTvDashboard = it
                      autoSaveRoleMatrix(viewTv = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "মান্থলি এফিসিয়েন্সি ব্রেকডাউন ও লাইন টেবিল",
                    subtitle = "Monthly Efficiency Breakdown Table (Sew-1, Sew-2, Sew-3)",
                    checked = roleCanViewMonthlyEff,
                    onCheckedChange = {
                      roleCanViewMonthlyEff = it
                      autoSaveRoleMatrix(viewMonthlyEff = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "মান্থলি ডিএইচইউ কোয়ালিটি রিপোর্ট",
                    subtitle = "Monthly DHU Quality Table & Defect Pareto Breakdown",
                    checked = roleCanViewMonthlyDhu,
                    onCheckedChange = {
                      roleCanViewMonthlyDhu = it
                      autoSaveRoleMatrix(viewMonthlyDhu = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "মান্থলি ওকেএল অডিট রিপোর্ট",
                    subtitle = "Monthly OQL Audit Compliance & Acceptance Table",
                    checked = roleCanViewMonthlyOql,
                    onCheckedChange = {
                      roleCanViewMonthlyOql = it
                      autoSaveRoleMatrix(viewMonthlyOql = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "লাইন ভিত্তিক বিস্তারিত অ্যানালিটিক্স",
                    subtitle = "Line Deep Dive, Hourly Trend Graphs & Speedometer",
                    checked = roleCanViewDeepDive,
                    onCheckedChange = {
                      roleCanViewDeepDive = it
                      autoSaveRoleMatrix(viewDeepDive = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "অডিট লগ ও হিস্ট্রি",
                    subtitle = "Audit Logs & System Activity Trail",
                    checked = roleCanViewAuditLogs,
                    onCheckedChange = {
                      roleCanViewAuditLogs = it
                      autoSaveRoleMatrix(viewAuditLogs = it)
                    }
                  )
                  HorizontalDivider(color = Color(0xFFF1F5F9))
                  PermToggleRow(
                    title = "পিডিএফ ও এক্সেল রিপোর্ট এক্সপোর্ট",
                    subtitle = "Export Production, Quality & NPT Documents",
                    checked = roleCanExportReports,
                    onCheckedChange = {
                      roleCanExportReports = it
                      autoSaveRoleMatrix(exportReports = it)
                    }
                  )
                }
              }
            }

            // Save Policy Button
            item {
              Button(
                onClick = {
                  repository.applyRolePolicy(
                    role = matrixRole,
                    canViewDashboard = roleCanViewDashboard,
                    canViewTvDashboard = roleCanViewTvDashboard,
                    canViewMonthlyEfficiency = roleCanViewMonthlyEff,
                    canViewMonthlyDhu = roleCanViewMonthlyDhu,
                    canViewMonthlyOql = roleCanViewMonthlyOql,
                    canViewLineDeepDive = roleCanViewDeepDive,
                    canViewAuditLogs = roleCanViewAuditLogs,
                    canExportReports = roleCanExportReports,
                    canProductionWrite = roleCanProductionWrite,
                    canFinishingWrite = roleCanFinishingWrite,
                    canQualityWrite = roleCanQualityWrite,
                    canOqlWrite = roleCanOqlWrite,
                    canNptWrite = roleCanNptWrite,
                    canMorningTargetWrite = roleCanMorningTargetWrite,
                    canMonthlyTargetWrite = roleCanMonthlyTargetWrite,
                    canTargetRevisionWrite = roleCanTargetRevisionWrite
                  )
                  roleSaveSuccessMessage = "${matrixRole.displayName} রোলের পারমিশন পলিসি সফলভাবে সংরক্ষিত হয়েছে।"
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NflRoyalBlue)
              ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Role Permissions (${matrixRole.displayName})", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
              }
            }

            if (roleSaveSuccessMessage != null) {
              item {
                Surface(
                  color = Color(0xFFDCFCE7),
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(roleSaveSuccessMessage ?: "", color = Color(0xFF14532D), fontSize = 11.5.sp)
                  }
                }
              }
            }
          }
        }
        1 -> {
          // TAB 1: USER ACCOUNTS & OVERRIDES
          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (userActionBanner != null) {
              item {
                Surface(
                  color = Color(0xFFDCFCE7),
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(userActionBanner ?: "", color = Color(0xFF14532D), fontSize = 11.5.sp)
                  }
                }
              }
            }

            // Pending Device & Account Approval Requests (In-App Admin Approval fallback for shamim.sustipe15@gmail.com)
            if (pendingDeviceRequests.isNotEmpty()) {
              item {
                Card(
                  shape = RoundedCornerShape(10.dp),
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                  border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                      Text(
                        text = "Pending Device / Account Approvals (${pendingDeviceRequests.size}) • ${com.example.model.PRIMARY_ADMIN_GMAIL}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                      )
                    }
                    pendingDeviceRequests.forEach { req ->
                      Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Row(
                          modifier = Modifier.padding(10.dp),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Column(Modifier.weight(1f)) {
                            Text("${req.userName} (ID: ${req.userId})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                            Text("Device: ${req.deviceName} (${req.deviceId})", fontSize = 10.sp, color = Color(0xFF475569))
                            Text("Type: ${req.requestType}", fontSize = 9.5.sp, color = Color(0xFFB45309))
                          }
                          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                              onClick = {
                                val res = repository.adminApproveDeviceOrAccountRequest(req.requestId)
                                userActionBanner = res.message
                              },
                              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                              modifier = Modifier.height(32.dp)
                            ) {
                              Text("Approve", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                              onClick = {
                                val res = repository.adminRejectDeviceOrAccountRequest(req.requestId)
                                userActionBanner = res.message
                              },
                              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                              modifier = Modifier.height(32.dp)
                            ) {
                              Text("Reject", fontSize = 10.5.sp, color = Color(0xFFDC2626))
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

            // Search Bar
            item {
              OutlinedTextField(
                value = userSearchQuery,
                onValueChange = { userSearchQuery = it },
                placeholder = { Text("Search users by name, ID or role...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                  if (userSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { userSearchQuery = "" }) {
                      Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                  }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
              )
            }

            val filteredUsers = users.filter { u ->
              userSearchQuery.isBlank() ||
                  u.name.contains(userSearchQuery, ignoreCase = true) ||
                  u.id.contains(userSearchQuery, ignoreCase = true) ||
                  u.role.displayName.contains(userSearchQuery, ignoreCase = true)
            }

            items(filteredUsers, key = { it.id }) { user ->
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(Modifier.weight(1f)) {
                      Text(user.name, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0F172A))
                      Text(
                        "Company ID: ${user.id} • Pass: •••••• • Devices: ${user.approvedDevices.size}/${com.example.model.MAX_APPROVED_DEVICES_PER_USER}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                      )
                    }
                    Surface(
                      color = when (user.role) {
                        UserRole.ADMIN -> Color(0xFFFEE2E2)
                        UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER -> Color(0xFFFEF3C7)
                        UserRole.SUPERVISOR -> Color(0xFFDBEAFE)
                        else -> Color(0xFFDCFCE7)
                      },
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        text = user.role.displayName,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (user.role) {
                          UserRole.ADMIN -> Color(0xFF991B1B)
                          UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER -> Color(0xFFB45309)
                          UserRole.SUPERVISOR -> Color(0xFF1E40AF)
                          else -> Color(0xFF166534)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }

                  Text(
                    text = "Floor: ${user.floor.displayName} • Assigned Line: ${user.assignedLine}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                  )

                  // Status pill: View-Only vs Entry Enabled
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    if (user.isViewOnly) {
                      Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(12.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("View Only (No Data Entry)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                      }
                    } else {
                      Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(12.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("Entry Enabled", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                        }
                      }
                    }

                    // Quick Write badges
                    Row(
                      modifier = Modifier.horizontalScroll(rememberScrollState()),
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      if (user.canProductionWrite) PermissionChip("W:Prod")
                      if (user.canFinishingWrite) PermissionChip("W:Finish")
                      if (user.canQualityWrite) PermissionChip("W:DHU")
                      if (user.canOqlWrite) PermissionChip("W:OQL")
                      if (user.canNptWrite) PermissionChip("W:NPT")
                      if (user.canMorningTargetWrite) PermissionChip("W:MorningTgt")
                      if (user.canMonthlyTargetWrite) PermissionChip("W:MonthlyTgt")
                      if (user.canTargetRevisionWrite) PermissionChip("W:Revise")
                    }
                  }

                  // Read / View Access badges
                  Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text("Read:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    if (user.canViewDashboard) PermissionChip("R:Dash")
                    if (user.canViewTvDashboard) PermissionChip("R:TV")
                    if (user.canViewMonthlyEfficiency) PermissionChip("R:M-Eff")
                    if (user.canViewMonthlyDhu) PermissionChip("R:M-DHU")
                    if (user.canViewMonthlyOql) PermissionChip("R:M-OQL")
                    if (user.canViewLineDeepDive) PermissionChip("R:DeepDive")
                    if (user.canViewAuditLogs) PermissionChip("R:Audit")
                    if (user.canExportReports) PermissionChip("R:Export")
                  }

                  // Active Approved Devices (2-Device Limit Control & Revocation)
                  if (user.approvedDevices.isNotEmpty()) {
                    Surface(
                      color = Color(0xFFF8FAFC),
                      shape = RoundedCornerShape(6.dp),
                      border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                          text = "📱 Active Approved Devices (${user.approvedDevices.size}/${com.example.model.MAX_APPROVED_DEVICES_PER_USER} Max):",
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF0F172A)
                        )
                        user.approvedDevices.forEach { dev ->
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Text(
                              text = "• ${dev.deviceName} (${dev.deviceId})",
                              fontSize = 10.sp,
                              color = Color(0xFF334155),
                              modifier = Modifier.weight(1f)
                            )
                            TextButton(
                              onClick = {
                                val res = repository.adminRevokeUserDevice(user.id, dev.deviceId)
                                userActionBanner = res.message
                              },
                              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                              modifier = Modifier.height(26.dp)
                            ) {
                              Text("Revoke Device", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                          }
                        }
                      }
                    }
                  }

                  if (!user.isAdmin) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      TextButton(onClick = {
                        editingUser = user
                        dialogErrorMessage = null
                        formId = user.id
                        formName = user.name
                        formPassword = ""
                        formRole = user.role
                        formFloor = user.floor
                        formLine = user.assignedLine

                        formViewDashboard = user.canViewDashboard
                        formViewTv = user.canViewTvDashboard
                        formViewMonthlyEff = user.canViewMonthlyEfficiency
                        formViewMonthlyDhu = user.canViewMonthlyDhu
                        formViewMonthlyOql = user.canViewMonthlyOql
                        formViewDeepDive = user.canViewLineDeepDive
                        formViewAuditLogs = user.canViewAuditLogs
                        formExportReports = user.canExportReports

                        formProdWrite = user.canProductionWrite
                        formFinishingWrite = user.canFinishingWrite
                        formQualityWrite = user.canQualityWrite
                        formOqlWrite = user.canOqlWrite
                        formNptWrite = user.canNptWrite
                        formMorningTargetWrite = user.canMorningTargetWrite
                        formMonthlyTargetWrite = user.canMonthlyTargetWrite
                        formTargetRevisionWrite = user.canTargetRevisionWrite

                        showUserDialog = true
                      }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit Permissions", fontSize = 11.sp)
                      }
                      TextButton(onClick = { repository.deleteUser(user.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 11.sp, color = Color(0xFFDC2626))
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
  }

  // DIALOG: CREATE / EDIT USER WITH FULL GRANULAR CONTROLS
  if (showUserDialog) {
    AlertDialog(
      onDismissRequest = {
        showUserDialog = false
        editingUser = null
      },
      title = {
        Column {
          Text(if (editingUser != null) "Edit User Permissions" else "Create New Factory User", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text("Configure View Access & Data Entry Rights", fontSize = 10.5.sp, color = Color(0xFF64748B))
        }
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedTextField(
            value = formId,
            onValueChange = { formId = it },
            label = { Text("User ID *") },
            placeholder = { Text("e.g. prod_01, sup_line_k") },
            enabled = editingUser == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = formName,
            onValueChange = { formName = it },
            label = { Text("Full Name *") },
            placeholder = { Text("e.g. Md. Shamim Hossain") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = formPassword,
            onValueChange = { formPassword = it },
            label = { Text("Password *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          // Role Selector
          Text("Assigned Role:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(
              UserRole.PRODUCTION_REPORTER,
              UserRole.SUPERVISOR,
              UserRole.QUALITY_REPORTER,
              UserRole.AUDITOR,
              UserRole.IE,
              UserRole.MECHANIC,
              UserRole.MANAGEMENT_VIEW,
              UserRole.PRODUCTION_MANAGER,
              UserRole.QUALITY_MANAGER
            ).forEach { r ->
              FilterChip(
                selected = formRole == r,
                onClick = {
                  formRole = r
                  val refUser = users.firstOrNull { it.role == r && !it.isAdmin }
                  if (refUser != null) {
                    formViewDashboard = refUser.canViewDashboard
                    formViewTv = refUser.canViewTvDashboard
                    formViewMonthlyEff = refUser.canViewMonthlyEfficiency
                    formViewMonthlyDhu = refUser.canViewMonthlyDhu
                    formViewMonthlyOql = refUser.canViewMonthlyOql
                    formViewDeepDive = refUser.canViewLineDeepDive
                    formViewAuditLogs = refUser.canViewAuditLogs
                    formExportReports = refUser.canExportReports
                    formProdWrite = refUser.canProductionWrite
                    formFinishingWrite = refUser.canFinishingWrite
                    formQualityWrite = refUser.canQualityWrite
                    formOqlWrite = refUser.canOqlWrite
                    formNptWrite = refUser.canNptWrite
                    formMorningTargetWrite = refUser.canMorningTargetWrite
                    formMonthlyTargetWrite = refUser.canMonthlyTargetWrite
                    formTargetRevisionWrite = refUser.canTargetRevisionWrite
                  }
                },
                label = { Text(r.displayName, fontSize = 10.sp) }
              )
            }
          }

          // Floor Selector
          Text("Assigned Floor:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.ALL).forEach { flr ->
              Surface(
                onClick = { formFloor = flr },
                shape = RoundedCornerShape(4.dp),
                color = if (formFloor == flr) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = flr.displayName,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (formFloor == flr) Color.White else Color(0xFF334155),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(vertical = 6.dp)
                )
              }
            }
          }

          OutlinedTextField(
            value = formLine,
            onValueChange = { formLine = it },
            label = { Text("Assigned Line (e.g. K1, J1, Iron 1, Packing 1, All)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          // Quick Preset: Make View-Only
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Button(
              onClick = {
                formProdWrite = false
                formFinishingWrite = false
                formQualityWrite = false
                formOqlWrite = false
                formNptWrite = false
                formMorningTargetWrite = false
                formMonthlyTargetWrite = false
                formTargetRevisionWrite = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF3C7)),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("Set View-Only (No Entry)", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = {
                formViewDashboard = true
                formViewTv = true
                formViewMonthlyEff = true
                formViewMonthlyDhu = true
                formViewMonthlyOql = true
                formViewDeepDive = true
                formViewAuditLogs = true
                formExportReports = true
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF)),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("Enable All Views", color = Color(0xFF1D4ED8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }

          // Granular DATA ENTRY Toggles
          Text("✍️ Data Entry Permissions (ডাটা এন্ট্রি):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF991B1B))
          PermToggleRow("Hourly Production Entry", "Sewing lines hourly output", formProdWrite) { formProdWrite = it }
          PermToggleRow("Finishing Entry", "Iron 1-3 & Packing 1-3", formFinishingWrite) { formFinishingWrite = it }
          PermToggleRow("Quality DHU Entry", "Endline inspection & defects", formQualityWrite) { formQualityWrite = it }
          PermToggleRow("OQL Audit Entry", "Buyer audit pass/fail", formOqlWrite) { formOqlWrite = it }
          PermToggleRow("NPT Incident Entry", "Breakdown & mechanic stop", formNptWrite) { formNptWrite = it }
          PermToggleRow("Morning Target Setup", "Line target, helpers, operators", formMorningTargetWrite) { formMorningTargetWrite = it }
          PermToggleRow("Monthly Target Setup", "Monthly baseline setup", formMonthlyTargetWrite) { formMonthlyTargetWrite = it }
          PermToggleRow("Target Revision", "Revising saved hourly targets", formTargetRevisionWrite) { formTargetRevisionWrite = it }

          // Granular VISUAL Toggles
          Text("👁️ Visual Access Permissions (কে দেখতে পারবে):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E3A8A))
          PermToggleRow("Live Production Dashboard", "Hourly floor matrix & gauges", formViewDashboard) { formViewDashboard = it }
          PermToggleRow("Smart TV Display", "16:9 widescreen display", formViewTv) { formViewTv = it }
          PermToggleRow("Monthly Efficiency Breakdown", "Line-wise efficiency table", formViewMonthlyEff) { formViewMonthlyEff = it }
          PermToggleRow("Monthly DHU Quality Table", "Line-wise DHU breakdown", formViewMonthlyDhu) { formViewMonthlyDhu = it }
          PermToggleRow("Monthly OQL Audit Table", "Line-wise OQL audit table", formViewMonthlyOql) { formViewMonthlyOql = it }
          PermToggleRow("Line Deep Dive Analytics", "Hourly trend graphs", formViewDeepDive) { formViewDeepDive = it }
          PermToggleRow("Audit Logs & History", "System audit trails", formViewAuditLogs) { formViewAuditLogs = it }
          PermToggleRow("Export PDF & Excel", "Summary reports export", formExportReports) { formExportReports = it }
          if (dialogErrorMessage != null) {
            Text(dialogErrorMessage ?: "", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val effectivePassword = if (formPassword.isNotBlank()) formPassword.trim() else (editingUser?.password ?: "")
            if (formId.isBlank() || formName.isBlank() || effectivePassword.isBlank()) {
              dialogErrorMessage = "Please provide Company ID, Full Name, and Password."
              return@Button
            }
            val account = UserAccount(
              id = formId.trim(),
              employeeId = formId.trim(),
              name = formName.trim(),
              password = effectivePassword,
              role = formRole,
              floor = formFloor,
              assignedLine = formLine.trim().ifEmpty { "K1" },
              isAdmin = formRole == UserRole.ADMIN,
              canViewDashboard = formViewDashboard,
              canViewTvDashboard = formViewTv,
              canViewMonthlyEfficiency = formViewMonthlyEff,
              canViewMonthlyDhu = formViewMonthlyDhu,
              canViewMonthlyOql = formViewMonthlyOql,
              canViewLineDeepDive = formViewDeepDive,
              canViewAuditLogs = formViewAuditLogs,
              canExportReports = formExportReports,
              canProductionWrite = formProdWrite,
              canFinishingWrite = formFinishingWrite,
              canQualityWrite = formQualityWrite,
              canOqlWrite = formOqlWrite,
              canNptWrite = formNptWrite,
              canMorningTargetWrite = formMorningTargetWrite,
              canMonthlyTargetWrite = formMonthlyTargetWrite,
              canTargetRevisionWrite = formTargetRevisionWrite,
              approvedDevices = editingUser?.approvedDevices ?: emptyList()
            )
            val saveRes = repository.saveUser(account, isNewAccount = editingUser == null)
            if (saveRes.success) {
              userActionBanner = saveRes.message
              dialogErrorMessage = null
              showUserDialog = false
              editingUser = null
            } else {
              dialogErrorMessage = saveRes.message
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = NflRoyalBlue)
        ) {
          Text("Save User", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showUserDialog = false
          editingUser = null
        }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun PermToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
      Text(subtitle, fontSize = 9.sp, color = Color(0xFF64748B))
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun PermissionChip(label: String) {
  Surface(
    color = Color(0xFFF1F5F9),
    shape = RoundedCornerShape(3.dp)
  ) {
    Text(
      text = label,
      fontSize = 8.5.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF334155),
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
    )
  }
}
