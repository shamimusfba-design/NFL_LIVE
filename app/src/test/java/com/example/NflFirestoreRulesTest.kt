package com.example

import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NflFirestoreRulesTest : FirestoreEmulatorTestBase() {

  private fun assertPermissionDenied(block: () -> Unit) {
    try {
      block()
      fail("Expected PERMISSION_DENIED from Firestore security rules, but operation succeeded")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  @Test
  fun unauthenticatedClientCannotReadUsersOrProductionData() {
    signOut()
    assertPermissionDenied {
      db.collection("users").document("1015198").get().awaitWithLooper()
    }
    assertPermissionDenied {
      db.collection("production_entries").document("PROD_K1_2026-10-27_0800-0900").get().awaitWithLooper()
    }
  }

  @Test
  fun pendingRegistrationCannotSelfPromoteToAdminOrApproved() {
    val attackerUid = signInAs("uid_attacker")
    // Pending registration with PENDING_APPROVAL and isAdmin=false is allowed
    db.collection("users").document("209999").set(
      mapOf(
        "id" to "209999",
        "employeeId" to "209999",
        "name" to "New Worker",
        "role" to "SUPERVISOR",
        "floor" to "SEW_1",
        "authorizedFloorScope" to "SEW_1",
        "accountStatus" to "PENDING_APPROVAL",
        "isAdmin" to false,
        "isActive" to false,
        "firebaseUid" to attackerUid
      )
    ).awaitWithLooper()

    // Attempting to self-promote to ADMIN or APPROVED is denied by server rules
    assertPermissionDenied {
      db.collection("users").document("209999").set(
        mapOf(
          "id" to "209999",
          "employeeId" to "209999",
          "name" to "New Worker",
          "role" to "ADMIN",
          "floor" to "ALL",
          "authorizedFloorScope" to "ALL",
          "accountStatus" to "APPROVED",
          "isAdmin" to true,
          "isActive" to true,
          "firebaseUid" to attackerUid
        )
      ).awaitWithLooper()
    }
  }

  @Test
  fun adminCanBootstrapAndApproveUserAndSupervisorIsFloorScoped() {
    // 1. Bootstrap Initial Admin 1015198
    val adminUid = signInAs("uid_admin_1015198")
    db.collection("users").document("1015198").set(
      mapOf(
        "id" to "1015198",
        "employeeId" to "1015198",
        "name" to "Initial Admin",
        "role" to "ADMIN",
        "floor" to "ALL",
        "authorizedFloorScope" to "ALL",
        "accountStatus" to "APPROVED",
        "isAdmin" to true,
        "isActive" to true,
        "firebaseUid" to adminUid
      )
    ).awaitWithLooper()
    db.collection("auth_bindings").document(adminUid).set(
      mapOf(
        "uid" to adminUid,
        "firebaseUid" to adminUid,
        "employeeId" to "1015198",
        "boundAtMillis" to 1761523200000L
      )
    ).awaitWithLooper()

    // 2. Admin approves Sew-1 Supervisor 200101
    db.collection("users").document("200101").set(
      mapOf(
        "id" to "200101",
        "employeeId" to "200101",
        "name" to "Rahim Sew1",
        "role" to "SUPERVISOR",
        "floor" to "SEW_1",
        "authorizedFloorScope" to "SEW_1",
        "accountStatus" to "APPROVED",
        "isAdmin" to false,
        "isActive" to true,
        "firebaseUid" to ""
      )
    ).awaitWithLooper()

    // 3. Supervisor signs in and binds auth UID
    val supUid = signInAs("uid_sup_200101")
    db.collection("auth_bindings").document(supUid).set(
      mapOf(
        "uid" to supUid,
        "firebaseUid" to supUid,
        "employeeId" to "200101",
        "boundAtMillis" to 1761523200000L
      )
    ).awaitWithLooper()

    // Supervisor can create production entry for assigned floor Sew-1
    db.collection("production_entries").document("PROD_K1_2026-10-27_0800-0900").set(
      mapOf(
        "lineId" to "K1",
        "floor" to "Sew-1",
        "date" to "2026-10-27",
        "hourSlot" to "08:00 - 09:00",
        "outputQty" to 120,
        "targetForHour" to 130,
        "timestamp" to System.currentTimeMillis()
      )
    ).awaitWithLooper()

    // Supervisor cannot create production entry for unassigned floor Sew-2
    assertPermissionDenied {
      db.collection("production_entries").document("PROD_K8_2026-10-27_0800-0900").set(
        mapOf(
          "lineId" to "K8",
          "floor" to "Sew-2",
          "date" to "2026-10-27",
          "hourSlot" to "08:00 - 09:00",
          "outputQty" to 110,
          "targetForHour" to 130,
          "timestamp" to System.currentTimeMillis()
        )
      ).awaitWithLooper()
    }

    // Supervisor cannot delete production entry
    assertPermissionDenied {
      db.collection("production_entries").document("PROD_K1_2026-10-27_0800-0900").delete().awaitWithLooper()
    }

    assertTrue(true)
  }
}
