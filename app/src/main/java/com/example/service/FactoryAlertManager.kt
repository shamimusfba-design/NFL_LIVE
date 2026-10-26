package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

enum class FactorySoundMode(val label: String) {
  SOUND_ON("Sound ON"),
  MUTE_ALL("Mute Sound"),
  MUTE_15_MIN("Mute 15 Mins"),
  MUTE_1_HOUR("Mute 1 Hour"),
  MUTE_UNTIL_END_OF_SHIFT("Mute Until End of Shift")
}

class FactoryAlertManager(private val context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("nfl_sound_alert_prefs", Context.MODE_PRIVATE)

  private val dedupPrefs: SharedPreferences =
    context.getSharedPreferences("nfl_admin_notification_dedup_prefs", Context.MODE_PRIVATE)

  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private var activeToneGenerator: ToneGenerator? = null
  private var isTonePlaying = false

  private val _soundMode = MutableStateFlow(loadSoundMode())
  val soundMode: StateFlow<FactorySoundMode> = _soundMode.asStateFlow()

  private val _activeAlarmSilenced = MutableStateFlow(false)
  val activeAlarmSilenced: StateFlow<Boolean> = _activeAlarmSilenced.asStateFlow()

  companion object {
    const val CHANNEL_CRITICAL = "nfl_channel_critical"
    const val CHANNEL_QUALITY = "nfl_channel_quality"
    const val CHANNEL_PRODUCTION = "nfl_channel_production"
    const val CHANNEL_GENERAL = "nfl_channel_general"
    const val CHANNEL_ADMIN_APPROVALS = "nfl_admin_approvals_channel"

    const val EXTRA_OPEN_ADMIN_APPROVALS = "open_admin_approvals"
    const val EXTRA_APPROVAL_REQUEST_ID = "approval_request_id"
    const val EXTRA_APPROVAL_REQUEST_TYPE = "approval_request_type"
    const val EXTRA_APPROVAL_TARGET_USER_ID = "approval_target_user_id"
    const val EXTRA_NOTIFICATION_ID = "approval_notification_id"

    const val REQUEST_TYPE_EMPLOYEE_REGISTRATION = "EMPLOYEE_REGISTRATION"
    const val REQUEST_TYPE_TV_DEVICE_APPROVAL = "TV_DEVICE_APPROVAL"

    private const val PREF_SOUND_MODE = "pref_sound_mode"
    private const val PREF_MUTE_UNTIL = "pref_mute_until_millis"
    private const val PREF_NOTIFIED_REQUEST_IDS = "notified_request_ids"
  }

  init {
    createNotificationChannels()
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

      val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
        .build()

      val criticalChannel = NotificationChannel(
        CHANNEL_CRITICAL,
        "Critical Factory Alarms & Breakdown",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Urgent machine breakdown stoppages and critical factory alarms"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        setSound(alarmSound, audioAttributes)
      }

      val qualityChannel = NotificationChannel(
        CHANNEL_QUALITY,
        "Quality & DHU Threshold Alerts",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "DHU threshold breaches and endline quality warnings"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 300, 150, 300)
        setSound(defaultSound, audioAttributes)
      }

      val productionChannel = NotificationChannel(
        CHANNEL_PRODUCTION,
        "Production & NPT Issues",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Hourly production gaps, input shortages, and NPT events"
        enableVibration(true)
        setSound(defaultSound, audioAttributes)
      }

      val generalChannel = NotificationChannel(
        CHANNEL_GENERAL,
        "General Factory Updates",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Routine sync and administrative updates"
      }

      val adminApprovalsChannel = NotificationChannel(
        CHANNEL_ADMIN_APPROVALS,
        "Admin Registration & TV Approvals",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Real-time employee registration and Smart TV authorization requests for Admins"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 400, 150, 400)
        setSound(defaultSound, audioAttributes)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
      }

      notificationManager.createNotificationChannels(
        listOf(criticalChannel, adminApprovalsChannel, qualityChannel, productionChannel, generalChannel)
      )
    }
  }

  fun hasNotifiedRequest(requestId: String): Boolean {
    if (requestId.isBlank()) return false
    val set = dedupPrefs.getStringSet(PREF_NOTIFIED_REQUEST_IDS, emptySet()) ?: emptySet()
    return requestId in set
  }

  fun markRequestNotified(requestId: String) {
    if (requestId.isBlank()) return
    val current = dedupPrefs.getStringSet(PREF_NOTIFIED_REQUEST_IDS, emptySet())?.toMutableSet()
      ?: mutableSetOf()
    current.add(requestId)
    dedupPrefs.edit().putStringSet(PREF_NOTIFIED_REQUEST_IDS, current).apply()
  }

  fun clearRequestNotified(requestId: String) {
    if (requestId.isBlank()) return
    val current = dedupPrefs.getStringSet(PREF_NOTIFIED_REQUEST_IDS, emptySet())?.toMutableSet()
      ?: mutableSetOf()
    if (current.remove(requestId)) {
      dedupPrefs.edit().putStringSet(PREF_NOTIFIED_REQUEST_IDS, current).apply()
    }
  }

  fun setSoundMode(mode: FactorySoundMode) {
    val editor = prefs.edit()
    editor.putString(PREF_SOUND_MODE, mode.name)

    val now = System.currentTimeMillis()
    when (mode) {
      FactorySoundMode.SOUND_ON -> {
        editor.putLong(PREF_MUTE_UNTIL, 0L)
      }
      FactorySoundMode.MUTE_ALL -> {
        editor.putLong(PREF_MUTE_UNTIL, Long.MAX_VALUE)
      }
      FactorySoundMode.MUTE_15_MIN -> {
        editor.putLong(PREF_MUTE_UNTIL, now + 15 * 60 * 1000L)
      }
      FactorySoundMode.MUTE_1_HOUR -> {
        editor.putLong(PREF_MUTE_UNTIL, now + 60 * 60 * 1000L)
      }
      FactorySoundMode.MUTE_UNTIL_END_OF_SHIFT -> {
        val shiftEndMillis = calculateShiftEndMillis()
        editor.putLong(PREF_MUTE_UNTIL, shiftEndMillis)
      }
    }
    editor.apply()
    _soundMode.value = mode
    if (isSoundMuted()) {
      silenceActiveAlarm()
    }
  }

  private fun loadSoundMode(): FactorySoundMode {
    val name = prefs.getString(PREF_SOUND_MODE, FactorySoundMode.SOUND_ON.name)
    val mode = try {
      FactorySoundMode.valueOf(name ?: FactorySoundMode.SOUND_ON.name)
    } catch (_: Exception) {
      FactorySoundMode.SOUND_ON
    }

    val muteUntil = prefs.getLong(PREF_MUTE_UNTIL, 0L)
    if (muteUntil in 1 until System.currentTimeMillis()) {
      // Temporary mute expired, revert to Sound ON
      prefs.edit().putString(PREF_SOUND_MODE, FactorySoundMode.SOUND_ON.name).putLong(PREF_MUTE_UNTIL, 0L).apply()
      return FactorySoundMode.SOUND_ON
    }
    return mode
  }

  fun isSoundMuted(): Boolean {
    val muteUntil = prefs.getLong(PREF_MUTE_UNTIL, 0L)
    if (muteUntil == 0L) return false
    if (muteUntil == Long.MAX_VALUE) return true
    val now = System.currentTimeMillis()
    if (now >= muteUntil) {
      // Expired!
      setSoundMode(FactorySoundMode.SOUND_ON)
      return false
    }
    return true
  }

  fun getMuteStatusText(): String {
    val muteUntil = prefs.getLong(PREF_MUTE_UNTIL, 0L)
    if (muteUntil == 0L) return "Sound ON"
    if (muteUntil == Long.MAX_VALUE) return "Muted Indefinitely"
    val diffSec = ((muteUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    val mins = diffSec / 60
    val secs = diffSec % 60
    return "Muted ($mins m $secs s left)"
  }

  private fun calculateShiftEndMillis(): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
    // Standard factory day shift ends at 19:00 (7 PM BST)
    cal.set(Calendar.HOUR_OF_DAY, 19)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (cal.timeInMillis <= System.currentTimeMillis()) {
      // If already past 19:00, mute for remaining 4 hours of night shift
      return System.currentTimeMillis() + 4 * 60 * 60 * 1000L
    }
    return cal.timeInMillis
  }

  fun silenceActiveAlarm() {
    try {
      activeToneGenerator?.stopTone()
      activeToneGenerator?.release()
      activeToneGenerator = null
    } catch (_: Exception) {}
    isTonePlaying = false
    _activeAlarmSilenced.value = true
  }

  fun isUserEligibleForAlert(role: UserRole, userFloor: FloorScope, incident: IncidentItem): Boolean {
    // 1. Admin and Management receive all high-priority or escalated alerts
    if (role == UserRole.ADMIN) return true
    if (role == UserRole.MANAGEMENT_VIEW) {
      return incident.severity == AlertSeverity.CRITICAL || incident.isEscalated
    }

    // 2. Production Manager & Quality Manager receive all their respective domain alerts
    if (role == UserRole.PRODUCTION_MANAGER) {
      return incident.category != IncidentCategory.QUALITY_PROBLEM || incident.isEscalated
    }
    if (role == UserRole.QUALITY_MANAGER) {
      return incident.category == IncidentCategory.QUALITY_PROBLEM || incident.isEscalated
    }

    // 3. Domain specialist role routing
    when (incident.category) {
      IncidentCategory.MACHINE_BREAKDOWN, IncidentCategory.POWER_OUTAGE -> {
        if (role == UserRole.MECHANIC) return true
      }
      IncidentCategory.QUALITY_PROBLEM -> {
        if (role == UserRole.QUALITY_REPORTER || role == UserRole.AUDITOR) return true
      }
      IncidentCategory.INPUT_MATERIAL -> {
        if (role == UserRole.INPUT_MATERIAL) return true
      }
      IncidentCategory.LINE_BALANCING -> {
        if (role == UserRole.IE) return true
      }
      IncidentCategory.LOW_PRODUCTION, IncidentCategory.OPERATOR_NA, IncidentCategory.OTHER -> {
        if (role == UserRole.SUPERVISOR || role == UserRole.PRODUCTION_REPORTER) return true
      }
    }

    // 4. Floor Supervisors receive alerts affecting their floor
    if (role == UserRole.SUPERVISOR) {
      val incidentFloor = incident.floor.lowercase()
      val scopeFloor = userFloor.displayName.lowercase()
      return userFloor == FloorScope.ALL || incidentFloor.contains(scopeFloor) || scopeFloor.contains(incidentFloor)
    }

    return false
  }

  fun triggerAlertNotification(
    incident: IncidentItem,
    currentRole: UserRole,
    userFloor: FloorScope
  ) {
    if (!isUserEligibleForAlert(currentRole, userFloor, incident)) {
      return
    }

    // 1. Play audible tone if not sound muted
    if (!isSoundMuted() && !incident.isSilencedOnDevice) {
      playAudibleAlarm(incident.severity)
    }

    // 2. Post Android system notification
    postSystemNotification(incident)
  }

  private fun playAudibleAlarm(severity: AlertSeverity) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        silenceActiveAlarm()
        _activeAlarmSilenced.value = false
        activeToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 95)
        isTonePlaying = true

        when (severity) {
          AlertSeverity.CRITICAL -> {
            activeToneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
          }
          AlertSeverity.HIGH -> {
            activeToneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
          }
          AlertSeverity.MEDIUM -> {
            activeToneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
          }
          AlertSeverity.LOW_INFO -> {
            activeToneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
          }
        }
      } catch (_: Exception) {}
    }
  }

  private fun postSystemNotification(incident: IncidentItem) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return
      }
    }

    val channelId = when (incident.severity) {
      AlertSeverity.CRITICAL -> CHANNEL_CRITICAL
      AlertSeverity.HIGH -> if (incident.category == IncidentCategory.QUALITY_PROBLEM) CHANNEL_QUALITY else CHANNEL_PRODUCTION
      AlertSeverity.MEDIUM -> CHANNEL_PRODUCTION
      AlertSeverity.LOW_INFO -> CHANNEL_GENERAL
    }

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("incident_id", incident.id)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      incident.id.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val titlePrefix = when (incident.severity) {
      AlertSeverity.CRITICAL -> "🚨 CRITICAL: "
      AlertSeverity.HIGH -> "⚠️ HIGH ALERT: "
      AlertSeverity.MEDIUM -> "🔔 NOTICE: "
      AlertSeverity.LOW_INFO -> "ℹ️ INFO: "
    }

    val contentTitle = "$titlePrefix${incident.category.displayName} [${incident.lineId}]"
    val contentText = "${incident.description} • ${incident.floor} • ${incident.nptType}"

    val notification = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(R.drawable.ic_nfl_logo)
      .setContentTitle(contentTitle)
      .setContentText(contentText)
      .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\nReported by: ${incident.reportedBy}\nStatus: ${incident.status.displayName}"))
      .setPriority(
        when (incident.severity) {
          AlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_MAX
          AlertSeverity.HIGH -> NotificationCompat.PRIORITY_HIGH
          AlertSeverity.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
          AlertSeverity.LOW_INFO -> NotificationCompat.PRIORITY_LOW
        }
      )
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    try {
      NotificationManagerCompat.from(context).notify(incident.id.hashCode(), notification)
    } catch (_: SecurityException) {}
  }

  fun triggerAdminApprovalNotification(
    requestKey: String,
    title: String,
    message: String,
    requestType: String = inferRequestType(requestKey),
    targetUserId: String = inferTargetUserId(requestKey),
    forceShow: Boolean = false
  ): Boolean {
    if (!forceShow && hasNotifiedRequest(requestKey)) {
      return false
    }

    if (!isSoundMuted()) {
      playAudibleAlarm(AlertSeverity.HIGH)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return false
      }
    }

    val notificationId = requestKey.hashCode()

    val openIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_OPEN_ADMIN_APPROVALS, true)
      putExtra(EXTRA_APPROVAL_REQUEST_ID, requestKey)
      putExtra(EXTRA_APPROVAL_REQUEST_TYPE, requestType)
      putExtra(EXTRA_APPROVAL_TARGET_USER_ID, targetUserId)
    }

    val contentPendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val approveIntent = Intent(context, AdminApprovalActionReceiver::class.java).apply {
      action = AdminApprovalActionReceiver.ACTION_APPROVE_REQUEST
      putExtra(EXTRA_APPROVAL_REQUEST_ID, requestKey)
      putExtra(EXTRA_APPROVAL_REQUEST_TYPE, requestType)
      putExtra(EXTRA_APPROVAL_TARGET_USER_ID, targetUserId)
      putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    }
    val approvePendingIntent = PendingIntent.getBroadcast(
      context,
      (requestKey + "_APPROVE").hashCode(),
      approveIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val rejectIntent = Intent(context, AdminApprovalActionReceiver::class.java).apply {
      action = AdminApprovalActionReceiver.ACTION_REJECT_REQUEST
      putExtra(EXTRA_APPROVAL_REQUEST_ID, requestKey)
      putExtra(EXTRA_APPROVAL_REQUEST_TYPE, requestType)
      putExtra(EXTRA_APPROVAL_TARGET_USER_ID, targetUserId)
      putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    }
    val rejectPendingIntent = PendingIntent.getBroadcast(
      context,
      (requestKey + "_REJECT").hashCode(),
      rejectIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val publicLockscreenNotification = NotificationCompat.Builder(context, CHANNEL_ADMIN_APPROVALS)
      .setSmallIcon(R.drawable.ic_nfl_logo)
      .setContentTitle("NFL Live Admin Alert")
      .setContentText("Pending approval action required. Unlock to view details.")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .build()

    val notification = NotificationCompat.Builder(context, CHANNEL_ADMIN_APPROVALS)
      .setSmallIcon(R.drawable.ic_nfl_logo)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(
        NotificationCompat.BigTextStyle()
          .bigText("$message\nTap to inspect in Admin Panel or use Approve / Reject below.")
      )
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicLockscreenNotification)
      .setContentIntent(contentPendingIntent)
      .addAction(0, "Approve", approvePendingIntent)
      .addAction(0, "Reject", rejectPendingIntent)
      .setAutoCancel(true)
      .build()

    return try {
      NotificationManagerCompat.from(context).notify(notificationId, notification)
      markRequestNotified(requestKey)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  fun cancelNotification(notificationId: Int) {
    try {
      NotificationManagerCompat.from(context).cancel(notificationId)
    } catch (_: Exception) {}
  }

  fun postActionFeedbackNotification(
    notificationId: Int,
    title: String,
    message: String,
    requestKey: String = "",
    requestType: String = "",
    targetUserId: String = ""
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return
      }
    }

    val openIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_OPEN_ADMIN_APPROVALS, true)
      if (requestKey.isNotBlank()) putExtra(EXTRA_APPROVAL_REQUEST_ID, requestKey)
      if (requestType.isNotBlank()) putExtra(EXTRA_APPROVAL_REQUEST_TYPE, requestType)
      if (targetUserId.isNotBlank()) putExtra(EXTRA_APPROVAL_TARGET_USER_ID, targetUserId)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val feedbackNotification = NotificationCompat.Builder(context, CHANNEL_ADMIN_APPROVALS)
      .setSmallIcon(R.drawable.ic_nfl_logo)
      .setContentTitle(title)
      .setContentText(message)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    try {
      NotificationManagerCompat.from(context).notify(notificationId, feedbackNotification)
    } catch (_: SecurityException) {}
  }

  private fun inferRequestType(requestKey: String): String {
    return if (requestKey.startsWith("DEV_") || requestKey.startsWith("TV_")) {
      REQUEST_TYPE_TV_DEVICE_APPROVAL
    } else {
      REQUEST_TYPE_EMPLOYEE_REGISTRATION
    }
  }

  private fun inferTargetUserId(requestKey: String): String {
    return requestKey
      .removePrefix("REG_")
      .removePrefix("DEV_")
      .removePrefix("TV_")
  }
}

