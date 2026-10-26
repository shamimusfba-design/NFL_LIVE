package com.example.service

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {
  private const val TAG = "FirebaseAuthManager"

  private val auth: FirebaseAuth?
    get() = try {
      Firebase.auth
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseAuth not initialized in this environment: ${e.message}")
      null
    }

  val currentUser: FirebaseUser?
    get() = auth?.currentUser

  val currentUid: String
    get() = auth?.currentUser?.uid.orEmpty()

  val currentEmail: String
    get() = auth?.currentUser?.email.orEmpty()

  fun authStateChanges(): Flow<FirebaseUser?> = callbackFlow {
    val firebaseAuth = auth
    if (firebaseAuth == null) {
      trySend(null)
      awaitClose {}
      return@callbackFlow
    }
    val listener = FirebaseAuth.AuthStateListener { fa ->
      trySend(fa.currentUser)
    }
    firebaseAuth.addAuthStateListener(listener)
    awaitClose { firebaseAuth.removeAuthStateListener(listener) }
  }

  /**
   * Explicit Google Sign-In button action using Jetpack Credential Manager.
   */
  suspend fun signInWithGoogleButton(activityContext: Context): Result<FirebaseUser> {
    return try {
      val webClientId = activityContext.getString(R.string.default_web_client_id)
      val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
      executeCredentialRequest(activityContext, request)
    } catch (e: GetCredentialCancellationException) {
      Result.failure(Exception("Google Sign-In was cancelled."))
    } catch (e: Exception) {
      Log.w(TAG, "Google Sign-In failed: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Silent / returning user Google Sign-In check via Credential Manager.
   */
  suspend fun trySilentGoogleSignIn(activityContext: Context): Result<FirebaseUser> {
    val existing = currentUser
    if (existing != null) return Result.success(existing)
    return try {
      val webClientId = activityContext.getString(R.string.default_web_client_id)
      val googleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(true)
        .setAutoSelectEnabled(true)
        .build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
      executeCredentialRequest(activityContext, request)
    } catch (e: NoCredentialException) {
      Result.failure(e)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Signs in with a custom token issued by the trusted NFL security backend.
   */
  suspend fun signInWithBackendCustomToken(customToken: String): Result<FirebaseUser> {
    if (customToken.isBlank()) {
      return Result.failure(IllegalArgumentException("Custom token is blank"))
    }
    val firebaseAuth = auth ?: return Result.failure(IllegalStateException("FirebaseAuth unavailable"))
    return try {
      val authResult = firebaseAuth.signInWithCustomToken(customToken).await()
      val user = authResult.user
      if (user != null) {
        Result.success(user)
      } else {
        Result.failure(IllegalStateException("Custom token sign-in returned null user"))
      }
    } catch (e: Exception) {
      Log.w(TAG, "Custom token sign-in failed: ${e.message}")
      Result.failure(e)
    }
  }

  private suspend fun executeCredentialRequest(
    activityContext: Context,
    request: GetCredentialRequest
  ): Result<FirebaseUser> {
    val firebaseAuth = auth ?: return Result.failure(IllegalStateException("FirebaseAuth unavailable"))
    val credentialManager = CredentialManager.create(activityContext)
    val result = credentialManager.getCredential(activityContext, request)
    val credential = result.credential
    if (credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
      val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
      val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
      val user = authResult.user
      return if (user != null) {
        Result.success(user)
      } else {
        Result.failure(IllegalStateException("Firebase signInWithCredential returned null user"))
      }
    }
    return Result.failure(IllegalStateException("Unsupported credential type"))
  }

  suspend fun signOut(context: Context) {
    try {
      CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      Log.w(TAG, "ClearCredentialState warning: ${e.message}")
    }
    try {
      auth?.signOut()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseAuth signOut warning: ${e.message}")
    }
  }
}
