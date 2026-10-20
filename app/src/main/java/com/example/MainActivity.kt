package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.model.UserRole
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
  ADMIN_USERS,
  LINE_DEEP_DIVE,
  TV_DASHBOARD,
  HISTORY_SYNC,
  SETTINGS,
  MONTHLY_BREAKDOWN,
  NOTIFICATION_CENTER
}

class MainActivity : ComponentActivity() {

  private lateinit var repository: NflRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    repository = NflRepository(applicationContext)

    setContent {
      MyApplicationTheme {
        val currentUser by repository.currentUser.collectAsState()
        val currentRole by repository.currentRole.collectAsState()
        val currentUserName by repository.currentUserName.collectAsState()
        val incidents by repository.incidents.collectAsState()

        val isTelevisionDevice = remember {
          packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
              (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
              android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        }

        var currentScreen by remember {
          mutableStateOf(
            when {
              currentUser == null -> AppScreen.LOGIN
              isTelevisionDevice || currentUser?.id == "management" -> AppScreen.TV_DASHBOARD
              else -> AppScreen.HOME
            }
          )
        }
        var selectedDeepDiveLineId by remember { mutableStateOf("K1") }
        var selectedMonthlyMetric by remember { mutableStateOf(com.example.model.MonthlyMetricType.EFFICIENCY) }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Handle Back Navigation cleanly
        if (currentScreen != AppScreen.HOME && currentScreen != AppScreen.LOGIN) {
          BackHandler {
            if (drawerState.isOpen) {
              scope.launch { drawerState.close() }
            } else {
              currentScreen = AppScreen.HOME
            }
          }
        }

        // If user logged out or is null, gate into LoginScreen
        if (currentUser == null || currentScreen == AppScreen.LOGIN) {
          LoginScreen(
            repository = repository,
            onLoginSuccess = {
              val loggedIn = repository.currentUser.value
              currentScreen = if (isTelevisionDevice || loggedIn?.id == "management") {
                AppScreen.TV_DASHBOARD
              } else {
                AppScreen.HOME
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

                  val canWriteMorningSetup = isAdminUser || (currentUser?.let { it.canMorningTargetWrite && it.role !in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER, UserRole.IE, UserRole.QUALITY_REPORTER) } ?: false)
                  val canWriteMonthlySetup = isAdminUser || (currentUser?.let { it.canMonthlyTargetWrite && it.role == UserRole.IE } ?: false)
                  val canWriteNpt = isAdminUser || (currentUser?.let { it.canNptWrite && it.role !in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER, UserRole.IE) } ?: false)
                  val canWriteProd = isAdminUser || (currentUser?.let { (it.canProductionWrite || it.canFinishingWrite) && it.role in setOf(UserRole.SUPERVISOR, UserRole.PRODUCTION_REPORTER) } ?: false)
                  val canWriteQual = isAdminUser || (currentUser?.let { (it.canQualityWrite || it.canOqlWrite) && it.role in setOf(UserRole.QUALITY_REPORTER, UserRole.AUDITOR) } ?: false)
                  val canWriteAnyEntry = canWriteProd || canWriteQual

                  // 2. Navigation Items
                  Text(
                    text = "FACTORY MODULES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                  )

                  NavigationDrawerItem(
                    label = { Text("🏠 Home (Live Matrix & Gauges)") },
                    selected = currentScreen == AppScreen.HOME,
                    onClick = {
                      currentScreen = AppScreen.HOME
                      scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                      unselectedContainerColor = Color.Transparent,
                      selectedContainerColor = Color(0xFF1E293B),
                      selectedTextColor = Color(0xFF38BDF8),
                      unselectedTextColor = Color(0xFFE2E8F0)
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
                        Text("🔔 Smart Factory Alerts")
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
                      unselectedTextColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                  )

                  if (canWriteMorningSetup) {
                    NavigationDrawerItem(
                      label = { Text("🌅 Morning Line Setup") },
                      selected = currentScreen == AppScreen.SUPERVISOR_SETUP,
                      onClick = {
                        currentScreen = AppScreen.SUPERVISOR_SETUP
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canViewDeepDive) {
                    NavigationDrawerItem(
                      label = { Text("📊 Line Deep Dive Performance") },
                      selected = currentScreen == AppScreen.LINE_DEEP_DIVE,
                      onClick = {
                        currentScreen = AppScreen.LINE_DEEP_DIVE
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canWriteNpt) {
                    NavigationDrawerItem(
                      label = { Text("⚙️ NPT & Maintenance Tracking") },
                      selected = currentScreen == AppScreen.NPT_SUPERVISOR,
                      onClick = {
                        currentScreen = AppScreen.NPT_SUPERVISOR
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canWriteProd) {
                    NavigationDrawerItem(
                      label = { Text("🧵 Production Data Entry") },
                      selected = currentScreen == AppScreen.PRODUCTION_ENTRY,
                      onClick = {
                        currentScreen = AppScreen.PRODUCTION_ENTRY
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canWriteQual) {
                    NavigationDrawerItem(
                      label = { Text("🔍 Quality / Defect Entry") },
                      selected = currentScreen == AppScreen.QUALITY_ENTRY,
                      onClick = {
                        currentScreen = AppScreen.QUALITY_ENTRY
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canWriteMonthlySetup) {
                    NavigationDrawerItem(
                      label = { Text("📅 Monthly Data Entry (মান্থলি ডাটা এন্ট্রি)") },
                      selected = currentScreen == AppScreen.MONTHLY_SETUP,
                      onClick = {
                        currentScreen = AppScreen.MONTHLY_SETUP
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  // Admin & Management section
                  if (isAdminUser || canViewTv || canViewAudit || canViewMonthly) {
                    Text(
                      text = "MANAGEMENT & SECURITY",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF64748B),
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                  }

                  if (isAdminUser) {
                    NavigationDrawerItem(
                      label = { Text("👥 User & Access Control") },
                      selected = currentScreen == AppScreen.ADMIN_USERS,
                      onClick = {
                        currentScreen = AppScreen.ADMIN_USERS
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canViewMonthly) {
                    NavigationDrawerItem(
                      label = { Text("📈 Monthly Breakdown Reports") },
                      selected = currentScreen == AppScreen.MONTHLY_BREAKDOWN,
                      onClick = {
                        currentScreen = AppScreen.MONTHLY_BREAKDOWN
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canViewTv) {
                    NavigationDrawerItem(
                      label = { Text("📺 Smart TV Dashboard (16:9)") },
                      selected = currentScreen == AppScreen.TV_DASHBOARD,
                      onClick = {
                        currentScreen = AppScreen.TV_DASHBOARD
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
                      ),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                  }

                  if (canViewAudit) {
                    NavigationDrawerItem(
                      label = { Text("📜 History & Audit Logs") },
                      selected = currentScreen == AppScreen.HISTORY_SYNC,
                      onClick = {
                        currentScreen = AppScreen.HISTORY_SYNC
                        scope.launch { drawerState.close() }
                      },
                      colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFFE2E8F0)
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
          ) {
            val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == UserRole.ADMIN
            val canViewDeepDive = isAdminUser || (currentUser?.canViewLineDeepDive ?: false)
            val canWriteMorningSetup = isAdminUser || (currentUser?.let { it.canMorningTargetWrite && it.role !in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER, UserRole.IE, UserRole.QUALITY_REPORTER) } ?: false)
            val canWriteMonthlySetup = isAdminUser || (currentUser?.let { it.canMonthlyTargetWrite && it.role == UserRole.IE } ?: false)
            val canWriteNpt = isAdminUser || (currentUser?.let { it.canNptWrite && it.role !in setOf(UserRole.MANAGEMENT_VIEW, UserRole.PRODUCTION_MANAGER, UserRole.QUALITY_MANAGER, UserRole.IE) } ?: false)
            val canWriteProd = isAdminUser || (currentUser?.let { (it.canProductionWrite || it.canFinishingWrite) && it.role in setOf(UserRole.SUPERVISOR, UserRole.PRODUCTION_REPORTER) } ?: false)
            val canWriteQual = isAdminUser || (currentUser?.let { (it.canQualityWrite || it.canOqlWrite) && it.role in setOf(UserRole.QUALITY_REPORTER, UserRole.AUDITOR) } ?: false)
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
                    onNavigateToNpt = { if (canWriteNpt) currentScreen = AppScreen.NPT_SUPERVISOR },
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
                    onNavigateToSetup = { if (canWriteMorningSetup) currentScreen = AppScreen.SUPERVISOR_SETUP }
                  )
                  AppScreen.NPT_SUPERVISOR -> NptSupervisorScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.TV_DASHBOARD -> TvDashboardScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.HISTORY_SYNC -> HistorySyncScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
                  AppScreen.SETTINGS -> SettingsScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.HOME }
                  )
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

  override fun onDestroy() {
    if (::repository.isInitialized) {
      repository.shutdown()
    }
    super.onDestroy()
  }
}
