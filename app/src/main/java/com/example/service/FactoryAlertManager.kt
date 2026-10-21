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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private var activeToneGenerator: ToneGenerator? = null
  private var isTonePlaying = false

  private val _soundMode = MutableStateFlow(loadSoundMode())
  val soundMode: StateFlow<FactorySoundMode> = _soundMode.asStateFlow()

  private val _activeAlarmSilenced = MutableStateFlow(false)
  val activeAlarmSilenced: StateFlow<Boolean> = _activeAlarmSilenced.asStateFlow()

  companion object {
    const val CHANNEL_CRITICAL = "nfl_channel_critical_v2"
    const val CHANNEL_QUALITY = "nfl_channel_quality_v2"
    const val CHANNEL_PRODUCTION = "nfl_channel_production_v2"
    const val CHANNEL_GENERAL = "nfl_channel_general_v2"

    private const val PREF_SOUND_MODE = "pref_sound_mode"
    private const val PREF_MUTE_UNTIL = "pref_mute_until_millis"
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
        vibrationPattern = longArrayOf(0, 700, 220, 700, 220, 900)
        setSound(alarmSound, audioAttributes)
      }

      val qualityChannel = NotificationChannel(
        CHANNEL_QUALITY,
        "Quality & DHU Threshold Alerts",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "DHU threshold breaches and endline quality warnings"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 700)
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

      productionChannel.vibrationPattern = longArrayOf(0, 450, 180, 450, 180, 650)

      val generalChannel = NotificationChannel(
        CHANNEL_GENERAL,
        "General Factory Updates",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Routine sync and administrative updates"
      }

      fun silentChannel(id: String, name: String, pattern: LongArray) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
          description = "Vibration alert without alarm sound when mute is enabled"
          enableVibration(true)
          vibrationPattern = pattern
          setSound(null, null)
        }

      val silentCritical = silentChannel("${CHANNEL_CRITICAL}_silent", "Critical Alarms (Silent + Vibration)", longArrayOf(0, 700, 220, 700, 220, 900))
      val silentQuality = silentChannel("${CHANNEL_QUALITY}_silent", "Quality Alerts (Silent + Vibration)", longArrayOf(0, 500, 200, 500, 200, 700))
      val silentProduction = silentChannel("${CHANNEL_PRODUCTION}_silent", "Production Alerts (Silent + Vibration)", longArrayOf(0, 450, 180, 450, 180, 650))
      val silentGeneral = silentChannel("${CHANNEL_GENERAL}_silent", "General Updates (Silent + Vibration)", longArrayOf(0, 250, 150, 250))

      notificationManager.createNotificationChannels(
        listOf(criticalChannel, qualityChannel, productionChannel, generalChannel,
          silentCritical, silentQuality, silentProduction, silentGeneral)
      )
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

  private fun normalizeScope(value: String): String =
    value.trim().lowercase().replace("-", "").replace(" ", "").replace("_", "")

  private fun sameFloor(userFloor: FloorScope, incidentFloor: String): Boolean {
    if (userFloor == FloorScope.ALL) return false
    return normalizeScope(userFloor.apiValue) == normalizeScope(incidentFloor) ||
        normalizeScope(userFloor.displayName) == normalizeScope(incidentFloor)
  }

  fun isUserEligibleForAlert(
    role: UserRole,
    userFloor: FloorScope,
    incident: IncidentItem,
    department: EmployeeDepartment = when (role) {
      UserRole.QUALITY_REPORTER, UserRole.QUALITY_MANAGER, UserRole.AUDITOR -> EmployeeDepartment.QUALITY
      UserRole.IE -> EmployeeDepartment.IE
      UserRole.MANAGEMENT_VIEW -> EmployeeDepartment.MANAGEMENT
      else -> EmployeeDepartment.PRODUCTION
    },
    designation: String = role.displayName,
    assignedLines: List<String> = emptyList(),
    assignedLine: String = "All"
  ): Boolean {
    // Factory Management notifications are intentionally disabled for the initial rollout.
    // Admin is a separate technical role and may still review all alerts.
    if (role == UserRole.ADMIN) return true
    if (department == EmployeeDepartment.MANAGEMENT || role == UserRole.MANAGEMENT_VIEW) return false

    val isProductionHead = department == EmployeeDepartment.PRODUCTION &&
        designation.equals("Production Head", ignoreCase = true)
    val isQualityHead = department == EmployeeDepartment.QUALITY &&
        designation.equals("Quality Head", ignoreCase = true)
    val allFloorHead = isProductionHead || isQualityHead

    val normalizedLine = normalizeScope(incident.lineId)
    val incidentIsFinishing = normalizeScope(incident.floor) == normalizeScope("Finishing") ||
        normalizedLine.startsWith("iron") || normalizedLine.startsWith("packing")
    val floorAllowed = when {
      allFloorHead -> true
      department == EmployeeDepartment.FINISHING ->
        incidentIsFinishing && (userFloor == FloorScope.FINISHING || userFloor == FloorScope.ALL)
      userFloor == FloorScope.ALL -> false
      else -> sameFloor(userFloor, incident.floor)
    }
    if (!floorAllowed) return false

    val lineScope = assignedLines.filter { it.isNotBlank() }.ifEmpty {
      listOf(assignedLine).filter { it.isNotBlank() }
    }
    val allLinesAllowed = lineScope.isEmpty() || lineScope.any {
      it.equals("All", ignoreCase = true) || it.equals("All lines on floor", ignoreCase = true)
    }
    if (!allLinesAllowed && lineScope.none { it.equals(incident.lineId, ignoreCase = true) }) return false

    val departmentCategoryAllowed = when (department) {
      EmployeeDepartment.QUALITY -> incident.category == IncidentCategory.QUALITY_PROBLEM
      EmployeeDepartment.PRODUCTION -> incident.category in setOf(
        IncidentCategory.MACHINE_BREAKDOWN,
        IncidentCategory.INPUT_MATERIAL,
        IncidentCategory.OPERATOR_NA,
        IncidentCategory.LOW_PRODUCTION,
        IncidentCategory.POWER_OUTAGE,
        IncidentCategory.OTHER
      )
      EmployeeDepartment.IE -> incident.category in setOf(
        IncidentCategory.LINE_BALANCING,
        IncidentCategory.LOW_PRODUCTION,
        IncidentCategory.OTHER
      )
      EmployeeDepartment.FINISHING -> incidentIsFinishing
      EmployeeDepartment.MANAGEMENT -> false
    }
    return departmentCategoryAllowed
  }

  fun triggerAlertNotification(
    incident: IncidentItem,
    currentRole: UserRole,
    userFloor: FloorScope,
    department: EmployeeDepartment = EmployeeDepartment.PRODUCTION,
    designation: String = currentRole.displayName,
    assignedLines: List<String> = emptyList(),
    assignedLine: String = "All"
  ) {
    if (!isUserEligibleForAlert(
        currentRole, userFloor, incident, department, designation, assignedLines, assignedLine
      )) {
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

        val alarmDurationMs = when (severity) {
          AlertSeverity.CRITICAL -> 6000
          AlertSeverity.HIGH -> 4200
          AlertSeverity.MEDIUM -> 2800
          AlertSeverity.LOW_INFO -> 1400
        }
        val alarmTone = when (severity) {
          AlertSeverity.CRITICAL -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
          AlertSeverity.HIGH -> ToneGenerator.TONE_PROP_BEEP2
          AlertSeverity.MEDIUM -> ToneGenerator.TONE_PROP_BEEP
          AlertSeverity.LOW_INFO -> ToneGenerator.TONE_PROP_ACK
        }
        activeToneGenerator?.startTone(alarmTone, alarmDurationMs)
      } catch (_: Exception) {}
    }
  }

  private fun postSystemNotification(incident: IncidentItem) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return
      }
    }

    val baseChannelId = when (incident.severity) {
      AlertSeverity.CRITICAL -> CHANNEL_CRITICAL
      AlertSeverity.HIGH -> if (incident.category == IncidentCategory.QUALITY_PROBLEM) CHANNEL_QUALITY else CHANNEL_PRODUCTION
      AlertSeverity.MEDIUM -> CHANNEL_PRODUCTION
      AlertSeverity.LOW_INFO -> CHANNEL_GENERAL
    }
    val useSilentChannel = isSoundMuted() || incident.isSilencedOnDevice
    val channelId = if (useSilentChannel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      "${baseChannelId}_silent"
    } else baseChannelId

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
      AlertSeverity.CRITICAL -> "🚨 CRITICAL ALARM"
      AlertSeverity.HIGH -> "⚠️ HIGH PRIORITY ALERT"
      AlertSeverity.MEDIUM -> "🔔 FACTORY ALERT"
      AlertSeverity.LOW_INFO -> "ℹ️ FACTORY UPDATE"
    }
    val issueType = incident.customNptTypeName.takeIf { it.isNotBlank() } ?: incident.nptType
    val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("Asia/Dhaka")
    }.format(Date(incident.startTimeMillis))
    val contentTitle = "$titlePrefix | ${incident.floor} | Line ${incident.lineId}"
    val contentText = "$issueType: ${incident.description}"
    val detailLines = listOf(
      "Issue: $issueType",
      "Floor / Section: ${incident.floor}",
      "Affected Line: ${incident.lineId}",
      "Description: ${incident.description}",
      "NPT / Process: ${incident.processName.ifBlank { issueType }}",
      "Machine: ${listOf(incident.machineType, incident.machineId.ifBlank { incident.machineRef }).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Not specified" }}",
      "Reported cause: ${incident.reportedCause.ifBlank { "Not specified" }}",
      "Affected manpower: ${incident.affectedManpower}",
      "Reported by: ${incident.reportedBy}",
      "Reported at (Bangladesh time): $timestamp",
      "Current status: ${incident.status.displayName}",
      "Required action: Please acknowledge this alert, contact the responsible team, and update the incident status after action."
    )
    val vibrationPattern = when (incident.severity) {
      AlertSeverity.CRITICAL -> longArrayOf(0, 700, 220, 700, 220, 900)
      AlertSeverity.HIGH -> longArrayOf(0, 500, 180, 500, 180, 700)
      AlertSeverity.MEDIUM -> longArrayOf(0, 400, 180, 400)
      AlertSeverity.LOW_INFO -> longArrayOf(0, 250, 150, 250)
    }

    val notification = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(R.drawable.ic_nfl_logo)
      .setContentTitle(contentTitle)
      .setContentText(contentText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(detailLines.joinToString("\n")))
      .setPriority(
        when (incident.severity) {
          AlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_MAX
          AlertSeverity.HIGH -> NotificationCompat.PRIORITY_HIGH
          AlertSeverity.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
          AlertSeverity.LOW_INFO -> NotificationCompat.PRIORITY_LOW
        }
      )
      .setVibrate(vibrationPattern)
      .setOnlyAlertOnce(false)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    try {
      NotificationManagerCompat.from(context).notify(incident.id.hashCode(), notification)
    } catch (_: SecurityException) {}
  }
}
