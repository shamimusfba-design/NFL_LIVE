package com.example

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows

abstract class FirestoreEmulatorTestBase {
  protected lateinit var db: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected val projectId = "demo-nfl-live-test"
  protected val databaseId = "(default)"
  protected val firestorePort = 8085

  @Before
  open fun setUp() {
    FirebaseApp.clearInstancesForTest()

    val options = FirebaseOptions.Builder()
      .setProjectId(projectId)
      .setApplicationId("1:1234567890:android:abcdef")
      .setApiKey("fake-api-key-for-emulator")
      .build()

    val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options)
    auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("127.0.0.1", 9099)

    db = FirebaseFirestore.getInstance(app)
    db.useEmulator("127.0.0.1", firestorePort)
    db.firestoreSettings = FirebaseFirestoreSettings.Builder()
      .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
      .build()
  }

  protected fun <T> Task<T>.awaitWithLooper(timeoutMs: Long = 15_000L): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!isComplete && System.currentTimeMillis() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(10)
    }
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    if (!isComplete) {
      throw AssertionError("Firebase Task timed out after ${timeoutMs}ms")
    }
    val ex = exception
    if (ex != null) {
      throw ex
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  protected fun signInAs(uid: String, email: String = "$uid@example.com"): String {
    val header = Base64.getUrlEncoder().withoutPadding()
      .encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
    val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
      """{"iss":"https://securetoken.google.com/$projectId","aud":"$projectId","sub":"$uid","user_id":"$uid","email":"$email","email_verified":true,"iat":0,"exp":9999999999}""".toByteArray()
    )
    val fakeIdToken = "$header.$payload."
    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(fakeIdToken, null)
    val authResult = auth.signInWithCredential(credential).awaitWithLooper()
    return authResult.user?.uid ?: auth.currentUser?.uid ?: uid
  }

  protected fun signOut() {
    auth.signOut()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }

  @After
  open fun tearDown() {
    try {
      val url = URL("http://127.0.0.1:$firestorePort/emulator/v1/projects/$projectId/databases/$databaseId/documents")
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "DELETE"
      conn.responseCode
      conn.disconnect()
    } catch (_: Exception) {
    }
    try {
      db.terminate().awaitWithLooper(3_000L)
    } catch (_: Exception) {
    }
    FirebaseApp.clearInstancesForTest()
  }
}
