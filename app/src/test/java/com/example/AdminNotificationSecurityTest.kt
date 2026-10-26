package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.model.AccountStatus
import com.example.model.AdminApprovalDeepLink
import com.example.model.AuthStatus
import com.example.model.EmployeeDesignation
import com.example.model.FloorScope
import com.example.model.INITIAL_ADMIN_EMPLOYEE_ID
import com.example.service.FactoryAlertManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdminNotificationSecurityTest {

  private lateinit var context: Context
  private lateinit var repository: NflRepository
  private lateinit var alertManager: FactoryAlertManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("nfl_factory_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("nfl_admin_notification_dedup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    alertManager = FactoryAlertManager(context)
    repository = NflRepository(context)
  }

  @Test
  fun notificationDeduplicationPreventsDuplicateAlertsForSameRequestId() {
    val requestId = "REG_205001"
    assertFalse(alertManager.hasNotifiedRequest(requestId))

    // 1. When POST_NOTIFICATIONS is not yet granted on Android 13+, fails gracefully without crashing
    val deniedNotify = alertManager.triggerAdminApprovalNotification(
      requestKey = requestId,
      title = "New Employee Registration",
      message = "Karim Ahmed (205001) • Production Supervisor",
      requestType = FactoryAlertManager.REQUEST_TYPE_EMPLOYEE_REGISTRATION,
      targetUserId = "205001"
    )
    assertFalse("Should return false when POST_NOTIFICATIONS is not yet granted", deniedNotify)
    assertFalse("Should not mark request notified when permission was denied", alertManager.hasNotifiedRequest(requestId))

    // 2. Grant POST_NOTIFICATIONS and verify first notification posts and second is deduplicated
    org.robolectric.Shadows.shadowOf(context as android.app.Application)
      .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

    val firstNotify = alertManager.triggerAdminApprovalNotification(
      requestKey = requestId,
      title = "New Employee Registration",
      message = "Karim Ahmed (205001) • Production Supervisor",
      requestType = FactoryAlertManager.REQUEST_TYPE_EMPLOYEE_REGISTRATION,
      targetUserId = "205001"
    )
    assertTrue("First notification for requestId should be posted", firstNotify)
    assertTrue(alertManager.hasNotifiedRequest(requestId))

    val duplicateNotify = alertManager.triggerAdminApprovalNotification(
      requestKey = requestId,
      title = "New Employee Registration",
      message = "Karim Ahmed (205001) • Production Supervisor",
      requestType = FactoryAlertManager.REQUEST_TYPE_EMPLOYEE_REGISTRATION,
      targetUserId = "205001"
    )
    assertFalse("Second notification for the same requestId must be deduplicated", duplicateNotify)
  }

  @Test
  fun notificationActionButtonsRejectUnauthenticatedCallerAndEnforceIdempotencyForAdmin() {
    // 1. Register a pending employee
    val regRes = repository.registerEmployee(
      fullName = "Nasir Uddin",
      employeeId = "304002",
      designation = EmployeeDesignation.PRODUCTION_SUPERVISOR,
      physicalFloor = FloorScope.SEW_2,
      password = "Password123",
      confirmPassword = "Password123"
    )
    assertTrue(regRes.success)

    // 2. Ensure logged out / non-admin cannot approve via notification action handler
    repository.logout()
    val unauthAction = repository.handleNotificationApprovalAction(
      requestKey = "REG_304002",
      requestType = FactoryAlertManager.REQUEST_TYPE_EMPLOYEE_REGISTRATION,
      targetUserId = "304002",
      approve = true
    )
    assertFalse("Unauthenticated notification action must be rejected", unauthAction.success)
    val userStillPending = repository.users.value.first { it.id == "304002" }
    assertEquals(AccountStatus.PENDING_APPROVAL, userStillPending.accountStatus)

    // 3. Provision Initial Admin (1015198) and log in as Admin
    val bootRes = repository.bootstrapFirstAdmin(
      employeeId = INITIAL_ADMIN_EMPLOYEE_ID,
      initialPassword = "AdminPassword123",
      confirmPassword = "AdminPassword123",
      fullName = "Primary Admin"
    )
    assertTrue(bootRes.success)
    val loginRes = repository.authenticateUser(INITIAL_ADMIN_EMPLOYEE_ID, "AdminPassword123")
    assertTrue(loginRes.status == AuthStatus.SUCCESS || loginRes.status == AuthStatus.PASSWORD_CHANGE_REQUIRED)

    // 4. Authenticated Admin approves via notification action
    val adminApprove = repository.handleNotificationApprovalAction(
      requestKey = "REG_304002",
      requestType = FactoryAlertManager.REQUEST_TYPE_EMPLOYEE_REGISTRATION,
      targetUserId = "304002",
      approve = true
    )
    assertTrue("Authenticated Admin notification action should succeed", adminApprove.success)
    val approvedUser = repository.users.value.first { it.id == "304002" }
    assertEquals(AccountStatus.APPROVED, approvedUser.accountStatus)

    // 5. Idempotency: Tapping Reject after request was already APPROVED must not overwrite it
    val secondAction = repository.handleNotificationApprovalAction(
      requestKey = "REG_304002",
      requestType = FactoryAlertManager.REQUEST_TYPE_EMPLOYEE_REGISTRATION,
      targetUserId = "304002",
      approve = false
    )
    assertTrue("Already-processed request returns graceful idempotent result", secondAction.message.contains("already APPROVED", ignoreCase = true))
    val stillApprovedUser = repository.users.value.first { it.id == "304002" }
    assertEquals(AccountStatus.APPROVED, stillApprovedUser.accountStatus)
  }

  @Test
  fun deepLinkStateIsStoredUntilConsumedByAdminScreen() {
    assertNull(repository.pendingApprovalDeepLink.value)
    repository.setPendingApprovalDeepLink(
      AdminApprovalDeepLink(
        requestId = "DEV_tv_display_TV01",
        requestType = "TV_DEVICE_APPROVAL",
        targetUserId = "tv_display"
      )
    )
    val link = repository.pendingApprovalDeepLink.value
    assertNotNull(link)
    assertEquals("DEV_tv_display_TV01", link?.requestId)
    assertEquals("TV_DEVICE_APPROVAL", link?.requestType)

    repository.consumePendingApprovalDeepLink()
    assertNull(repository.pendingApprovalDeepLink.value)
  }

  @Test
  fun unapprovedSmartTvRequiresAdminApprovalBeforeValidTvSession() {
    assertFalse("Fresh TV device without Admin approval must not have a valid TV session", repository.hasValidTvSession())
    val tvRequest = repository.ensureTvDisplaySetupRequest()
    assertNotNull(tvRequest)
    assertEquals("PENDING", tvRequest.status)
    assertFalse("Pending TV request must still require Admin approval before granting TV session", repository.hasValidTvSession())
  }
}
