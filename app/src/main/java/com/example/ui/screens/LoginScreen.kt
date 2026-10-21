package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.UserRole
import com.example.model.EmployeeDepartment
import com.example.model.FloorScope

private val LoginBg = Color(0xFF061426)
private val LoginSurface = Color(0xFF0B2038)
private val LoginSurfaceAlt = Color(0xFF0E2945)
private val LoginBorder = Color(0xFF234565)
private val LoginText = Color(0xFFF3F7FC)
private val LoginMuted = Color(0xFFA8BCD2)
private val LoginCyan = Color(0xFF22B8F0)
private val LoginTeal = Color(0xFF20D3A2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
  repository: NflRepository,
  onLoginSuccess: () -> Unit
) {
  val users by repository.users.collectAsState()
  var usernameInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var infoMessage by remember { mutableStateOf<String?>(null) }
  var pendingApprovalStatus by remember { mutableStateOf< com.example.model.AuthStatus?>(null) }
  var showRegisterDialog by remember { mutableStateOf(false) }
  var registerName by remember { mutableStateOf("") }
  var registerPassword by remember { mutableStateOf("") }
  var registerDepartment by remember { mutableStateOf(EmployeeDepartment.PRODUCTION) }
  var registerDesignation by remember { mutableStateOf("Supervisor") }
  var registerFloor by remember { mutableStateOf(FloorScope.SEW_1) }
  var registerLine by remember { mutableStateOf("All lines on floor") }
  var registerBusy by remember { mutableStateOf(false) }

  val designationsByDepartment = mapOf(
    EmployeeDepartment.PRODUCTION to listOf("Reporter", "Supervisor", "In-charge", "APM", "PM / Production Manager", "Production Head"),
    EmployeeDepartment.QUALITY to listOf("QI", "Controller", "Officer", "Assistant Manager", "Manager", "Quality Head"),
    EmployeeDepartment.IE to listOf("IE Officer", "IE Manager"),
    EmployeeDepartment.FINISHING to listOf("Supervisor", "In-charge", "Manager"),
    EmployeeDepartment.MANAGEMENT to listOf("Factory Management")
  )
  val registrationLineOptions = remember(registerFloor) {
    val linesForScope = if (registerFloor == FloorScope.FINISHING) {
      listOf("Iron 1", "Iron 2", "Iron 3", "Packing 1", "Packing 2", "Packing 3")
    } else {
      repository.allLinesMaster
        .filter { it.floor.equals(registerFloor.apiValue, ignoreCase = true) }
        .map { it.id }
    }
    listOf("All lines on floor") + linesForScope.distinct()
  }
  
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(Color(0xFF061426), Color(0xFF092543), Color(0xFF04101F))))
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Spacer(Modifier.height(8.dp))

      // Official company logo: reuse the existing vector asset unchanged.
      Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, LoginTeal.copy(alpha = 0.65f)),
        modifier = Modifier.size(78.dp)
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(7.dp)) {
          Icon(
            painter = painterResource(com.example.R.drawable.ic_nfl_logo),
            contentDescription = "Northern Fashions Ltd. company logo",
            modifier = Modifier.fillMaxSize(),
            tint = Color.Unspecified
          )
        }
      }
      Spacer(Modifier.height(14.dp))
      Text("NFL LIVE", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = LoginText)
      Spacer(Modifier.height(3.dp))
      Text("NORTHERN FASHIONS LTD.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = LoginMuted)
      Spacer(Modifier.height(14.dp))
      Surface(
        color = Color(0xFF063A3A).copy(alpha = 0.75f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, LoginTeal.copy(alpha = 0.6f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.Lock, contentDescription = null, tint = LoginTeal, modifier = Modifier.size(15.dp))
          Text("SECURE WORKSPACE", color = LoginTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
      }
      Spacer(Modifier.height(18.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = LoginSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, LoginBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Text("Welcome back", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = LoginText)
          Text("Sign in with your authorized Employee ID & Password", fontSize = 14.sp, color = LoginMuted)
          Spacer(Modifier.height(2.dp))

          OutlinedTextField(
            value = usernameInput,
            onValueChange = {
              usernameInput = it
              errorMessage = null
              infoMessage = null
            },
            label = { Text("Employee ID", color = LoginMuted) },
            placeholder = { Text("e.g. admin, supervisor, or employee card #", color = LoginMuted.copy(alpha = 0.5f), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = LoginCyan) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_id_input"),
            shape = RoundedCornerShape(14.dp),
            colors = loginFieldColors()
          )
          OutlinedTextField(
            value = passwordInput,
            onValueChange = {
              passwordInput = it
              errorMessage = null
            },
            label = { Text("Password", color = LoginMuted) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = LoginCyan) },
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = if (passwordVisible) "Hide password" else "Show password",
                  tint = LoginMuted
                )
              }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
            shape = RoundedCornerShape(14.dp),
            colors = loginFieldColors()
          )

          AnimatedVisibility(visible = errorMessage != null) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF4A1F2B), modifier = Modifier.fillMaxWidth()) {
              Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFFA3AE), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(errorMessage ?: "", color = Color(0xFFFFC6CD), fontSize = 12.sp)
              }
            }
          }

          AnimatedVisibility(visible = infoMessage != null) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF063A3A), modifier = Modifier.fillMaxWidth()) {
              Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = LoginTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(infoMessage ?: "", color = LoginText, fontSize = 12.sp)
              }
            }
          }

          AnimatedVisibility(
            visible = pendingApprovalStatus == com.example.model.AuthStatus.DEVICE_APPROVAL_REQUIRED ||
                pendingApprovalStatus == com.example.model.AuthStatus.DEVICE_LIMIT_REACHED ||
                pendingApprovalStatus == com.example.model.AuthStatus.ACCOUNT_PENDING_APPROVAL
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF0F2942),
              border = BorderStroke(1.dp, LoginCyan.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Admin approval required", color = LoginCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                  "This account or device is waiting for approval. No Gmail OTP is needed. Ask Admin to review the request in the NFL LIVE Admin Panel, then check again.",
                  color = LoginMuted, fontSize = 11.sp
                )
                OutlinedButton(
                  onClick = {
                    if (usernameInput.isBlank() || passwordInput.isBlank()) {
                      errorMessage = "Enter your Employee ID and password first."
                    } else {
                      val result = repository.authenticateUser(usernameInput, passwordInput)
                      if (result.status == com.example.model.AuthStatus.SUCCESS) {
                        pendingApprovalStatus = null
                        errorMessage = null
                        onLoginSuccess()
                      } else {
                        pendingApprovalStatus = result.status.takeIf {
                          it == com.example.model.AuthStatus.DEVICE_APPROVAL_REQUIRED ||
                          it == com.example.model.AuthStatus.ACCOUNT_PENDING_APPROVAL ||
                          it == com.example.model.AuthStatus.DEVICE_LIMIT_REACHED
                        }
                        errorMessage = result.message
                      }
                    }
                  },
                  modifier = Modifier.fillMaxWidth(),
                  border = BorderStroke(1.dp, LoginCyan)
                ) { Text("Check approval status", color = LoginCyan) }
              }
            }
          }

          Button(
            onClick = {
              if (usernameInput.isBlank() || passwordInput.isBlank()) {
                errorMessage = "Please enter both Employee ID and password."
                return@Button
              }
              val authResult = repository.authenticateUser(usernameInput, passwordInput)
              when (authResult.status) {
                com.example.model.AuthStatus.SUCCESS -> {
                  pendingApprovalStatus = null
                  onLoginSuccess()
                }
                com.example.model.AuthStatus.DEVICE_LIMIT_REACHED,
                com.example.model.AuthStatus.DEVICE_APPROVAL_REQUIRED,
                com.example.model.AuthStatus.ACCOUNT_PENDING_APPROVAL -> {
                  pendingApprovalStatus = authResult.status
                  errorMessage = authResult.message
                }
                else -> {
                  pendingApprovalStatus = null
                  errorMessage = authResult.message
                }
              }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("login_submit_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LoginCyan, contentColor = Color(0xFF041526))
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Sign in securely", fontWeight = FontWeight.Bold, fontSize = 15.sp)
          }

          TextButton(
            onClick = {
              errorMessage = "Use Register for a new account, or ask Admin to approve your existing account/device."
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
          ) {
            Text("Need help signing in?", color = LoginCyan, fontSize = 12.sp)
          }

          // Single developer attribution placed directly below Need Help
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 2.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "This app is developed by Shamim",
              color = Color(0xFF67E8F9),
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
              text = "IPE & Planning",
              color = LoginMuted,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium
            )
          }

          HorizontalDivider(color = LoginBorder)
          OutlinedButton(
            onClick = { showRegisterDialog = true; errorMessage = null },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, LoginTeal),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LoginTeal)
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Register new employee account")
          }
        }
      }

      Spacer(Modifier.height(14.dp))
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = LoginSurface.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, LoginBorder)
      ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick access", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LoginText)
            Spacer(Modifier.weight(1f))
            Text("ROLE SHORTCUTS", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = LoginMuted)
          }
          Text("Tap role to fill ID (enter password manually):", fontSize = 10.sp, color = LoginMuted)

          val roleRows = users.chunked(3)
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            roleRows.forEach { rowUsers ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                rowUsers.forEach { user ->
                  val isSelectedRole = usernameInput.equals(user.id, ignoreCase = true)
                  val shortRoleLabel = when (user.role) {
                    UserRole.ADMIN -> "Admin"
                    UserRole.SUPERVISOR -> "Supervisor"
                    UserRole.PRODUCTION_REPORTER -> "Production"
                    UserRole.QUALITY_REPORTER -> "Quality QC"
                    UserRole.AUDITOR -> "Auditor"
                    UserRole.PRODUCTION_MANAGER -> "Prod Mgr"
                    UserRole.QUALITY_MANAGER -> "Quality Mgr"
                    UserRole.MECHANIC -> "Mechanic"
                    UserRole.IE -> "IE"
                    UserRole.INPUT_MATERIAL -> "Input"
                    UserRole.MANAGEMENT_VIEW -> "TV Monitor"
                  }
                  Surface(
                    onClick = {
                      usernameInput = user.id
                      passwordInput = "" // Never auto-fill or expose passwords
                      errorMessage = null
                      infoMessage = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelectedRole) LoginCyan.copy(alpha = 0.18f) else LoginSurfaceAlt,
                    border = BorderStroke(1.dp, if (isSelectedRole) LoginCyan else LoginBorder),
                    modifier = Modifier.weight(1f)
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.Center
                    ) {
                      Icon(
                        imageVector = when (user.role) {
                          UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                          UserRole.SUPERVISOR -> Icons.Default.Groups
                          UserRole.QUALITY_REPORTER, UserRole.QUALITY_MANAGER, UserRole.AUDITOR -> Icons.Default.Verified
                          UserRole.MANAGEMENT_VIEW -> Icons.Default.Tv
                          else -> Icons.Default.Person
                        },
                        contentDescription = null,
                        tint = if (isSelectedRole) LoginTeal else LoginCyan,
                        modifier = Modifier.size(13.dp)
                      )
                      Spacer(Modifier.width(4.dp))
                      Text(
                        text = shortRoleLabel,
                        color = LoginText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                      )
                    }
                  }
                }
                repeat(3 - rowUsers.size) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
      Spacer(Modifier.height(22.dp))
      Text("NORTHERN FASHIONS LTD.", color = LoginMuted.copy(alpha = 0.85f), fontSize = 10.sp, letterSpacing = 1.5.sp)
      Spacer(Modifier.height(8.dp))
    }
  }

  if (showRegisterDialog) {
    AlertDialog(
      onDismissRequest = { if (!registerBusy) showRegisterDialog = false },
      containerColor = LoginSurface,
      titleContentColor = LoginText,
      textContentColor = LoginMuted,
      title = { Text("Register employee account", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            "Create an account request. You can sign in only after Admin approves it. Use a unique Employee ID and a password of at least 8 characters.",
            fontSize = 12.sp
          )
          OutlinedTextField(
            value = usernameInput,
            onValueChange = { usernameInput = it.trimStart() },
            label = { Text("Employee ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = loginFieldColors()
          )
          OutlinedTextField(
            value = registerName,
            onValueChange = { registerName = it },
            label = { Text("Full name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = loginFieldColors()
          )
          RegistrationDropdown(
            label = "Department",
            selected = registerDepartment.displayName,
            options = EmployeeDepartment.entries.map { it.displayName },
            onSelected = { selected ->
              registerDepartment = EmployeeDepartment.entries.first { it.displayName == selected }
              registerDesignation = designationsByDepartment[registerDepartment]?.firstOrNull() ?: "Supervisor"
              if (registerDepartment == EmployeeDepartment.FINISHING) registerFloor = FloorScope.FINISHING
              else if (registerFloor == FloorScope.FINISHING) registerFloor = FloorScope.SEW_1
              registerLine = "All lines on floor"
            }
          )
          RegistrationDropdown(
            label = "Designation",
            selected = registerDesignation,
            options = designationsByDepartment[registerDepartment] ?: listOf("Other"),
            onSelected = { registerDesignation = it }
          )
          RegistrationDropdown(
            label = "Floor / Section",
            selected = if (registerDesignation.contains("Head", ignoreCase = true)) "All Floor (Admin approved)" else registerFloor.displayName,
            options = if (registerDesignation.contains("Head", ignoreCase = true)) listOf("All Floor (Admin approved)") else
              if (registerDepartment == EmployeeDepartment.FINISHING) listOf(FloorScope.FINISHING.displayName)
              else listOf(FloorScope.SEW_1.displayName, FloorScope.SEW_2.displayName, FloorScope.SEW_3.displayName),
            onSelected = { selected ->
              registerFloor = FloorScope.entries.firstOrNull { it.displayName == selected } ?: registerFloor
              registerLine = "All lines on floor"
            }
          )
          if (registerDepartment != EmployeeDepartment.MANAGEMENT &&
              !registerDesignation.contains("Head", ignoreCase = true)) {
            RegistrationDropdown(
              label = "Line coverage",
              selected = registerLine,
              options = registrationLineOptions,
              onSelected = { registerLine = it }
            )
          } else {
            Text(
              "Floor/line coverage will be confirmed by Admin. Department heads are assigned All Floors within their own department.",
              fontSize = 11.sp,
              color = LoginMuted
            )
          }
          OutlinedTextField(
            value = registerPassword,
            onValueChange = { registerPassword = it },
            label = { Text("Create password (8+ characters)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = loginFieldColors()
          )
        }
      },
      confirmButton = {
        Button(
          enabled = !registerBusy,
          onClick = {
            registerBusy = true
            repository.registerEmployeeAccount(
              usernameInput,
              registerName,
              registerPassword,
              registerDepartment,
              registerDesignation,
              if (registerDesignation.contains("Head", ignoreCase = true)) FloorScope.ALL else registerFloor,
              if (registerLine == "All lines on floor" || registerDesignation.contains("Head", ignoreCase = true)) "All" else registerLine
            ) { result ->
              registerBusy = false
              if (result.success) {
                showRegisterDialog = false
                registerPassword = ""
                registerName = ""
                registerDepartment = EmployeeDepartment.PRODUCTION
                registerDesignation = "Supervisor"
                registerFloor = FloorScope.SEW_1
                registerLine = "All lines on floor"
                infoMessage = result.message
                errorMessage = null
                pendingApprovalStatus = com.example.model.AuthStatus.ACCOUNT_PENDING_APPROVAL
              } else {
                errorMessage = result.message
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = LoginTeal, contentColor = LoginBg)
        ) { Text(if (registerBusy) "Submitting..." else "Submit request", fontWeight = FontWeight.Bold) }
      },
      dismissButton = {
        TextButton(enabled = !registerBusy, onClick = { showRegisterDialog = false }) {
          Text("Cancel", color = LoginMuted)
        }
      }
    )
  }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistrationDropdown(
  label: String,
  selected: String,
  options: List<String>,
  onSelected: (String) -> Unit
) {
  var expanded by remember(label) { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, color = LoginMuted, fontSize = 12.sp)
    Box {
      OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LoginBorder),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
      ) {
        Text(selected, color = LoginText, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose $label", tint = LoginCyan)
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
          DropdownMenuItem(
            text = { Text(option) },
            onClick = {
              expanded = false
              onSelected(option)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = LoginText,
  unfocusedTextColor = LoginText,
  focusedBorderColor = LoginCyan,
  unfocusedBorderColor = LoginBorder,
  cursorColor = LoginCyan,
  focusedContainerColor = LoginBg.copy(alpha = 0.35f),
  unfocusedContainerColor = LoginBg.copy(alpha = 0.35f),
  focusedLabelColor = LoginCyan,
  unfocusedLabelColor = LoginMuted
)
