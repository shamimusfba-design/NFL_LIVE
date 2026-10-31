package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.AdminApprovalDeepLink
import com.example.model.UserRole
import com.example.service.AdminApprovalSyncWorker
import com.example.service.FactoryAlertManager
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NflRoyalBlue
import kotlinx.coroutines.launch

enum class AppScreen {
  LOGIN,
  HOME,
  PRODUCTION_ENTRY,
  QUALITY_ENTRY,
  NPT_SUPERVISOR,
  SUPERVISOR_SETUP,
  MONTHLY_SETUP,
  WEEKLY_QUALITY,
  ADMIN_USERS,
  LINE_DEEP_DIVE,
  QUALITY_DASHBOARD,
  TV_DASHBOARD,
  HISTORY_SYNC,
  SETTINGS,
  MONTHLY_BREAKDOWN,
  NOTIFICATION_CENTER
}

class MainActivity : ComponentActivity() {

  private lateinit var repository: NflRepository

  private fun extractApprovalDeepLink(sourceIntent: Intent?) {
    val openAdmin = sourceIntent?.getBooleanExtra(FactoryAlertManager.EXTRA_OPEN_ADMIN_APPROVALS, false) == true
    val requestId = sourceIntent?.getStringExtra(FactoryAlertManager.EXTRA_APPROVAL_REQUEST_ID).orEmpty().trim()
    val requestType = sourceIntent?.getStringExtra(FactoryAlertManager.EXTRA_APPROVAL_REQUEST_TYPE).orEmpty().trim()
    val targetUserId = sourceIntent?.getStringExtra(FactoryAlertManager.EXTRA_APPROVAL_TARGET_USER_ID).orEmpty().trim()
    if (openAdmin || requestId.isNotEmpty()) {
      repository.setPendingApprovalDeepLink(
        AdminApprovalDeepLink(
          requestId = requestId.ifEmpty { "pending_queue" },
          requestType = requestType.ifEmpty { "EMPLOYEE_REGISTRATION" },
          targetUserId = targetUserId
        )
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (::repository.isInitialized) {
      extractApprovalDeepLink(intent)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      window.decorView.isForceDarkAllowed = false
    }
    enableEdgeToEdge()
    repository = NflRepository.getInstance(applicationContext)
    extractApprovalDeepLink(intent)

    setContent {
      MyApplicationTheme(darkTheme = false) {
        val currentUser by repository.currentUser.collectAsStateWithLifecycle()
        val currentRole by repository.currentRole.collectAsStateWithLifecycle()
        val currentUserName by repository.currentUserName.collectAsStateWithLifecycle()
        val incidents by repository.incidents.collectAsStateWithLifecycle()
        val pendingDeepLink by repository.pendingApprovalDeepLink.collectAsStateWithLifecycle()

        val users by repository.users.collectAsStateWithLifecycle()
        val deviceApprovalRequests by repository.deviceApprovalRequests.collectAsStateWithLifecycle()

        val isTelevisionDevice = remember {
          packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
              (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
              android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        }

        val hasValidTvSession = remember(currentUser, users, deviceApprovalRequests) {
          repository.hasValidTvSession()
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(currentUser?.id, currentUser?.isAdmin) {
          if (currentUser?.isAdmin == true) {
            AdminApprovalSyncWorker.schedulePeriodicSync(applicationContext)
            AdminApprovalSyncWorker.triggerImmediateCheck(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS
              ) == PackageManager.PERMISSION_GRANTED
              if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            }
          }
        }

        val openAdminFromNotification = remember {
          intent?.getBooleanExtra("open_admin_approvals", false) == true
        }

        var currentScreen by remember {
          val initialUser = currentUser ?: repository.currentUser.value
          mutableStateOf(
            when {
              isTelevisionDevice -> AppScreen.TV_DASHBOARD
              initialUser == null -> AppScreen.LOGIN
              initialUser.id == "management" || initialUser.id.equals(NflRepository.TV_DISPLAY_ACCOUNT_ID, ignoreCase = true) -> AppScreen.TV_DASHBOARD
              (openAdminFromNotification || pendingDeepLink != null) && initialUser.isAdmin -> AppScreen.ADMIN_USERS
              else -> AppScreen.HOME
            }
          )
        }
        var selectedDeepDiveLineId by remember { mutableStateOf("K1") }
        var selectedMonthlyMetric by remember { mutableStateOf(com.example.model.MonthlyMetricType.EFFICIENCY) }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val effectiveUser = currentUser ?: repository.currentUser.value
        val isTvDisplayAccount = effectiveUser?.id?.equals(NflRepository.TV_DISPLAY_ACCOUNT_ID, ignoreCase = true) == true

        // Navigate directly to ADMIN_USERS whenever an authenticated Admin has a pending notification deep-link
        LaunchedEffect(effectiveUser?.id, effectiveUser?.isAdmin, pendingDeepLink) {
          if (!isTelevisionDevice && !isTvDisplayAccount && effectiveUser?.isAdmin == true && pendingDeepLink != null) {
            currentScreen = AppScreen.ADMIN_USERS
          }
        }

        // Prevent TV device or tv_display session from ever entering entry, admin, or settings screens
        LaunchedEffect(currentScreen, isTelevisionDevice, isTvDisplayAccount) {
          if ((isTelevisionDevice || isTvDisplayAccount) && currentScreen != AppScreen.TV_DASHBOARD) {
            currentScreen = AppScreen.TV_DASHBOARD
          }
        }

        // Handle Back Navigation cleanly
        if (!isTelevisionDevice && !isTvDisplayAccount && currentScreen != AppScreen.HOME && currentScreen != AppScreen.LOGIN) {
          BackHandler {
            if (drawerState.isOpen) {
              scope.launch { drawerState.close() }
            } else {
              currentScreen = AppScreen.HOME
            }
          }
        }

        if (isTelevisionDevice) {
          if (hasValidTvSession) {
            TvDashboardScreen(
              repository = repository,
              onNavigateBack = { /* Smart TV stays on TV_DASHBOARD */ }
            )
          } else {
            TvSetupScreen(repository = repository)
          }
        } else if (effectiveUser == null || currentScreen == AppScreen.LOGIN) {
          LoginScreen(
            repository = repository,
            onLoginSuccess = {
              val loggedIn = repository.currentUser.value
              currentScreen = when {
                loggedIn?.id == "management" || loggedIn?.id?.equals(NflRepository.TV_DISPLAY_ACCOUNT_ID, ignoreCase = true) == true -> AppScreen.TV_DASHBOARD
                loggedIn?.isAdmin == true && repository.pendingApprovalDeepLink.value != null -> AppScreen.ADMIN_USERS
                else -> AppScreen.HOME
              }
            }
          )
        } else {
          // Wrap main app inside smooth ModalNavigationDrawer
          ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentScreen != AppScreen.TV_DASHBOARD,
            drawerContent = {
              ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F172A),
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
              ) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                  Column(
                    modifier = Modifier
                      .fillMaxSize()
                      .verticalScroll(rememberScrollState())
                  ) {
                    // 1. Drawer Header with NFL LIVE Branding & Current User
                    Surface(
                      color = Color(0xFF1E293B),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(
                        modifier = Modifier
                          .fillMaxWidth()
                          .statusBarsPadding()
                          .padding(16.dp)
                      ) {
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                          Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(36.dp)
                          ) {
                            Box(contentAlignment = Alignment.Center) {
                              Icon(
                                painter = painterResource(R.drawable.ic_nfl_logo),
                                contentDescription = "NFL Live Logo",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                              )
                            }
                          }
                          Column {
                            Text(
                              text = "NFL LIVE",
                              color = Color.White,
                              fontSize = 17.sp,
                              fontWeight = FontWeight.Black
                            )
                            Text(
                              text = "Northern Fashions Ltd.",
                              color = Color(0xFF94A3B8),
                              fontSize = 10.sp
                            )
                            Text(
                              text = "Dev: Shamim • IPE & Planning",
                              color = Color(0xFF38BDF8),
                              fontSize = 9.5.sp,
                              fontWeight = FontWeight.Bold
                            )
                          }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // User Info Badge with Direct Switch / Login Action
                        Surface(
                          color = Color(0xFF0F172A),
                          shape = RoundedCornerShape(8.dp),
                          modifier = Modifier.fillMaxWidth()
                        ) {
                          Row(
                            modifier = Modifier
                              .fillMaxWidth()
                              .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                          ) {
                            Row(
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(8.dp),
                              modifier = Modifier.weight(1f)
                            ) {
                              Box(
                                modifier = Modifier
                                  .size(10.dp)
                                  .clip(CircleShape)
                                  .background(Color(0xFF10B981))
                              )
                              Column {
                                Text(
                                  text = currentRole.displayName,
                                  color = Color.White,
                                  fontWeight = FontWeight.Bold,
                                  fontSize = 12.sp,
                                  maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                  Text(
                                    text = "ID: ${currentUser?.id ?: currentRole.name.lowercase()}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp
                                  )
                                  if (currentUser?.isViewOnly == true) {
                                    Surface(
                                      color = Color(0xFFFEF3C7),
                                      shape = RoundedCornerShape(3.dp)
                                    ) {
                                      Text(
                                        text = "VIEW ONLY",
                                        color = Color(0xFFB45309),
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                      )
                                    }
                                  }
                                }
                              }
                            }

                            Surface(
                              onClick = {
                                currentScreen = AppScreen.LOGIN
                                scope.launch { drawerState.close() }
                              },
                              color = Color(0xFF1E3A8A),
                              shape = RoundedCornerShape(6.dp),
                              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f))
                            ) {
                              Text(
                                text = "Switch",
                                color = Color(0xFFFDE047),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                              )
                            }
                          }
                        }
                      }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Direct Login & Account Switch in Drawer
                    NavigationDrawerItem(
                      label = { Text("🔑 Switch User / Login Screen", color = Color(0xFFFDE047), fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                      selected = false,
                      onClick = {
                        currentScreen = AppScreen.LOGIN
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color(0xFF1E293B)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )

                    val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == UserRole.ADMIN
                    val canViewDash = isAdminUser || (currentUser?.canViewDashboard ?: true)
                    val canViewTv = isAdminUser || (currentUser?.canViewTvDashboard ?: false)
                    val canViewMonthly = isAdminUser || (currentUser?.let { it.canViewMonthlyEfficiency || it.canViewMonthlyDhu || it.canViewMonthlyOql } ?: false)
                    val canViewDeepDive = isAdminUser || (currentUser?.canViewLineDeepDive ?: false)
                    val canViewAudit = isAdminUser || (currentUser?.canViewAuditLogs ?: false)

                    val isIeUser = currentUser?.role == UserRole.IE
                    val canWriteMorningSetup = isAdminUser || isIeUser || (currentUser?.let { it.canMorningTargetWrite && it.role in setOf(UserRole.IE, UserRole.PRODUCTION_REPORTER) } ?: false)
                    val canWriteMonthlySetup = isAdminUser || isIeUser || (currentUser?.let { it.canMonthlyTargetWrite && it.role == UserRole.IE } ?: false)
                    val canWriteNpt = isAdminUser || isIeUser || (currentUser?.let { it.canNptWrite && it.role !in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER) } ?: false)
                    val canWriteProd = isAdminUser || isIeUser || (currentUser?.let { (it.canProductionWrite || it.canFinishingWrite) && it.role in setOf(UserRole.SUPERVISOR, UserRole.PRODUCTION_REPORTER, UserRole.IE) } ?: false)
                    val canWriteQual = isAdminUser || isIeUser || (currentUser?.let { (it.canQualityWrite || it.canOqlWrite) && it.role in setOf(UserRole.QUALITY_REPORTER, UserRole.AUDITOR, UserRole.IE) } ?: false)
                    val canWriteWeeklyQualityOrAudit = repository.canWriteWeeklyQualityOrBiHourlyAudit(currentUser)
                    val canWriteAnyEntry = canWriteProd || canWriteQual

                    // 2. Navigation Items
                    Text(
                      text = "FACTORY MODULES",
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF94A3B8),
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    NavigationDrawerItem(
                      label = { Text("🏠 Home (Live Matrix & Gauges)", color = if (currentScreen == AppScreen.HOME) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                      selected = currentScreen == AppScreen.HOME,
                      onClick = {
                        currentScreen = AppScreen.HOME
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color.White
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                      label = {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                          Text("🔔 Smart Factory Alerts", color = if (currentScreen == AppScreen.NOTIFICATION_CENTER) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold)
                          val unreadCount = incidents.count { it.status != com.example.model.IncidentStatus.RESOLVED && it.status != com.example.model.IncidentStatus.VERIFIED }
                          if (unreadCount > 0) {
                            Surface(
                              color = Color(0xFFEF4444),
                              shape = CircleShape
                            ) {
                              Text(
                                text = unreadCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                              )
                            }
                          }
                        }
                      },
                      selected = currentScreen == AppScreen.NOTIFICATION_CENTER,
                      onClick = {
                        currentScreen = AppScreen.NOTIFICATION_CENTER
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color.White
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )

                    if (canWriteMorningSetup) {
                      NavigationDrawerItem(
                        label = { Text("🌅 Morning Line Setup", color = if (currentScreen == AppScreen.SUPERVISOR_SETUP) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.SUPERVISOR_SETUP,
                        onClick = {
                          currentScreen = AppScreen.SUPERVISOR_SETUP
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canViewDeepDive) {
                      NavigationDrawerItem(
                        label = { Text("📊 Line Deep Dive Performance", color = if (currentScreen == AppScreen.LINE_DEEP_DIVE) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.LINE_DEEP_DIVE,
                        onClick = {
                          currentScreen = AppScreen.LINE_DEEP_DIVE
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canViewDeepDive) {
                      NavigationDrawerItem(
                        label = { Text("🧪 Quality Analytics Dashboard", color = if (currentScreen == AppScreen.QUALITY_DASHBOARD) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.QUALITY_DASHBOARD,
                        onClick = {
                          currentScreen = AppScreen.QUALITY_DASHBOARD
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canWriteNpt) {
                      NavigationDrawerItem(
                        label = { Text("⚙️ NPT & Maintenance Tracking", color = if (currentScreen == AppScreen.NPT_SUPERVISOR) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.NPT_SUPERVISOR,
                        onClick = {
                          currentScreen = AppScreen.NPT_SUPERVISOR
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canWriteProd) {
                      NavigationDrawerItem(
                        label = { Text("🧵 Production Data Entry", color = if (currentScreen == AppScreen.PRODUCTION_ENTRY) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.PRODUCTION_ENTRY,
                        onClick = {
                          currentScreen = AppScreen.PRODUCTION_ENTRY
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canWriteQual) {
                      NavigationDrawerItem(
                        label = { Text("🔍 Quality / Defect Entry", color = if (currentScreen == AppScreen.QUALITY_ENTRY) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.QUALITY_ENTRY,
                        onClick = {
                          currentScreen = AppScreen.QUALITY_ENTRY
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canWriteMonthlySetup) {
                      NavigationDrawerItem(
                        label = { Text("📅 Monthly Data Entry", color = if (currentScreen == AppScreen.MONTHLY_SETUP) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.MONTHLY_SETUP,
                        onClick = {
                          currentScreen = AppScreen.MONTHLY_SETUP
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canWriteWeeklyQualityOrAudit) {
                      NavigationDrawerItem(
                        label = { Text("📈 Weekly OQL / AQC Entry", color = if (currentScreen == AppScreen.WEEKLY_QUALITY) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.WEEKLY_QUALITY,
                        onClick = {
                          currentScreen = AppScreen.WEEKLY_QUALITY
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    // Admin & Management section
                    if (isAdminUser || canViewTv || canViewAudit || canViewMonthly) {
                      Text(
                        text = "MANAGEMENT & SECURITY",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                      )
                    }

                    if (isAdminUser) {
                      NavigationDrawerItem(
                        label = { Text("👥 User & Access Control", color = if (currentScreen == AppScreen.ADMIN_USERS) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.ADMIN_USERS,
                        onClick = {
                          currentScreen = AppScreen.ADMIN_USERS
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canViewMonthly) {
                      NavigationDrawerItem(
                        label = { Text("📈 Monthly Breakdown Reports", color = if (currentScreen == AppScreen.MONTHLY_BREAKDOWN) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.MONTHLY_BREAKDOWN,
                        onClick = {
                          currentScreen = AppScreen.MONTHLY_BREAKDOWN
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canViewTv) {
                      NavigationDrawerItem(
                        label = { Text("📺 Smart TV Dashboard (16:9)", color = if (currentScreen == AppScreen.TV_DASHBOARD) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.TV_DASHBOARD,
                        onClick = {
                          currentScreen = AppScreen.TV_DASHBOARD
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    if (canViewAudit) {
                      NavigationDrawerItem(
                        label = { Text("📜 History & Audit Logs", color = if (currentScreen == AppScreen.HISTORY_SYNC) Color(0xFF38BDF8) else Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == AppScreen.HISTORY_SYNC,
                        onClick = {
                          currentScreen = AppScreen.HISTORY_SYNC
                          scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                          unselectedContainerColor = Color.Transparent,
                          selectedContainerColor = Color(0xFF1E293B),
                          selectedTextColor = Color(0xFF38BDF8),
                          unselectedTextColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                      )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF334155))

                    // Developer Credit Banner in Drawer
                    Surface(
                      color = Color(0xFF0F172A),
                      shape = RoundedCornerShape(8.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                      Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                      ) {
                        Text(
                          text = "This app is developed by Shamim",
                          color = Color(0xFF38BDF8),
                          fontSize = 11.5.sp,
                          fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                          text = "IPE and Planning",
                          color = Color(0xFF94A3B8),
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Medium
                        )
                      }
                    }

                    // Logout Action
                    NavigationDrawerItem(
                      label = { Text("🚪 Logout (${currentUserName.take(15)})", color = Color(0xFFF87171), fontWeight = FontWeight.Bold) },
                      selected = false,
                      onClick = {
                        repository.logout()
                        currentScreen = AppScreen.LOGIN
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                      ),
                      modifier = Modifier.padding(10.dp)
                    )
                  }
                }
              }
            }
          ) {
            val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == UserRole.ADMIN
            val isIeUser = currentUser?.role == UserRole.IE
            val canViewDeepDive = isAdminUser || isIeUser || (currentUser?.canViewLineDeepDive ?: false)
            val canWriteMorningSetup = isAdminUser || isIeUser || (currentUser?.let { it.canMorningTargetWrite && it.role in setOf(UserRole.IE, UserRole.PRODUCTION_REPORTER) } ?: false)
            val canWriteMonthlySetup = isAdminUser || isIeUser || (currentUser?.let { it.canMonthlyTargetWrite && it.role == UserRole.IE } ?: false)
            val canWriteNpt = isAdminUser || isIeUser || (currentUser?.let { it.canNptWrite && it.role !in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER) } ?: false)
            val canWriteProd = isAdminUser || isIeUser || (currentUser?.let { (it.canProductionWrite || it.canFinishingWrite) && it.role in setOf(UserRole.SUPERVISOR, UserRole.PRODUCTION_REPORTER, UserRole.IE) } ?: false)
            val canWriteQual = isAdminUser || isIeUser || (currentUser?.let { (it.canQualityWrite || it.canOqlWrite) && it.role in setOf(UserRole.QUALITY_REPORTER, UserRole.AUDITOR, UserRole.IE) } ?: false)
            val canWriteAnyEntry = canWriteProd || canWriteQual

            Scaffold(
              modifier = Modifier.fillMaxSize(),
              bottomBar = {
                // Do not show bottom nav on TV dashboard so it displays truly fullscreen 16:9
                if (currentScreen != AppScreen.TV_DASHBOARD) {
                  NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                  ) {
                    NavigationBarItem(
                      selected = currentScreen == AppScreen.HOME,
                      onClick = { currentScreen = AppScreen.HOME },
                      icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                      label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                      colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NflRoyalBlue,
                        selectedTextColor = NflRoyalBlue,
                        indicatorColor = Color(0xFFE3F2FD)
                      ),
                      modifier = Modifier.testTag("nav_home")
                    )

                    if (canWriteMorningSetup) {
                      NavigationBarItem(
                        selected = currentScreen == AppScreen.SUPERVISOR_SETUP,
                        onClick = { currentScreen = AppScreen.SUPERVISOR_SETUP },
                        icon = { Icon(Icons.Default.WbSunny, contentDescription = "Morning Setup") },
                        label = { Text("Setup", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                          selectedIconColor = NflRoyalBlue,
                          selectedTextColor = NflRoyalBlue,
                          indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_setup")
                      )
                    }

                    if (canViewDeepDive) {
                      NavigationBarItem(
                        selected = currentScreen == AppScreen.LINE_DEEP_DIVE,
                        onClick = { currentScreen = AppScreen.LINE_DEEP_DIVE },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Deep Dive") },
                        label = { Text("Deep Dive", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                          selectedIconColor = NflRoyalBlue,
                          selectedTextColor = NflRoyalBlue,
                          indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_deep_dive")
                      )
                    }

                    if (canWriteNpt) {
                      NavigationBarItem(
                        selected = currentScreen == AppScreen.NPT_SUPERVISOR,
                        onClick = { currentScreen = AppScreen.NPT_SUPERVISOR },
                        icon = { Icon(Icons.Default.Build, contentDescription = "NPT") },
                        label = { Text("NPT", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                          selectedIconColor = NflRoyalBlue,
                          selectedTextColor = NflRoyalBlue,
                          indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_npt")
                      )
                    }

                    if (canWriteAnyEntry) {
                      NavigationBarItem(
                        selected = currentScreen == AppScreen.PRODUCTION_ENTRY || currentScreen == AppScreen.QUALITY_ENTRY,
                        onClick = {
                          currentScreen = when {
                            canWriteQual && !canWriteProd -> AppScreen.QUALITY_ENTRY
                            canWriteProd && !canWriteQual -> AppScreen.PRODUCTION_ENTRY
                            currentRole in setOf(UserRole.QUALITY_REPORTER, UserRole.QUALITY_MANAGER, UserRole.AUDITOR) -> AppScreen.QUALITY_ENTRY
                            else -> AppScreen.PRODUCTION_ENTRY
                          }
                        },
                        icon = { Icon(Icons.Default.EditNote, contentDescription = "Entry") },
                        label = { Text("Entry", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                          selectedIconColor = NflRoyalBlue,
                          selectedTextColor = NflRoyalBlue,
                          indicatorColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.testTag("nav_entry")
                      )
                    }
                  }
                }
              }
            ) { innerPadding ->
              Surface(
                color = Color(0xFFF1F5F9),
                contentColor = Color(0xFF0F172A),
                modifier = Modifier
                  .fillMaxSize()
                  .padding(if (currentScreen == AppScreen.TV_DASHBOARD) PaddingValues(0.dp) else innerPadding)
              ) {
                when (currentScreen) {
                  AppScreen.LOGIN -> LoginScreen(
                    repository = repository,
                    onLoginSuccess = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.HOME -> HomeScreen(
                    repository = repository,
                    onNavigateToProduction = { if (canWriteProd) currentScreen = AppScreen.PRODUCTION_ENTRY },
                    onNavigateToQuality = { if (canWriteQual) currentScreen = AppScreen.QUALITY_ENTRY },
                    onNavigateToNpt = { currentScreen = AppScreen.NPT_SUPERVISOR },
                    onNavigateToSetup = { if (canWriteMorningSetup) currentScreen = AppScreen.SUPERVISOR_SETUP },
                    onNavigateToTv = { currentScreen = AppScreen.TV_DASHBOARD },
                    onNavigateToHistory = { currentScreen = AppScreen.HISTORY_SYNC },
                    onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToLineDeepDive = { lineId ->
                      if (canViewDeepDive) {
                        selectedDeepDiveLineId = lineId
                        currentScreen = AppScreen.LINE_DEEP_DIVE
                      }
                    },
                    onNavigateToMonthlySetup = { if (canWriteMonthlySetup) currentScreen = AppScreen.MONTHLY_SETUP },
                    onNavigateToAdminUsers = { if (isAdminUser) currentScreen = AppScreen.ADMIN_USERS },
                    onNavigateToMonthlyBreakdown = { metric ->
                      selectedMonthlyMetric = metric
                      currentScreen = AppScreen.MONTHLY_BREAKDOWN
                    },
                    onNavigateToNotificationCenter = { currentScreen = AppScreen.NOTIFICATION_CENTER },
                    onNavigateToLogin = { currentScreen = AppScreen.LOGIN }
                  )
                  AppScreen.MONTHLY_BREAKDOWN -> MonthlyBreakdownScreen(
                    repository = repository,
                    initialMetric = selectedMonthlyMetric,
                    onNavigateBack = { currentScreen = AppScreen.HOME },
                    onNavigateToLine = { lineId ->
                      if (canViewDeepDive) {
                        selectedDeepDiveLineId = lineId
                        currentScreen = AppScreen.LINE_DEEP_DIVE
                      }
                    }
                  )
                  AppScreen.QUALITY_DASHBOARD -> QualityDashboardScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME },
                    onNavigateToLineDeepDive = { lineId ->
                      if (canViewDeepDive) {
                        selectedDeepDiveLineId = lineId
                        currentScreen = AppScreen.LINE_DEEP_DIVE
                      }
                    }
                  )
                  AppScreen.LINE_DEEP_DIVE -> LineDeepDiveScreen(
                    repository = repository,
                    lineId = selectedDeepDiveLineId,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.SUPERVISOR_SETUP -> SupervisorSetupScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.MONTHLY_SETUP -> MonthlySetupScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.WEEKLY_QUALITY -> WeeklyQualityEntryScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.ADMIN_USERS -> if (isAdminUser) {
                    AdminUsersScreen(
                      repository = repository,
                      onNavigateBack = { currentScreen = AppScreen.HOME }
                    )
                  } else {
                    LaunchedEffect(Unit) { currentScreen = AppScreen.HOME }
                  }
                  AppScreen.PRODUCTION_ENTRY -> ProductionEntryScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME },
                    onViewHistory = { currentScreen = AppScreen.HISTORY_SYNC },
                    onNavigateToQuality = { if (canWriteQual) currentScreen = AppScreen.QUALITY_ENTRY },
                    onNavigateToMonthlySetup = { if (canWriteMonthlySetup) currentScreen = AppScreen.MONTHLY_SETUP },
                    onNavigateToSetup = { if (canWriteMorningSetup) currentScreen = AppScreen.SUPERVISOR_SETUP }
                  )
                  AppScreen.QUALITY_ENTRY -> QualityEntryScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME },
                    onViewHistory = { currentScreen = AppScreen.HISTORY_SYNC },
                    onNavigateToProduction = { if (canWriteProd) currentScreen = AppScreen.PRODUCTION_ENTRY },
                    onNavigateToMonthlySetup = { if (canWriteMonthlySetup) currentScreen = AppScreen.MONTHLY_SETUP },
                    onNavigateToSetup = { if (canWriteMorningSetup) currentScreen = AppScreen.SUPERVISOR_SETUP },
                    onNavigateToWeeklyQuality = { if (repository.canWriteWeeklyQualityOrBiHourlyAudit(currentUser)) currentScreen = AppScreen.WEEKLY_QUALITY }
                  )
                  AppScreen.NPT_SUPERVISOR -> NptSupervisorScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.TV_DASHBOARD -> TvDashboardScreen(
                    repository = repository,
                    onNavigateBack = {
                      if (!isTelevisionDevice && !isTvDisplayAccount) {
                        currentScreen = AppScreen.HOME
                      }
                    }
                  )
                  AppScreen.HISTORY_SYNC -> if (!isTvDisplayAccount) {
                    HistorySyncScreen(
                      repository = repository,
                      onNavigateBack = { currentScreen = AppScreen.HOME }
                    )
                  } else {
                    LaunchedEffect(Unit) { currentScreen = AppScreen.TV_DASHBOARD }
                  }
                  AppScreen.SETTINGS -> if (!isTvDisplayAccount) {
                    SettingsScreen(
                      repository = repository,
                      onNavigateBack = { currentScreen = AppScreen.HOME }
                    )
                  } else {
                    LaunchedEffect(Unit) { currentScreen = AppScreen.TV_DASHBOARD }
                  }
                  AppScreen.NOTIFICATION_CENTER -> NotificationCenterScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onStop() {
    if (::repository.isInitialized) {
      repository.flushPendingPersistenceOnIo()
    }
    super.onStop()
  }

  override fun onDestroy() {
    if (::repository.isInitialized) {
      repository.shutdown()
    }
    super.onDestroy()
  }
}

@Composable
fun TvSetupScreen(repository: NflRepository) {
  val deviceName = remember { repository.getDeviceName() }
  val shortCode = remember { repository.getTvShortDeviceCode() }
  val deviceId = remember { repository.getDeviceId() }

  LaunchedEffect(Unit) {
    repository.ensureTvDisplaySetupRequest()
    // The first send can fail (no internet, access blocked). Keep trying while this screen is open.
    repeat(20) {
      repository.republishTvSetupRequest()
      kotlinx.coroutines.delay(30_000L)
    }
  }
  val cloudStatus by repository.tvSetupCloudStatus.collectAsStateWithLifecycle()

  // Read-only diagnostics so a stuck setup can be understood from the TV screen itself.
  val syncBanner by repository.syncStatusBanner.collectAsStateWithLifecycle()
  val deviceRequests by repository.deviceApprovalRequests.collectAsStateWithLifecycle()
  val myRequestStatus = remember(deviceRequests, deviceId) {
    deviceRequests.firstOrNull {
      it.userId.equals(NflRepository.TV_DISPLAY_ACCOUNT_ID, ignoreCase = true) && it.deviceId == deviceId
    }?.status ?: "NOT CREATED"
  }

  Surface(
    color = Color(0xFF061126),
    modifier = Modifier
      .fillMaxSize()
      .testTag("tv_setup_screen")
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E3A8A)),
        modifier = Modifier.widthIn(max = 520.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = "TV Setup",
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(44.dp)
          )
          Text(
            text = "NFL LIVE — TV Setup",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
          )
          Text(
            text = "Waiting for administrator approval",
            color = Color(0xFFFDE047),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("tv_setup_waiting_text")
          )
          Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "DEVICE CODE",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = shortCode,
                color = Color(0xFF4ADE80),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.testTag("tv_setup_device_code")
              )
              Text(
                text = "Device Name: $deviceName",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("tv_setup_device_name")
              )
              Text(
                text = "Account: Smart TV Display (tv_display) • ID: $deviceId",
                color = Color(0xFF64748B),
                fontSize = 11.sp
              )
            }
          }
          Text(
            text = "Approve this TV in User & Access Control → Device Requests to activate the live factory display. Approval is needed only once.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
          )
          Text(
            text = "Cloud: $cloudStatus",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier.testTag("tv_setup_cloud_status")
          )
          Text(
            text = "Request status on this TV: $myRequestStatus",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier.testTag("tv_setup_request_status")
          )
          if (!syncBanner.isNullOrBlank()) {
            Text(
              text = syncBanner.orEmpty(),
              color = Color(0xFFFBBF24),
              fontSize = 12.sp,
              modifier = Modifier.testTag("tv_setup_sync_banner")
            )
          }
        }
      }
    }
  }
}
