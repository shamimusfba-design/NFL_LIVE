package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.NflRepository
import com.example.model.AuthResult
import com.example.model.AuthStatus
import com.example.model.DesignationPolicy
import com.example.model.EmployeeDesignation
import com.example.model.FloorScope
import com.example.model.INITIAL_ADMIN_EMPLOYEE_ID
import com.example.model.MAX_APPROVED_DEVICES_PER_USER

private enum class AuthScreenMode {
  LOGIN,
  REGISTER,
  BOOTSTRAP_ADMIN,
  FORCE_PASSWORD_CHANGE
}

@Composable
private fun nflAuthTextFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = Color(0xFF0F172A),
  unfocusedTextColor = Color(0xFF0F172A),
  disabledTextColor = Color(0xFF64748B),
  focusedContainerColor = Color(0xFFF8FAFC),
  unfocusedContainerColor = Color(0xFFF8FAFC),
  disabledContainerColor = Color(0xFFF1F5F9),
  focusedBorderColor = Color(0xFF0755F5),
  unfocusedBorderColor = Color(0xFFDCE5F2),
  disabledBorderColor = Color(0xFFE2E8F0),
  focusedLabelColor = Color(0xFF0755F5),
  unfocusedLabelColor = Color(0xFF64748B),
  disabledLabelColor = Color(0xFF94A3B8),
  focusedPlaceholderColor = Color(0xFF94A3B8),
  unfocusedPlaceholderColor = Color(0xFF94A3B8),
  focusedLeadingIconColor = Color(0xFF64748B),
  unfocusedLeadingIconColor = Color(0xFF64748B),
  disabledLeadingIconColor = Color(0xFF94A3B8),
  focusedTrailingIconColor = Color(0xFF64748B),
  unfocusedTrailingIconColor = Color(0xFF64748B),
  cursorColor = Color(0xFF0755F5)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
  repository: NflRepository,
  onLoginSuccess: () -> Unit
) {
  val users by repository.users.collectAsState()
  val isFirstAdminProvisioned = remember(users) { repository.isFirstAdminProvisioned() }

  var screenMode by remember { mutableStateOf(AuthScreenMode.LOGIN) }

  // Login state
  var employeeId by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var infoBanner by remember { mutableStateOf<String?>(null) }
  var lastAuthResult by remember { mutableStateOf<AuthResult?>(null) }

  // Device replacement / OTP verification state
  var otpCode by remember { mutableStateOf("") }
  var otpStatusMessage by remember { mutableStateOf<String?>(null) }
  var otpStatusSuccess by remember { mutableStateOf(false) }
  var isSendingOtp by remember { mutableStateOf(false) }

  // Employee Registration state
  var regFullName by remember { mutableStateOf("") }
  var regEmployeeId by remember { mutableStateOf("") }
  var regDesignation by remember { mutableStateOf(EmployeeDesignation.PRODUCTION_SUPERVISOR) }
  var regDesignationExpanded by remember { mutableStateOf(false) }
  var regPhysicalFloor by remember { mutableStateOf(FloorScope.SEW_1) }
  var regPassword by remember { mutableStateOf("") }
  var regConfirmPassword by remember { mutableStateOf("") }
  var showRegPassword by remember { mutableStateOf(false) }

  // First Admin (1015198) Bootstrap state
  var bootstrapAdminName by remember { mutableStateOf("Initial Administrator") }
  var bootstrapPassword by remember { mutableStateOf("") }
  var bootstrapConfirmPassword by remember { mutableStateOf("") }
  var showBootstrapPassword by remember { mutableStateOf(false) }

  // Mandatory Password Change state
  var currentTempPassword by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmNewPassword by remember { mutableStateOf("") }
  var showNewPassword by remember { mutableStateOf(false) }

  val deviceId = remember { repository.getDeviceId() }
  val deviceName = remember { repository.getDeviceName() }
  val fieldColors = nflAuthTextFieldColors()

  BackHandler(enabled = screenMode != AuthScreenMode.LOGIN) {
    errorMsg = null
    infoBanner = null
    screenMode = AuthScreenMode.LOGIN
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF061B43))
  ) {
    // Realistic Garment Sewing Floor Factory Header with Deep Navy Dissolve Gradient
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(310.dp)
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_factory_header),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color(0x33061B43),
                Color(0x88061B43),
                Color(0xEE061B43),
                Color(0xFF061B43)
              )
            )
          )
      )
    }

    // Scrollable Foreground Content
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      // Circular Floating White Badge with Original Company Logo
      Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.size(86.dp)
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.fillMaxSize()
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_nfl_logo),
            contentDescription = "NFL LIVE Logo",
            modifier = Modifier.size(60.dp),
            contentScale = ContentScale.Fit
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Prominent Application Name: NFL in white + LIVE in vibrant blue
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(
          text = "NFL",
          fontSize = 34.sp,
          fontWeight = FontWeight.Black,
          color = Color.White,
          letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "LIVE",
          fontSize = 34.sp,
          fontWeight = FontWeight.Black,
          color = Color(0xFF1677FF),
          letterSpacing = 1.2.sp
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Floating White Login Card
      Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 440.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Sub-screen header (Register, Bootstrap, Force Password Change)
          if (screenMode != AuthScreenMode.LOGIN) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = {
                  errorMsg = null
                  infoBanner = null
                  screenMode = AuthScreenMode.LOGIN
                }
              ) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back to Sign In",
                  tint = Color(0xFF0F172A)
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Column {
                Text(
                  text = when (screenMode) {
                    AuthScreenMode.REGISTER -> "Employee Registration"
                    AuthScreenMode.BOOTSTRAP_ADMIN -> "First Administrator Setup"
                    AuthScreenMode.FORCE_PASSWORD_CHANGE -> "Mandatory Password Update"
                    else -> ""
                  },
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = when (screenMode) {
                    AuthScreenMode.REGISTER -> "Create your official account for approval"
                    AuthScreenMode.BOOTSTRAP_ADMIN -> "Initialize primary administrator ($INITIAL_ADMIN_EMPLOYEE_ID)"
                    AuthScreenMode.FORCE_PASSWORD_CHANGE -> "Security compliance requirement"
                    else -> ""
                  },
                  fontSize = 11.sp,
                  color = Color(0xFF64748B)
                )
              }
            }
          }

            // Info / Success Banner
            AnimatedVisibility(visible = infoBanner != null) {
              Surface(
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = infoBanner.orEmpty(),
                    color = Color(0xFF14532D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }

            // Error Banner
            AnimatedVisibility(visible = errorMsg != null) {
              Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = errorMsg.orEmpty(),
                    color = Color(0xFF7F1D1D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }

            when (screenMode) {
              AuthScreenMode.LOGIN -> {
                // Compact First Admin Setup Banner (only shown if 1015198 is not yet provisioned)
                if (!isFirstAdminProvisioned) {
                  Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(20.dp)
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "First Admin Setup (ID: $INITIAL_ADMIN_EMPLOYEE_ID)",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.ExtraBold,
                          color = Color(0xFF92400E),
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        Text(
                          text = "Initialize primary administrator account",
                          fontSize = 10.sp,
                          color = Color(0xFF78350F),
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                      FilledTonalButton(
                        onClick = {
                          errorMsg = null
                          infoBanner = null
                          screenMode = AuthScreenMode.BOOTSTRAP_ADMIN
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                          containerColor = Color(0xFFFDE68A),
                          contentColor = Color(0xFF78350F)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                          .height(32.dp)
                          .testTag("open_bootstrap_admin_button")
                      ) {
                        Icon(
                          Icons.Default.Security,
                          contentDescription = null,
                          modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = "Setup",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Black,
                          maxLines = 1,
                          softWrap = false
                        )
                      }
                    }
                  }
                }

                OutlinedTextField(
                  value = employeeId,
                  onValueChange = {
                    employeeId = it
                    errorMsg = null
                    lastAuthResult = null
                  },
                  label = { Text("Employee ID") },
                  placeholder = { Text("Enter Employee ID (e.g. $INITIAL_ADMIN_EMPLOYEE_ID)") },
                  leadingIcon = { Icon(Icons.Default.Badge, contentDescription = "Employee ID") },
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_employee_id_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = password,
                  onValueChange = {
                    password = it
                    errorMsg = null
                  },
                  label = { Text("Password") },
                  placeholder = { Text("Enter your account password") },
                  leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                  trailingIcon = {
                    IconButton(
                      onClick = { showPassword = !showPassword },
                      modifier = Modifier.testTag("login_password_toggle")
                    ) {
                      Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                      )
                    }
                  },
                  visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                // Device Replacement / Pending Verification Panel
                val requiresVerification = lastAuthResult?.status in setOf(
                  AuthStatus.DEVICE_LIMIT_REACHED,
                  AuthStatus.DEVICE_APPROVAL_REQUIRED
                )

                AnimatedVisibility(visible = requiresVerification) {
                  Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(
                      modifier = Modifier.padding(12.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Icon(
                          Icons.Default.PhonelinkLock,
                          contentDescription = null,
                          tint = Color(0xFFD97706),
                          modifier = Modifier.size(18.dp)
                        )
                        Text(
                          text = "Active Device Conflict — Replacement Required",
                          fontSize = 12.sp,
                          fontWeight = FontWeight.Black,
                          color = Color(0xFF92400E)
                        )
                      }
                      Text(
                        text = "Each Employee ID is restricted to 1 authorized active device. Request a Device Replacement or enter the 6-digit Admin verification code.",
                        fontSize = 11.sp,
                        color = Color(0xFF78350F)
                      )

                      OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it.filter { ch -> ch.isDigit() } },
                        label = { Text("6-Digit Admin Verification Code") },
                        placeholder = { Text("e.g. 482910") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                          .fillMaxWidth()
                          .testTag("device_otp_input"),
                        shape = RoundedCornerShape(10.dp)
                      )

                      if (otpStatusMessage != null) {
                        Text(
                          text = otpStatusMessage!!,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = if (otpStatusSuccess) Color(0xFF166534) else Color(0xFFB91C1C)
                        )
                      }

                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        OutlinedButton(
                          onClick = {
                            val targetId = lastAuthResult?.user?.id ?: employeeId.trim()
                            isSendingOtp = true
                            repository.requestGmailVerificationCode(
                              userId = targetId,
                              requestType = "DEVICE_REPLACEMENT"
                            ) { res ->
                              isSendingOtp = false
                              otpStatusSuccess = res.success
                              otpStatusMessage = res.message
                            }
                          },
                          enabled = !isSendingOtp,
                          modifier = Modifier.weight(1f),
                          shape = RoundedCornerShape(8.dp)
                        ) {
                          Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                          )
                          Spacer(modifier = Modifier.width(4.dp))
                          Text(
                            if (isSendingOtp) "Sending..." else "Request Code",
                            fontSize = 11.sp,
                            maxLines = 1
                          )
                        }

                        Button(
                          onClick = {
                            val targetId = lastAuthResult?.user?.id ?: employeeId.trim()
                            val verifyRes = repository.verifyGmailApprovalCode(targetId, otpCode)
                            otpStatusSuccess = verifyRes.success
                            otpStatusMessage = verifyRes.message
                            if (verifyRes.success) {
                              val retryAuth = repository.authenticateUser(employeeId, password)
                              if (retryAuth.status == AuthStatus.SUCCESS) {
                                onLoginSuccess()
                              } else if (retryAuth.status == AuthStatus.PASSWORD_CHANGE_REQUIRED) {
                                currentTempPassword = password
                                screenMode = AuthScreenMode.FORCE_PASSWORD_CHANGE
                              }
                            }
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                          modifier = Modifier.weight(1f),
                          shape = RoundedCornerShape(8.dp)
                        ) {
                          Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text(
                            "Verify & Bind",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                          )
                        }
                      }
                    }
                  }
                }

                Button(
                  onClick = {
                    isLoading = true
                    errorMsg = null
                    infoBanner = null
                    val authResult = repository.authenticateUser(employeeId, password)
                    isLoading = false
                    lastAuthResult = authResult
                    when (authResult.status) {
                      AuthStatus.SUCCESS -> onLoginSuccess()
                      AuthStatus.PASSWORD_CHANGE_REQUIRED -> {
                        currentTempPassword = password
                        infoBanner = "First-time Admin login verified. Please set a new permanent password."
                        screenMode = AuthScreenMode.FORCE_PASSWORD_CHANGE
                      }
                      else -> {
                        errorMsg = authResult.message
                      }
                    }
                  },
                  enabled = !isLoading,
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0755F5),
                    contentColor = Color.White
                  ),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_submit_button")
                ) {
                  if (isLoading) {
                    CircularProgressIndicator(
                      color = Color.White,
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                  } else {
                    Icon(
                      Icons.AutoMirrored.Filled.Login,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                  }
                  Text("Sign In", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                // Secondary Create Account Button
                OutlinedButton(
                  onClick = {
                    errorMsg = null
                    infoBanner = null
                    lastAuthResult = null
                    screenMode = AuthScreenMode.REGISTER
                  },
                  border = BorderStroke(1.5.dp, Color(0xFF0755F5)),
                  colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0755F5)
                  ),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_register_button")
                ) {
                  Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF0755F5)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "New Employee? Create Account",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0755F5),
                    maxLines = 1
                  )
                }
              }

              AuthScreenMode.REGISTER -> {
                val isFinishingRole = DesignationPolicy.isFinishingDesignation(regDesignation)
                val effectivePhysicalFloor = if (isFinishingRole) FloorScope.FINISHING else regPhysicalFloor
                val previewAuthorizedScope = DesignationPolicy.resolveAuthorizedScope(regDesignation, effectivePhysicalFloor)

                OutlinedTextField(
                  value = regFullName,
                  onValueChange = {
                    regFullName = it
                    errorMsg = null
                  },
                  label = { Text("Full Name") },
                  placeholder = { Text("Enter your full official name") },
                  leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_full_name_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = regEmployeeId,
                  onValueChange = {
                    regEmployeeId = it
                    errorMsg = null
                  },
                  label = { Text("Employee ID") },
                  placeholder = { Text("Enter official Employee ID") },
                  leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_employee_id_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                // Official Designation Dropdown
                ExposedDropdownMenuBox(
                  expanded = regDesignationExpanded,
                  onExpandedChange = { regDesignationExpanded = !regDesignationExpanded },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  OutlinedTextField(
                    value = regDesignation.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Official Designation") },
                    leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regDesignationExpanded) },
                    colors = fieldColors,
                    modifier = Modifier
                      .menuAnchor()
                      .fillMaxWidth()
                      .testTag("register_designation_dropdown"),
                    shape = RoundedCornerShape(12.dp)
                  )
                  ExposedDropdownMenu(
                    expanded = regDesignationExpanded,
                    onDismissRequest = { regDesignationExpanded = false },
                    modifier = Modifier.background(Color.White)
                  ) {
                    EmployeeDesignation.publicOptions().forEach { option ->
                      DropdownMenuItem(
                        text = {
                          Column {
                            Text(
                              option.displayName,
                              fontWeight = FontWeight.Bold,
                              fontSize = 13.sp,
                              color = Color(0xFF0F172A)
                            )
                            Text(
                              text = "Dept: ${option.department} • Scope: ${option.defaultAuthorizedScope?.displayName ?: "Assigned Floor"}",
                              fontSize = 11.sp,
                              color = Color(0xFF64748B)
                            )
                          }
                        },
                        onClick = {
                          regDesignation = option
                          if (DesignationPolicy.isFinishingDesignation(option)) {
                            regPhysicalFloor = FloorScope.FINISHING
                          } else if (regPhysicalFloor == FloorScope.FINISHING) {
                            regPhysicalFloor = FloorScope.SEW_1
                          }
                          regDesignationExpanded = false
                        }
                      )
                    }
                  }
                }

                // Auto-mapped Department & Access Scope Summary
                Surface(
                  color = Color(0xFFEFF6FF),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Dept: ${regDesignation.department}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A8A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = "Auto-Scope: ${previewAuthorizedScope.displayName}",
                        fontSize = 11.sp,
                        color = Color(0xFF1D4ED8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                    Surface(
                      color = Color(0xFFDBEAFE),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = regDesignation.defaultRole.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                      )
                    }
                  }
                }

                // Physical Floor Selection (Sew-1, Sew-2, Sew-3, or Finishing for Finishing roles; never All Floor)
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = if (isFinishingRole) "Assigned Section (Auto-mapped)" else "Assigned Physical Floor",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                  )
                  if (isFinishingRole) {
                    FilterChip(
                      selected = true,
                      onClick = {},
                      label = { Text("Finishing Section", fontWeight = FontWeight.Bold) },
                      leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                  } else {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      DesignationPolicy.validPhysicalSewingFloors.forEach { floorOption ->
                        val isSelected = regPhysicalFloor == floorOption
                        FilterChip(
                          selected = isSelected,
                          onClick = { regPhysicalFloor = floorOption },
                          colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDBEAFE),
                            selectedLabelColor = Color(0xFF1E3A8A),
                            containerColor = Color(0xFFF8FAFC),
                            labelColor = Color(0xFF475569)
                          ),
                          label = {
                            Text(
                              text = floorOption.displayName,
                              fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                              maxLines = 1
                            )
                          },
                          modifier = Modifier
                            .weight(1f)
                            .testTag("register_floor_${floorOption.name.lowercase()}")
                        )
                      }
                    }
                  }
                }

                OutlinedTextField(
                  value = regPassword,
                  onValueChange = {
                    regPassword = it
                    errorMsg = null
                  },
                  label = { Text("Password") },
                  placeholder = { Text("At least 4 characters") },
                  leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                  trailingIcon = {
                    IconButton(onClick = { showRegPassword = !showRegPassword }) {
                      Icon(
                        imageVector = if (showRegPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility"
                      )
                    }
                  },
                  visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = regConfirmPassword,
                  onValueChange = {
                    regConfirmPassword = it
                    errorMsg = null
                  },
                  label = { Text("Confirm Password") },
                  placeholder = { Text("Re-enter password") },
                  leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                  visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_confirm_password_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                Button(
                  onClick = {
                    errorMsg = null
                    val res = repository.registerEmployee(
                      fullName = regFullName,
                      employeeId = regEmployeeId,
                      designation = regDesignation,
                      physicalFloor = effectivePhysicalFloor,
                      password = regPassword,
                      confirmPassword = regConfirmPassword
                    )
                    if (res.success) {
                      employeeId = regEmployeeId.trim()
                      password = ""
                      regFullName = ""
                      regEmployeeId = ""
                      regPassword = ""
                      regConfirmPassword = ""
                      infoBanner = res.message
                      screenMode = AuthScreenMode.LOGIN
                    } else {
                      errorMsg = res.message
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White
                  ),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("register_submit_button")
                ) {
                  Icon(Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Submit Registration for Approval", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                  onClick = {
                    errorMsg = null
                    screenMode = AuthScreenMode.LOGIN
                  },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Back to Sign In", fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                }
              }

              AuthScreenMode.BOOTSTRAP_ADMIN -> {
                Surface(
                  color = Color(0xFFEFF6FF),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Text(
                      text = "Designated Initial Admin ID: $INITIAL_ADMIN_EMPLOYEE_ID",
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Black,
                      color = Color(0xFF1E3A8A)
                    )
                    Text(
                      text = "Set the initial password for Employee ID $INITIAL_ADMIN_EMPLOYEE_ID. Stored as a salted SHA-256 hash and must be updated after first sign-in.",
                      fontSize = 11.sp,
                      color = Color(0xFF1E40AF)
                    )
                  }
                }

                OutlinedTextField(
                  value = INITIAL_ADMIN_EMPLOYEE_ID,
                  onValueChange = {},
                  readOnly = true,
                  enabled = false,
                  label = { Text("Initial Admin Employee ID") },
                  leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = bootstrapAdminName,
                  onValueChange = { bootstrapAdminName = it },
                  label = { Text("Administrator Name") },
                  leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = bootstrapPassword,
                  onValueChange = {
                    bootstrapPassword = it
                    errorMsg = null
                  },
                  label = { Text("Initial Admin Password") },
                  placeholder = { Text("Minimum 6 characters") },
                  leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                  trailingIcon = {
                    IconButton(onClick = { showBootstrapPassword = !showBootstrapPassword }) {
                      Icon(
                        imageVector = if (showBootstrapPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password"
                      )
                    }
                  },
                  visualTransformation = if (showBootstrapPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bootstrap_password_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = bootstrapConfirmPassword,
                  onValueChange = {
                    bootstrapConfirmPassword = it
                    errorMsg = null
                  },
                  label = { Text("Confirm Initial Password") },
                  leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                  visualTransformation = if (showBootstrapPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bootstrap_confirm_password_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                Button(
                  onClick = {
                    errorMsg = null
                    val res = repository.bootstrapFirstAdmin(
                      employeeId = INITIAL_ADMIN_EMPLOYEE_ID,
                      initialPassword = bootstrapPassword,
                      confirmPassword = bootstrapConfirmPassword,
                      fullName = bootstrapAdminName
                    )
                    if (res.success) {
                      employeeId = INITIAL_ADMIN_EMPLOYEE_ID
                      password = ""
                      bootstrapPassword = ""
                      bootstrapConfirmPassword = ""
                      infoBanner = res.message
                      screenMode = AuthScreenMode.LOGIN
                    } else {
                      errorMsg = res.message
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1D4ED8),
                    contentColor = Color.White
                  ),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("bootstrap_submit_button")
                ) {
                  Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Provision Initial Admin ($INITIAL_ADMIN_EMPLOYEE_ID)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                  onClick = {
                    errorMsg = null
                    screenMode = AuthScreenMode.LOGIN
                  },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Back to Sign In", fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                }
              }

              AuthScreenMode.FORCE_PASSWORD_CHANGE -> {
                val activeId = lastAuthResult?.user?.id ?: employeeId.trim().ifEmpty { INITIAL_ADMIN_EMPLOYEE_ID }

                Surface(
                  color = Color(0xFFFFFBEB),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "For security compliance, Employee ID $activeId must set a new permanent password before accessing the dashboard.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF92400E),
                    modifier = Modifier.padding(10.dp)
                  )
                }

                OutlinedTextField(
                  value = currentTempPassword,
                  onValueChange = {
                    currentTempPassword = it
                    errorMsg = null
                  },
                  label = { Text("Current / Initial Password") },
                  leadingIcon = { Icon(Icons.Default.LockClock, contentDescription = null) },
                  visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = newPassword,
                  onValueChange = {
                    newPassword = it
                    errorMsg = null
                  },
                  label = { Text("New Permanent Password") },
                  placeholder = { Text("At least 6 characters") },
                  leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                  trailingIcon = {
                    IconButton(onClick = { showNewPassword = !showNewPassword }) {
                      Icon(
                        imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password"
                      )
                    }
                  },
                  visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("force_new_password_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                  value = confirmNewPassword,
                  onValueChange = {
                    confirmNewPassword = it
                    errorMsg = null
                  },
                  label = { Text("Confirm New Password") },
                  leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                  visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                  singleLine = true,
                  colors = fieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("force_confirm_password_input"),
                  shape = RoundedCornerShape(12.dp)
                )

                Button(
                  onClick = {
                    val res = repository.changePassword(
                      userId = activeId,
                      currentPassword = currentTempPassword,
                      newPassword = newPassword,
                      confirmPassword = confirmNewPassword
                    )
                    if (res.success) {
                      onLoginSuccess()
                    } else {
                      errorMsg = res.message
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White
                  ),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("force_change_password_submit")
                ) {
                  Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Update Password & Continue", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Four Feature Icons in one horizontal row with vertical dividers
        Surface(
          color = Color(0xFF0C2556).copy(alpha = 0.65f),
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.45f)),
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            FeatureIconColumn(
              title = "Production\nMonitoring",
              containerColor = Color(0xFF1D72FE),
              icon = Icons.Default.BarChart,
              modifier = Modifier.weight(1f)
            )
            VerticalDivider(
              color = Color(0xFF38BDF8).copy(alpha = 0.25f),
              modifier = Modifier.height(36.dp)
            )
            FeatureIconColumn(
              title = "Quality\nManagement",
              containerColor = Color(0xFF10B981),
              icon = Icons.Default.Verified,
              modifier = Modifier.weight(1f)
            )
            VerticalDivider(
              color = Color(0xFF38BDF8).copy(alpha = 0.25f),
              modifier = Modifier.height(36.dp)
            )
            FeatureIconColumn(
              title = "Operations\nControl",
              containerColor = Color(0xFFF59E0B),
              icon = Icons.Default.Settings,
              modifier = Modifier.weight(1f)
            )
            VerticalDivider(
              color = Color(0xFF38BDF8).copy(alpha = 0.25f),
              modifier = Modifier.height(36.dp)
            )
            FeatureIconColumn(
              title = "Real-time\nData",
              containerColor = Color(0xFF8B5CF6),
              icon = Icons.Default.Sync,
              modifier = Modifier.weight(1f)
            )
          }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Minimal Footer
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(bottom = 14.dp)
        ) {
          Text(
            text = "NORTHERN FASHIONS LIMITED",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = Color(0xFF94A3B8)
          )
          Box(
            modifier = Modifier
              .width(36.dp)
              .height(3.dp)
              .clip(RoundedCornerShape(1.5.dp))
              .background(Color(0xFF1677FF))
          )
        }
      }
    }
  }

@Composable
private fun FeatureIconColumn(
  title: String,
  containerColor: Color,
  icon: ImageVector,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = containerColor,
      shadowElevation = 4.dp,
      modifier = Modifier.size(46.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title.replace("\n", " "),
          tint = Color.White,
          modifier = Modifier.size(24.dp)
        )
      }
    }
    Text(
      text = title,
      fontSize = 10.5.sp,
      fontWeight = FontWeight.SemiBold,
      color = Color(0xFFE2E8F0),
      textAlign = TextAlign.Center,
      lineHeight = 13.sp,
      maxLines = 2
    )
  }
}
