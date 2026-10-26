package com.example.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.NflRepository
import java.util.concurrent.TimeUnit

/**
 * Spark/free-plan compatible background synchronization worker that checks for pending
 * Employee Registration and Smart TV authorization requests for authenticated Admin devices.
 */
class AdminApprovalSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val repository = NflRepository.getInstance(applicationContext)
      repository.checkPendingAdminApprovalsInBackground()
      Result.success()
    } catch (e: Exception) {
      Result.retry()
    }
  }

  companion object {
    private const val PERIODIC_WORK_NAME = "nfl_admin_approval_periodic_sync"
    private const val IMMEDIATE_WORK_NAME = "nfl_admin_approval_immediate_sync"

    fun scheduleForAdmin(context: Context) {
      try {
        val constraints = Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()

        val periodicRequest = PeriodicWorkRequestBuilder<AdminApprovalSyncWorker>(
          15,
          TimeUnit.MINUTES
        )
          .setConstraints(constraints)
          .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
          PERIODIC_WORK_NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          periodicRequest
        )
      } catch (_: Exception) {}
    }

    fun enqueueImmediateCheck(context: Context) {
      try {
        val constraints = Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<AdminApprovalSyncWorker>()
          .setConstraints(constraints)
          .build()

        WorkManager.getInstance(context.applicationContext).enqueue(oneTimeRequest)
      } catch (_: Exception) {}
    }

    fun schedulePeriodicSync(context: Context) {
      scheduleForAdmin(context)
    }

    fun triggerImmediateCheck(context: Context) {
      enqueueImmediateCheck(context)
    }

    fun cancelForLoggedOutAdmin(context: Context) {
      try {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
      } catch (_: Exception) {}
    }
  }
}

