package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.NflRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles Approve / Reject notification action buttons securely:
 * - Never trusts secrets or role claims inside the PendingIntent.
 * - Requires an active authenticated Admin session in NflRepository.
 * - Enforces idempotency if the request was already approved or rejected by another Admin or screen.
 */
class AdminApprovalActionReceiver : BroadcastReceiver() {

  companion object {
    const val ACTION_APPROVE_REQUEST = "com.example.action.APPROVE_ADMIN_REQUEST"
    const val ACTION_REJECT_REQUEST = "com.example.action.REJECT_ADMIN_REQUEST"
  }

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action != ACTION_APPROVE_REQUEST && action != ACTION_REJECT_REQUEST) return

    val requestKey = intent.getStringExtra(FactoryAlertManager.EXTRA_APPROVAL_REQUEST_ID).orEmpty()
    val requestType = intent.getStringExtra(FactoryAlertManager.EXTRA_APPROVAL_REQUEST_TYPE)
      .orEmpty()
      .ifBlank { FactoryAlertManager.REQUEST_TYPE_EMPLOYEE_REGISTRATION }
    val targetUserId = intent.getStringExtra(FactoryAlertManager.EXTRA_APPROVAL_TARGET_USER_ID)
      .orEmpty()
      .ifBlank {
        requestKey.removePrefix("REG_").removePrefix("DEV_").removePrefix("TV_")
      }
    val notificationId = intent.getIntExtra(
      FactoryAlertManager.EXTRA_NOTIFICATION_ID,
      requestKey.hashCode()
    )

    if (requestKey.isBlank() && targetUserId.isBlank()) return

    val isApprove = action == ACTION_APPROVE_REQUEST
    val repository = NflRepository.getInstance(context.applicationContext)
    val result = repository.handleNotificationApprovalAction(
      requestKey = requestKey,
      requestType = requestType,
      targetUserId = targetUserId,
      approve = isApprove
    )

    val alertManager = FactoryAlertManager(context.applicationContext)
    if (result.success) {
      alertManager.postActionFeedbackNotification(
        notificationId = notificationId,
        title = if (isApprove) "Request Approved" else "Request Rejected",
        message = result.message,
        requestKey = requestKey,
        requestType = requestType,
        targetUserId = targetUserId
      )
    } else {
      alertManager.postActionFeedbackNotification(
        notificationId = notificationId,
        title = "Admin Verification Required",
        message = result.message,
        requestKey = requestKey,
        requestType = requestType,
        targetUserId = targetUserId
      )
    }

    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        repository.flushPendingCloudSync()
      } finally {
        pendingResult?.finish()
      }
    }
  }
}
