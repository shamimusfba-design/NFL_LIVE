package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the fail-closed checks and the Admin multi-device session rules.
 * NOTE: written without being able to run Gradle; run ./gradlew :app:testDebugUnitTest to confirm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthSessionHardeningTest {

  private fun newRepo(): NflRepository {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return NflRepository(context)
  }

  @Test
  fun `logged out setRole and NPT incident actions are denied`() {
    val repo = newRepo()
    repo.logout()
    assertEquals(null, repo.currentUser.value)

    val roleBefore = repo.currentRole.value
    val otherRole = UserRole.entries.first { it != roleBefore }
    repo.setRole(otherRole)
    assertEquals(roleBefore, repo.currentRole.value)

    val incidentsBefore = repo.incidents.value.size
    repo.reportIncident(
      IncidentItem(
        lineId = "K1",
        floor = "Sew 2",
        category = IncidentCategory.MACHINE_BREAKDOWN,
        description = "Logged out attempt",
        reportedBy = "Nobody"
      )
    )
    assertEquals(incidentsBefore, repo.incidents.value.size)
    assertTrue(repo.syncStatusBanner.value.orEmpty().contains("Access Denied", ignoreCase = true))
  }

  @Test
  fun `admin session is not logged out by a newer record with another session id or device`() {
    val repo = newRepo()
    repo.authenticateUser("admin", "123")
    val admin = repo.currentUser.value
    assertNotNull(admin)
    assertTrue(admin!!.isAdmin)

    // Another Admin device signs in and rewrites the shared account record.
    val newerFromOtherAdminDevice = admin.copy(
      updatedAtMillis = admin.updatedAtMillis + 5000L,
      activeSessionId = "OTHER-ADMIN-DEVICE-SESSION",
      authorizedDeviceId = "OTHER-ADMIN-DEVICE-ID"
    )
    repo.upsertRemoteUsers(listOf(newerFromOtherAdminDevice))

    assertNotNull(repo.currentUser.value)
    assertEquals("admin", repo.currentUser.value?.id)
  }

  @Test
  fun `admin is still logged out by a newer suspended record`() {
    val repo = newRepo()
    repo.authenticateUser("admin", "123")
    val admin = repo.currentUser.value!!

    repo.upsertRemoteUsers(
      listOf(
        admin.copy(
          updatedAtMillis = admin.updatedAtMillis + 5000L,
          accountStatus = AccountStatus.SUSPENDED,
          isActive = false
        )
      )
    )
    assertEquals(null, repo.currentUser.value)
  }

  @Test
  fun `admin is still logged out by an explicit REVOKED session marker`() {
    val repo = newRepo()
    repo.authenticateUser("admin", "123")
    val admin = repo.currentUser.value!!

    repo.upsertRemoteUsers(
      listOf(
        admin.copy(
          updatedAtMillis = admin.updatedAtMillis + 5000L,
          activeSessionId = "REVOKED-test-marker"
        )
      )
    )
    assertEquals(null, repo.currentUser.value)
  }

  @Test
  fun `employee session is still ended by a newer record with a different session id`() {
    val repo = newRepo()
    val res = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, res.status)
    val sup = repo.currentUser.value!!

    repo.upsertRemoteUsers(
      listOf(
        sup.copy(
          updatedAtMillis = sup.updatedAtMillis + 5000L,
          activeSessionId = "A-DIFFERENT-DEVICE-SESSION"
        )
      )
    )
    assertEquals(null, repo.currentUser.value)
  }

  @Test
  fun `admin stays logged in after app restart without network`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val first = NflRepository(context)
    first.authenticateUser("admin", "123")
    assertNotNull(first.currentUser.value)

    val restarted = NflRepository(context)
    assertNotNull(restarted.currentUser.value)
    assertEquals("admin", restarted.currentUser.value?.id)
  }
}
