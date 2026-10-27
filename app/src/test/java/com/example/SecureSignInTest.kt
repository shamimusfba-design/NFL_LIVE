package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.model.AuthStatus
import com.example.model.EmployeeDesignation
import com.example.model.FloorScope
import com.example.service.FirebaseAuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The old shared logins are closed in the real app; the other tests keep them switched on with
 * -Dnfl.legacyLogin=true (see app/build.gradle.kts). These tests switch them off, like the real app.
 * NOTE: written without being able to run Gradle; CI is the check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureSignInTest {

  private inline fun <T> withLegacyLogin(enabled: Boolean, block: () -> T): T {
    val old = System.getProperty("nfl.legacyLogin")
    System.setProperty("nfl.legacyLogin", enabled.toString())
    try {
      return block()
    } finally {
      if (old == null) System.clearProperty("nfl.legacyLogin") else System.setProperty("nfl.legacyLogin", old)
    }
  }

  @Test
  fun `the old shared logins admin and 123 do not work and the default accounts are gone`() = withLegacyLogin(false) {
    val repo = NflRepository(ApplicationProvider.getApplicationContext<Context>())
    assertTrue("no default account may exist", repo.users.value.none { it.id.equals("admin", true) })
    for (id in listOf("admin", "1015198", "ie", "production")) {
      val r = repo.authenticateUser(id, "123")
      assertEquals(id, AuthStatus.INVALID_CREDENTIALS, r.status)
      assertTrue(r.message, r.message.contains("closed"))
    }
    assertNull(repo.currentUser.value)
  }

  @Test
  fun `a registration needs at least six characters because Firebase needs them`() = withLegacyLogin(false) {
    val repo = NflRepository(ApplicationProvider.getApplicationContext<Context>())
    val short = repo.validateRegistrationInputs("Rahim Uddin", "EMP-5501", EmployeeDesignation.PRODUCTION_SUPERVISOR, FloorScope.SEW_2, "12345", "12345")
    assertNotNull(short)
    assertTrue(short!!.message, short.message.contains("6"))
    assertNull(repo.validateRegistrationInputs("Rahim Uddin", "EMP-5501", EmployeeDesignation.PRODUCTION_SUPERVISOR, FloorScope.SEW_2, "123456", "123456"))
    val mismatch = repo.validateRegistrationInputs("Rahim Uddin", "EMP-5501", EmployeeDesignation.PRODUCTION_SUPERVISOR, FloorScope.SEW_2, "123456", "654321")
    assertNotNull(mismatch)
  }

  @Test
  fun `nobody can register the reserved administrator id`() = withLegacyLogin(false) {
    val repo = NflRepository(ApplicationProvider.getApplicationContext<Context>())
    val r = repo.validateRegistrationInputs("Someone", "1015198", EmployeeDesignation.PRODUCTION_SUPERVISOR, FloorScope.SEW_2, "123456", "123456")
    assertNotNull(r)
    assertTrue(r!!.message, r.message.contains("reserved"))
  }

  @Test
  fun `the hidden firebase e-mail of an employee is stable and lower case`() {
    assertEquals("1015198@staff.nfl-live.app", FirebaseAuthManager.employeeEmail(" 1015198 "))
    assertEquals("emp-sup-202@staff.nfl-live.app", FirebaseAuthManager.employeeEmail("EMP-SUP-202"))
  }

  @Test
  fun `a registration made without the old switch stores no password hash`() = withLegacyLogin(false) {
    val repo = NflRepository(ApplicationProvider.getApplicationContext<Context>())
    val r = repo.registerEmployee("Karim Supervisor", "EMP-SUP-202", EmployeeDesignation.PRODUCTION_SUPERVISOR, FloorScope.SEW_1, "pass1234", "pass1234")
    assertTrue(r.message, r.success)
    val saved = repo.users.value.first { it.id == "EMP-SUP-202" }
    assertEquals("", saved.passwordHash)
    assertEquals("", saved.passwordSalt)
    assertEquals("", saved.password)
  }
}
