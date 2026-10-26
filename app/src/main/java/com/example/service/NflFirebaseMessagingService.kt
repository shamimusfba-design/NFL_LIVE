package com.example.service

import android.util.Log
import com.example.data.NflRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives real Firebase Cloud Messaging (FCM) push notifications when the app is in
 * foreground, background, or closed, and manages Admin device FCM registration tokens.
 */
class NflFirebaseMessagingService : FirebaseMessagingService() {

  companion object {
    private const val TAG = "NflFcmService"
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.i(TAG, "FCM registration token refreshed")
    val repository = NflRepository.getInstance(applicationContext)
    repository.updateFcmToken(token)
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    val data = remoteMessage.data
    val notificationTitle = remoteMessage.notification?.title
    val notificationBody = remoteMessage.notification?.body

    val repository = NflRepository.getInstance(applicationContext)
    repository.handleIncomingFcmPushPayload(
      data = data,
      fallbackTitle = notificationTitle,
      fallbackBody = notificationBody
    )
  }
}
