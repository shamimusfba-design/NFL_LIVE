package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.example.BuildConfig
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.os.SystemClock
import android.util.Log
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class SheetSyncResult(
  val success: Boolean,
  val message: String,
  val fetchedProduction: List<ProductionRecord> = emptyList(),
  val fetchedQuality: List<QualityRecord> = emptyList(),
  val fetchedSetups: List<SupervisorLineSetup> = emptyList(),
  val fetchedIncidents: List<IncidentItem> = emptyList(),
  val fetchedMonthlyTargets: List<MonthlyFactoryTarget> = emptyList(),
  val fetchedUsers: List<UserAccount> = emptyList(),
  val pushedCount: Int = 0,
  val pulledCount: Int = 0,
  val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
)

class GoogleSheetsStorageService(private val appContext: Context) {

  private val prefs: SharedPreferences =
    appContext.getSharedPreferences("nfl_google_sheets_storage", Context.MODE_PRIVATE)

  val productionSaveCallCount = AtomicInteger(0)
  val qualitySaveCallCount = AtomicInteger(0)
  val setupsSaveCallCount = AtomicInteger(0)
  val incidentsSaveCallCount = AtomicInteger(0)
  val monthlyTargetsSaveCallCount = AtomicInteger(0)
  val usersSaveCallCount = AtomicInteger(0)
  val deviceRequestsSaveCallCount = AtomicInteger(0)
  val auditLogsSaveCallCount = AtomicInteger(0)

  fun resetSaveCallCounts() {
    productionSaveCallCount.set(0)
    qualitySaveCallCount.set(0)
    setupsSaveCallCount.set(0)
    incidentsSaveCallCount.set(0)
    monthlyTargetsSaveCallCount.set(0)
    usersSaveCallCount.set(0)
    deviceRequestsSaveCallCount.set(0)
    auditLogsSaveCallCount.set(0)
  }

  // Trusted backend time offset relative to local clock (updated from server responses)
  @Volatile
  private var serverTimeOffsetMillis: Long = prefs.getLong(KEY_SERVER_TIME_OFFSET, 0L)

  private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  companion object {
    const val DEFAULT_SHEET_ID = "1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0"
    const val BANGLADESH_TIMEZONE_ID = "Asia/Dhaka"
    const val DAILY_ENTRY_START_HOUR = 6 // 06:00 AM Bangladesh Time
    const val DAILY_ENTRY_END_HOUR = 23 // Until 23:59:59 (Midnight) Bangladesh Time
    const val OTP_EXPIRY_MILLIS = 10 * 60 * 1000L // 10 minutes
    const val MAX_OTP_ATTEMPTS = 5
    const val MAX_OTP_RESENDS = 3
    const val OTP_RESEND_COOLDOWN_MILLIS = 60 * 1000L // 60 seconds

    private const val KEY_SHEET_ID = "config_sheet_id"
    private const val KEY_WEB_APP_URL = "config_web_app_url"
    private const val KEY_AUTO_REFRESH = "config_auto_refresh"
    private const val KEY_LAST_SYNC_TIME = "state_last_sync_time"
    private const val KEY_SERVER_TIME_OFFSET = "security_server_time_offset_ms"

    private const val KEY_SAVED_PRODUCTION = "ledger_production_v1"
    private const val KEY_SAVED_QUALITY = "ledger_quality_v1"
    private const val KEY_SAVED_SETUPS = "ledger_setups_v1"
    private const val KEY_SAVED_INCIDENTS = "ledger_incidents_v1"
    private const val KEY_SAVED_MONTHLY = "ledger_monthly_v1"
    private const val KEY_SAVED_AUDIT = "ledger_audit_v1"
    private const val KEY_SAVED_USERS = "ledger_users_rbac_v1"
    private const val KEY_SAVED_DEVICE_REQUESTS = "ledger_device_requests_v1"
    private const val KEY_SERVER_DEVICE_LEDGER = "ledger_server_devices_v1"
    private const val KEY_FIRST_ADMIN_PROVISIONED = "security_first_admin_1015198_provisioned"
    private const val KEY_ACTIVE_SESSION_ID = "security_active_session_id"
    private const val KEY_ACTIVE_SESSION_USER_ID = "security_active_session_user_id"
    private const val KEY_ACTIVE_SESSION_LOGIN_TIME = "security_active_session_login_time_ms"

    fun sha256(input: String): String {
      return try {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { "%02x".format(it) }
      } catch (_: Throwable) {
        input.hashCode().toString()
      }
    }

    fun generateSalt(): String {
      return UUID.randomUUID().toString().replace("-", "").take(16)
    }

    fun hashPassword(password: String, salt: String = ""): String {
      val clean = password.trim()
      return if (salt.isBlank()) {
        sha256(clean)
      } else {
        sha256("$salt:$clean")
      }
    }

    fun verifyPassword(inputPassword: String, storedHash: String, salt: String = ""): Boolean {
      val clean = inputPassword.trim()
      if (clean.isEmpty() || storedHash.isBlank()) return false
      if (salt.isNotBlank() && sha256("$salt:$clean").equals(storedHash, ignoreCase = true)) {
        return true
      }
      // Backward compatibility for unsalted SHA-256 hashes
      return sha256(clean).equals(storedHash, ignoreCase = true)
    }

    fun extractSheetId(input: String): String {
      val trimmed = input.trim()
      val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
      val match = regex.find(trimmed)
      return match?.groupValues?.getOrNull(1) ?: trimmed.ifBlank { DEFAULT_SHEET_ID }
    }

    val GOOGLE_APPS_SCRIPT_TEMPLATE = """
      // NFL LIVE — CENTRAL SECURITY & ACCESS-CONTROL BACKEND (GOOGLE APPS SCRIPT)
      // Keeps the Google Sheet PRIVATE while validating all requests on the backend.
      // Initial Admin ID: 1015198 | Primary Admin Gmail: shamim.sustipe15@gmail.com | Timezone: Asia/Dhaka
      // See backend/google_apps_script_security_backend.gs for full script.
      const SHEET_ID = '1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0';
      const INITIAL_ADMIN_EMPLOYEE_ID = '1015198';
      const PRIMARY_ADMIN_GMAIL = 'shamim.sustipe15@gmail.com';
      const TIMEZONE = 'Asia/Dhaka';
      const MAX_DEVICES_PER_EMPLOYEE = 1;
    """.trimIndent()
  }

  fun isFirstAdminBootstrapLocked(): Boolean =
    prefs.getBoolean(KEY_FIRST_ADMIN_PROVISIONED, false)

  fun setFirstAdminBootstrapLocked(locked: Boolean) {
    prefs.edit().putBoolean(KEY_FIRST_ADMIN_PROVISIONED, locked).apply()
  }

  fun getLocalActiveSessionId(): String =
    prefs.getString(KEY_ACTIVE_SESSION_ID, "").orEmpty()

  fun setLocalActiveSessionId(sessionId: String) {
    if (sessionId.isBlank()) {
      clearLocalActiveSession()
    } else {
      prefs.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
    }
  }

  fun getLocalActiveSessionUserId(): String =
    prefs.getString(KEY_ACTIVE_SESSION_USER_ID, "").orEmpty()

  fun getLocalActiveSessionLoginTime(): Long =
    prefs.getLong(KEY_ACTIVE_SESSION_LOGIN_TIME, 0L)

  fun saveLocalActiveSession(employeeId: String, sessionId: String, loginTimeMillis: Long) {
    prefs.edit()
      .putString(KEY_ACTIVE_SESSION_USER_ID, employeeId.trim())
      .putString(KEY_ACTIVE_SESSION_ID, sessionId.trim())
      .putLong(KEY_ACTIVE_SESSION_LOGIN_TIME, loginTimeMillis)
      .apply()
  }

  fun clearLocalActiveSession() {
    prefs.edit()
      .remove(KEY_ACTIVE_SESSION_USER_ID)
      .remove(KEY_ACTIVE_SESSION_ID)
      .remove(KEY_ACTIVE_SESSION_LOGIN_TIME)
      .apply()
  }

  /**
   * Returns a deterministic hardware-bound device identifier so clearing app storage or
   * reinstalling the app on the same physical device does NOT reset the device identity.
   */
  fun getDeviceId(): String {
    val androidId = try {
      Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    } catch (_: Throwable) {
      ""
    }
    val hardwareFingerprint = "${Build.MANUFACTURER}|${Build.BRAND}|${Build.MODEL}|${Build.DEVICE}|$androidId"
    return "DEV-" + sha256(hardwareFingerprint).take(16).uppercase(Locale.US)
  }

  fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }.orEmpty().ifBlank { "Android" }
    val model = Build.MODEL.orEmpty().ifBlank { "Device" }
    return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
  }

  /**
   * Updates the trusted server time offset from HTTP Date header or Apps Script serverTimeMillis.
   */
  fun updateTrustedServerTime(serverEpochMillis: Long) {
    if (serverEpochMillis > 1700000000000L) {
      val offset = serverEpochMillis - System.currentTimeMillis()
      serverTimeOffsetMillis = offset
      prefs.edit().putLong(KEY_SERVER_TIME_OFFSET, offset).apply()
    }
  }

  private fun captureServerDateHeader(dateHeader: String?) {
    if (dateHeader.isNullOrBlank()) return
    try {
      val httpFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
      val parsed = httpFormat.parse(dateHeader)
      if (parsed != null) {
        updateTrustedServerTime(parsed.time)
      }
    } catch (_: Throwable) {
    }
  }

  fun getTrustedBangladeshTimeMillis(): Long = System.currentTimeMillis() + serverTimeOffsetMillis

  fun getBangladeshTimeZone(): TimeZone = bangladeshTz

  fun getTrustedBangladeshCalendar(customMillis: Long? = null): Calendar {
    val cal = Calendar.getInstance(getBangladeshTimeZone(), Locale.US)
    cal.timeInMillis = customMillis ?: getTrustedBangladeshTimeMillis()
    return cal
  }

  fun getCurrentBangladeshDateIso(customMillis: Long? = null): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      timeZone = getBangladeshTimeZone()
    }
    return fmt.format(Date(customMillis ?: getTrustedBangladeshTimeMillis()))
  }

  fun getCurrentBangladeshHour(customMillis: Long? = null): Int {
    return getTrustedBangladeshCalendar(customMillis).get(Calendar.HOUR_OF_DAY)
  }

  fun formatBangladeshTimestamp(customMillis: Long? = null): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'BST'", Locale.US).apply {
      timeZone = getBangladeshTimeZone()
    }
    return fmt.format(Date(customMillis ?: getTrustedBangladeshTimeMillis()))
  }

  fun getCurrentBangladeshTimeFormatted(customMillis: Long? = null): String =
    formatBangladeshTimestamp(customMillis)

  fun sha256(input: String): String = Companion.sha256(input)

  /**
   * Daily Production and Quality entry is permitted from 06:00 AM until midnight (23:59:59)
   * Bangladesh time (Asia/Dhaka).
   */
  fun isWithinBangladeshEntryWindow(customMillis: Long? = null): Boolean {
    val hour = getCurrentBangladeshHour(customMillis)
    return hour in DAILY_ENTRY_START_HOUR..DAILY_ENTRY_END_HOUR
  }

  fun isWithinDailyEntryWindow(customMillis: Long? = null): Boolean =
    isWithinBangladeshEntryWindow(customMillis)

  private val normalizedDateCache = java.util.concurrent.ConcurrentHashMap<String, String>()
  private val bangladeshTz: TimeZone = TimeZone.getTimeZone(BANGLADESH_TIMEZONE_ID)

  /**
   * Normalizes a date string ("yyyy-MM-dd" or "dd MMM yyyy") into standard ISO "yyyy-MM-dd".
   * Uses a fast-path check and memoization cache to avoid Regex/SimpleDateFormat allocations inside UI loops.
   */
  fun normalizeToIsoDate(dateInput: String): String {
    val trimmed = dateInput.trim()
    if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-' &&
      trimmed[0].isDigit() && trimmed[1].isDigit() && trimmed[2].isDigit() && trimmed[3].isDigit()
    ) {
      return trimmed
    }
    normalizedDateCache[trimmed]?.let { return it }
    val formats = listOf("dd MMM yyyy", "d MMM yyyy", "yyyy/MM/dd", "dd-MM-yyyy")
    for (pattern in formats) {
      try {
        val parser = SimpleDateFormat(pattern, Locale.US).apply {
          timeZone = bangladeshTz
          isLenient = false
        }
        val parsed = parser.parse(trimmed)
        if (parsed != null) {
          val outFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = bangladeshTz
          }
          val formatted = outFmt.format(parsed)
          normalizedDateCache[trimmed] = formatted
          return formatted
        }
      } catch (_: Throwable) {
      }
    }
    val fallback = trimmed.take(10)
    normalizedDateCache[trimmed] = fallback
    return fallback
  }

  fun getConfiguredSheetId(): String {
    val saved = prefs.getString(KEY_SHEET_ID, null)
    if (!saved.isNullOrBlank()) return extractSheetId(saved)
    return DEFAULT_SHEET_ID
  }

  fun setConfiguredSheetId(rawInput: String): String {
    val cleanId = extractSheetId(rawInput)
    prefs.edit().putString(KEY_SHEET_ID, cleanId).apply()
    return cleanId
  }

  fun getConfiguredWebAppUrl(): String {
    val saved = prefs.getString(KEY_WEB_APP_URL, null)
    if (!saved.isNullOrBlank() && !saved.contains("/default/exec")) return saved.trim()
    return ""
  }

  fun setConfiguredWebAppUrl(url: String) {
    prefs.edit().putString(KEY_WEB_APP_URL, url.trim()).apply()
  }

  fun isAutoRefreshEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_REFRESH, true)

  fun setAutoRefreshEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
  }

  fun getLastSyncTime(): String {
    return prefs.getString(KEY_LAST_SYNC_TIME, null)
      ?: SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
  }

  private fun saveLastSyncTime(time: String) {
    prefs.edit().putString(KEY_LAST_SYNC_TIME, time).apply()
  }

  fun hasValidWebAppUrl(url: String = getConfiguredWebAppUrl()): Boolean {
    return url.startsWith("https://") && !url.contains("/default/exec")
  }

  // =========================================================================
  // LOCAL PERSISTENT STORAGE (JSON SERIALIZATION FOR USER SUBMISSIONS)
  // =========================================================================

  fun saveLocalProductionEntries(entries: List<ProductionRecord>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    productionSaveCallCount.incrementAndGet()
    val nonDemo = entries.filter { !it.isDemo }
    val arr = JSONArray()
    nonDemo.forEach { item ->
      arr.put(JSONObject().apply {
        put("id", item.id)
        put("submissionId", item.submissionId)
        put("lineId", item.lineId)
        put("floor", item.floor)
        put("date", item.date)
        put("hourSlot", item.hourSlot)
        put("styleNumber", item.styleNumber)
        put("outputQty", item.outputQty)
        put("remarks", item.remarks)
        put("timestamp", item.timestamp)
        put("syncStatus", item.syncStatus.name)
        put("revision", item.revision)
        put("modifiedBy", item.modifiedBy)
        put("isConfirmedZero", item.isConfirmedZero)
      })
    }
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_PRODUCTION, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalProductionEntries rows=${nonDemo.size} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun loadLocalProductionEntries(): List<ProductionRecord> {
    val raw = prefs.getString(KEY_SAVED_PRODUCTION, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        ProductionRecord(
          id = obj.optString("id", UUID.randomUUID().toString()),
          submissionId = obj.optString("submissionId", UUID.randomUUID().toString()),
          lineId = obj.optString("lineId", "K1"),
          floor = obj.optString("floor", "Sew 2"),
          date = obj.optString("date", todayDateStr()),
          hourSlot = obj.optString("hourSlot", "08:00 - 09:00"),
          styleNumber = obj.optString("styleNumber", ""),
          outputQty = obj.optInt("outputQty", 0),
          remarks = obj.optString("remarks", ""),
          timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
          syncStatus = runCatching { SyncStatus.valueOf(obj.optString("syncStatus", "SYNCED")) }.getOrDefault(SyncStatus.SYNCED),
          revision = obj.optInt("revision", 1),
          modifiedBy = obj.optString("modifiedBy", "Production Reporter"),
          isConfirmedZero = obj.optBoolean("isConfirmedZero", false),
          isDemo = false
        )
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveLocalQualityEntries(entries: List<QualityRecord>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    qualitySaveCallCount.incrementAndGet()
    val nonDemo = entries.filter { !it.isDemo }
    val arr = JSONArray()
    nonDemo.forEach { item ->
      val defectsArr = JSONArray()
      item.defects.forEach { d ->
        defectsArr.put(JSONObject().apply {
          put("defectType", d.defectType)
          put("count", d.count)
        })
      }
      arr.put(JSONObject().apply {
        put("id", item.id)
        put("submissionId", item.submissionId)
        put("lineId", item.lineId)
        put("floor", item.floor)
        put("date", item.date)
        put("hourSlot", item.hourSlot)
        put("styleNumber", item.styleNumber)
        put("checkedGarments", item.checkedGarments)
        put("defectiveGarments", item.defectiveGarments)
        put("defects", defectsArr)
        put("totalDefects", item.totalDefects)
        put("remarks", item.remarks)
        put("defectSent", item.defectSent)
        put("defectReceived", item.defectReceived)
        put("defectBalance", item.defectBalance)
        put("spotSent", item.spotSent)
        put("spotReceived", item.spotReceived)
        put("spotBalance", item.spotBalance)
        put("timestamp", item.timestamp)
        put("syncStatus", item.syncStatus.name)
        put("revision", item.revision)
        put("modifiedBy", item.modifiedBy)
        put("isConfirmedZero", item.isConfirmedZero)
      })
    }
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_QUALITY, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalQualityEntries rows=${nonDemo.size} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun loadLocalQualityEntries(): List<QualityRecord> {
    val raw = prefs.getString(KEY_SAVED_QUALITY, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val defArr = obj.optJSONArray("defects") ?: JSONArray()
        val defects = (0 until defArr.length()).mapNotNull { j ->
          val dObj = defArr.optJSONObject(j) ?: return@mapNotNull null
          DefectItem(dObj.optString("defectType", "Other"), dObj.optInt("count", 1))
        }
        QualityRecord(
          id = obj.optString("id", UUID.randomUUID().toString()),
          submissionId = obj.optString("submissionId", UUID.randomUUID().toString()),
          lineId = obj.optString("lineId", "K1"),
          floor = obj.optString("floor", "Sew 2"),
          date = obj.optString("date", todayDateStr()),
          hourSlot = obj.optString("hourSlot", "08:00 - 09:00"),
          styleNumber = obj.optString("styleNumber", ""),
          checkedGarments = obj.optInt("checkedGarments", 0),
          defectiveGarments = obj.optInt("defectiveGarments", 0),
          defects = defects,
          totalDefects = obj.optInt("totalDefects", defects.sumOf { it.count }),
          remarks = obj.optString("remarks", ""),
          defectSent = obj.optInt("defectSent", 0),
          defectReceived = obj.optInt("defectReceived", 0),
          defectBalance = obj.optInt("defectBalance", 0),
          spotSent = obj.optInt("spotSent", 0),
          spotReceived = obj.optInt("spotReceived", 0),
          spotBalance = obj.optInt("spotBalance", 0),
          timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
          syncStatus = runCatching { SyncStatus.valueOf(obj.optString("syncStatus", "SYNCED")) }.getOrDefault(SyncStatus.SYNCED),
          revision = obj.optInt("revision", 1),
          modifiedBy = obj.optString("modifiedBy", "Quality Inspector"),
          isConfirmedZero = obj.optBoolean("isConfirmedZero", false),
          isDemo = false
        )
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveLocalSetups(setups: List<SupervisorLineSetup>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    setupsSaveCallCount.incrementAndGet()
    val nonDemo = setups.filter { !it.isDemo }
    val arr = JSONArray()
    nonDemo.forEach { s ->
      arr.put(JSONObject().apply {
        put("id", s.id)
        put("lineId", s.lineId)
        put("floor", s.floor)
        put("date", s.date)
        put("styleNumber", s.styleNumber)
        put("itemName", s.itemName)
        put("orderNumber", s.orderNumber)
        put("smv", s.smv)
        put("targetHourlyPcs", s.targetHourlyPcs)
        put("targetDailyPcs", s.targetDailyPcs)
        put("targetEfficiencyPct", s.targetEfficiencyPct)
        put("operatorsCount", s.operatorsCount)
        put("machinesCount", s.machinesCount)
        put("helpersCount", s.helpersCount)
        put("finishingStaffCount", s.finishingStaffCount)
        put("includeHelpersInEfficiency", s.includeHelpersInEfficiency)
        put("workingMinutesPerHour", s.workingMinutesPerHour)
        put("workingHoursSoFar", s.workingHoursSoFar)
        put("totalShiftHours", s.totalShiftHours)
        put("snapshotTimestamp", s.snapshotTimestamp)
      })
    }
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_SETUPS, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalSetups rows=${nonDemo.size} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun loadLocalSetups(): List<SupervisorLineSetup> {
    val raw = prefs.getString(KEY_SAVED_SETUPS, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        SupervisorLineSetup(
          id = obj.optString("id", UUID.randomUUID().toString()),
          lineId = obj.optString("lineId", "K1"),
          floor = obj.optString("floor", "Sew 2"),
          date = obj.optString("date", todayDateStr()),
          styleNumber = obj.optString("styleNumber", ""),
          itemName = obj.optString("itemName", "Polo Shirt"),
          orderNumber = obj.optString("orderNumber", "PO-88210"),
          smv = obj.optDouble("smv", 14.50),
          targetHourlyPcs = obj.optInt("targetHourlyPcs", 60),
          targetDailyPcs = obj.optInt("targetDailyPcs", 480),
          targetEfficiencyPct = obj.optDouble("targetEfficiencyPct", 75.0),
          operatorsCount = obj.optInt("operatorsCount", 20),
          machinesCount = obj.optInt("machinesCount", 20),
          helpersCount = obj.optInt("helpersCount", 2),
          finishingStaffCount = obj.optInt("finishingStaffCount", 4),
          includeHelpersInEfficiency = obj.optBoolean("includeHelpersInEfficiency", false),
          workingMinutesPerHour = obj.optInt("workingMinutesPerHour", 60),
          workingHoursSoFar = obj.optInt("workingHoursSoFar", 7),
          totalShiftHours = obj.optInt("totalShiftHours", 8),
          snapshotTimestamp = obj.optLong("snapshotTimestamp", System.currentTimeMillis()),
          isDemo = false
        )
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveLocalIncidents(incidents: List<IncidentItem>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    incidentsSaveCallCount.incrementAndGet()
    val nonDemo = incidents.filter { !it.isDemo }
    val arr = JSONArray()
    nonDemo.forEach { inc ->
      arr.put(JSONObject().apply {
        put("id", inc.id)
        put("submissionId", inc.submissionId)
        put("lineId", inc.lineId)
        put("floor", inc.floor)
        put("category", inc.category.name)
        put("styleNumber", inc.styleNumber)
        put("nptType", inc.nptType)
        put("customNptTypeName", inc.customNptTypeName)
        put("processName", inc.processName)
        put("machineType", inc.machineType)
        put("machineRef", inc.machineRef)
        put("description", inc.description)
        put("reportedCause", inc.reportedCause)
        put("impactLevel", inc.impactLevel)
        put("affectedManpower", inc.affectedManpower)
        put("startTimeMillis", inc.startTimeMillis)
        if (inc.acknowledgedTimeMillis != null) put("acknowledgedTimeMillis", inc.acknowledgedTimeMillis)
        if (inc.acknowledgedBy != null) put("acknowledgedBy", inc.acknowledgedBy)
        if (inc.mechanicStartTimeMillis != null) put("mechanicStartTimeMillis", inc.mechanicStartTimeMillis)
        if (inc.mechanicName != null) put("mechanicName", inc.mechanicName)
        put("isMaintenanceStarted", inc.isMaintenanceStarted)
        if (inc.endTimeMillis != null) put("endTimeMillis", inc.endTimeMillis)
        if (inc.resolvedBy != null) put("resolvedBy", inc.resolvedBy)
        put("resolutionNote", inc.resolutionNote)
        put("status", inc.status.name)
        put("isMutedOnCurrentDevice", inc.isMutedOnCurrentDevice)
        put("reportedBy", inc.reportedBy)
        val commentsArr = JSONArray()
        inc.comments.forEach { c ->
          commentsArr.put(JSONObject().apply {
            put("id", c.id)
            put("incidentId", c.incidentId)
            put("userName", c.userName)
            put("userRole", c.userRole)
            put("commentText", c.commentText)
            put("timestamp", c.timestamp)
          })
        }
        put("comments", commentsArr)
      })
    }
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_INCIDENTS, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalIncidents rows=${nonDemo.size} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun loadLocalIncidents(): List<IncidentItem> {
    val raw = prefs.getString(KEY_SAVED_INCIDENTS, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val commentsList = mutableListOf<IncidentComment>()
        val commentsArr = obj.optJSONArray("comments")
        if (commentsArr != null) {
          for (cIdx in 0 until commentsArr.length()) {
            val cObj = commentsArr.optJSONObject(cIdx) ?: continue
            commentsList.add(
              IncidentComment(
                id = cObj.optString("id", UUID.randomUUID().toString()),
                incidentId = cObj.optString("incidentId", ""),
                userName = cObj.optString("userName", "User"),
                userRole = cObj.optString("userRole", ""),
                commentText = cObj.optString("commentText", ""),
                timestamp = cObj.optLong("timestamp", System.currentTimeMillis())
              )
            )
          }
        }
        IncidentItem(
          id = obj.optString("id", UUID.randomUUID().toString()),
          submissionId = obj.optString("submissionId", UUID.randomUUID().toString()),
          lineId = obj.optString("lineId", "K1"),
          floor = obj.optString("floor", "Sew 2"),
          category = runCatching { IncidentCategory.valueOf(obj.optString("category", "MACHINE_BREAKDOWN")) }
            .getOrDefault(IncidentCategory.MACHINE_BREAKDOWN),
          styleNumber = obj.optString("styleNumber", ""),
          nptType = obj.optString("nptType", "Machine Breakdown"),
          customNptTypeName = obj.optString("customNptTypeName", ""),
          processName = obj.optString("processName", ""),
          machineType = obj.optString("machineType", ""),
          machineRef = obj.optString("machineRef", ""),
          description = obj.optString("description", ""),
          reportedCause = obj.optString("reportedCause", ""),
          impactLevel = obj.optString("impactLevel", "HIGH"),
          affectedManpower = obj.optInt("affectedManpower", 6),
          startTimeMillis = obj.optLong("startTimeMillis", System.currentTimeMillis()),
          acknowledgedTimeMillis = if (obj.has("acknowledgedTimeMillis")) obj.optLong("acknowledgedTimeMillis") else null,
          acknowledgedBy = if (obj.has("acknowledgedBy")) obj.optString("acknowledgedBy") else null,
          mechanicStartTimeMillis = if (obj.has("mechanicStartTimeMillis")) obj.optLong("mechanicStartTimeMillis") else null,
          mechanicName = if (obj.has("mechanicName")) obj.optString("mechanicName") else null,
          isMaintenanceStarted = obj.optBoolean("isMaintenanceStarted", false),
          endTimeMillis = if (obj.has("endTimeMillis")) obj.optLong("endTimeMillis") else null,
          resolvedBy = if (obj.has("resolvedBy")) obj.optString("resolvedBy") else null,
          resolutionNote = obj.optString("resolutionNote", ""),
          status = runCatching { IncidentStatus.valueOf(obj.optString("status", "OPEN")) }.getOrDefault(IncidentStatus.OPEN),
          isMutedOnCurrentDevice = obj.optBoolean("isMutedOnCurrentDevice", false),
          reportedBy = obj.optString("reportedBy", "Supervisor"),
          comments = commentsList,
          isDemo = false
        )
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveLocalMonthlyTargets(targets: List<MonthlyFactoryTarget>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    monthlyTargetsSaveCallCount.incrementAndGet()
    val arr = JSONArray()
    targets.forEach { t ->
      arr.put(JSONObject().apply {
        put("floor", t.floor)
        put("monthYear", t.monthYear)
        put("targetEfficiencyPct", t.targetEfficiencyPct)
        put("updateEfficiencyPct", t.updateEfficiencyPct)
        put("targetOqlPct", t.targetOqlPct)
        put("targetDhuPct", t.targetDhuPct)
        put("friPassRatePct", t.friPassRatePct)
        put("bsOqlPct", t.bsOqlPct)
        put("lastUpdatedBy", t.lastUpdatedBy)
        put("lastUpdatedMillis", t.lastUpdatedMillis)
        put("lineId", t.lineId)
        put("monthlyTargetPcs", t.monthlyTargetPcs)
        put("monthlyAchievementPcs", t.monthlyAchievementPcs)
      })
    }
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_MONTHLY, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalMonthlyTargets rows=${targets.size} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun loadLocalMonthlyTargets(): List<MonthlyFactoryTarget> {
    val raw = prefs.getString(KEY_SAVED_MONTHLY, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        MonthlyFactoryTarget(
          floor = obj.optString("floor", "Sew 2"),
          monthYear = obj.optString("monthYear", "October 2026"),
          targetEfficiencyPct = obj.optDouble("targetEfficiencyPct", 72.0),
          updateEfficiencyPct = obj.optDouble("updateEfficiencyPct", 68.5),
          targetOqlPct = obj.optDouble("targetOqlPct", 3.50),
          targetDhuPct = obj.optDouble("targetDhuPct", 2.00),
          friPassRatePct = obj.optDouble("friPassRatePct", 100.0),
          bsOqlPct = obj.optDouble("bsOqlPct", 2.40),
          lastUpdatedBy = obj.optString("lastUpdatedBy", "Management / IE"),
          lastUpdatedMillis = obj.optLong("lastUpdatedMillis", System.currentTimeMillis()),
          lineId = obj.optString("lineId", ""),
          monthlyTargetPcs = obj.optDouble("monthlyTargetPcs", 0.0),
          monthlyAchievementPcs = obj.optDouble("monthlyAchievementPcs", 0.0)
        )
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveLocalUsers(users: List<UserAccount>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    usersSaveCallCount.incrementAndGet()
    val arr = JSONArray()
    val serverDevicesMap = loadServerDeviceMap().toMutableMap()
    users.forEach { u ->
      val devicesArr = JSONArray()
      u.approvedDevices.take(MAX_APPROVED_DEVICES_PER_USER).forEach { d ->
        devicesArr.put(JSONObject().apply {
          put("deviceId", d.deviceId)
          put("deviceName", d.deviceName)
          put("approvedAtMillis", d.approvedAtMillis)
          put("approvedBy", d.approvedBy)
          put("lastActiveMillis", d.lastActiveMillis)
        })
      }
      serverDevicesMap[u.id.lowercase(Locale.US)] = u.approvedDevices.take(MAX_APPROVED_DEVICES_PER_USER)
      val effectiveSalt = u.passwordSalt
      val effectiveHash = when {
        u.passwordHash.isNotBlank() -> u.passwordHash
        u.password.isNotBlank() -> hashPassword(u.password, effectiveSalt)
        else -> ""
      }
      arr.put(JSONObject().apply {
        put("id", u.id)
        put("employeeId", u.employeeId.ifBlank { u.id })
        put("name", u.name)
        // Never persist plaintext passwords locally
        put("password", "")
        put("passwordHash", effectiveHash)
        put("passwordSalt", effectiveSalt)
        put("designation", u.designation)
        put("department", u.department)
        put("requestedDesignation", u.requestedDesignation)
        put("requestedFloor", u.requestedFloor.name)
        put("role", u.role.name)
        put("floor", u.floor.name)
        put("authorizedFloorScope", u.authorizedFloorScope.name)
        put("assignedLine", u.assignedLine)
        put("isAdmin", u.isAdmin)
        put("mustChangePassword", u.mustChangePassword)
        put("activeSessionId", u.activeSessionId)
        put("authorizedDeviceId", u.authorizedDeviceId)
        put("authorizedDeviceName", u.authorizedDeviceName)
        put("rejectionReason", u.rejectionReason)
        put("createdAtMillis", u.createdAtMillis)
        put("updatedAtMillis", u.updatedAtMillis)
        put("approvedBy", u.approvedBy)
        put("approvedAtMillis", u.approvedAtMillis)
        put("canViewDashboard", u.canViewDashboard)
        put("canViewTvDashboard", u.canViewTvDashboard)
        put("canViewMonthlyEfficiency", u.canViewMonthlyEfficiency)
        put("canViewMonthlyDhu", u.canViewMonthlyDhu)
        put("canViewMonthlyOql", u.canViewMonthlyOql)
        put("canViewLineDeepDive", u.canViewLineDeepDive)
        put("canViewAuditLogs", u.canViewAuditLogs)
        put("canExportReports", u.canExportReports)
        put("canProductionWrite", u.canProductionWrite)
        put("canFinishingWrite", u.canFinishingWrite)
        put("canQualityWrite", u.canQualityWrite)
        put("canOqlWrite", u.canOqlWrite)
        put("canNptWrite", u.canNptWrite)
        put("canMorningTargetWrite", u.canMorningTargetWrite)
        put("canMonthlyTargetWrite", u.canMonthlyTargetWrite)
        put("canTargetRevisionWrite", u.canTargetRevisionWrite)
        put("isActive", u.isActive)
        put("accountStatus", u.accountStatus.name)
        put("approvedDevices", devicesArr)
        put("maxDevices", MAX_APPROVED_DEVICES_PER_USER)
      })
    }
    saveServerDeviceMap(serverDevicesMap)
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_USERS, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalUsers rows=${users.size} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun loadLocalUsers(): List<UserAccount> {
    val raw = prefs.getString(KEY_SAVED_USERS, null) ?: return emptyList()
    val serverDevicesMap = loadServerDeviceMap()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val role = runCatching { UserRole.valueOf(obj.optString("role", "SUPERVISOR")) }.getOrDefault(UserRole.SUPERVISOR)
        val floor = runCatching { FloorScope.valueOf(obj.optString("floor", "SEW_2")) }.getOrDefault(FloorScope.SEW_2)
        val requestedFloor = runCatching { FloorScope.valueOf(obj.optString("requestedFloor", floor.name)) }.getOrDefault(floor)
        val authorizedScope = runCatching { FloorScope.valueOf(obj.optString("authorizedFloorScope", floor.name)) }.getOrDefault(floor)
        val isAdmin = obj.optBoolean("isAdmin", role == UserRole.ADMIN)
        val status = runCatching { AccountStatus.valueOf(obj.optString("accountStatus", "APPROVED")) }.getOrDefault(AccountStatus.APPROVED)
        val userId = obj.optString("id", obj.optString("employeeId", ""))
        val devArr = obj.optJSONArray("approvedDevices")
        val parsedDevices = if (devArr != null) {
          (0 until devArr.length()).mapNotNull { j ->
            val dObj = devArr.optJSONObject(j) ?: return@mapNotNull null
            ApprovedDevice(
              deviceId = dObj.optString("deviceId", ""),
              deviceName = dObj.optString("deviceName", "Android Device"),
              approvedAtMillis = dObj.optLong("approvedAtMillis", System.currentTimeMillis()),
              approvedBy = dObj.optString("approvedBy", INITIAL_ADMIN_EMPLOYEE_ID),
              lastActiveMillis = dObj.optLong("lastActiveMillis", System.currentTimeMillis())
            )
          }.filter { it.deviceId.isNotBlank() }
        } else {
          serverDevicesMap[userId.lowercase(Locale.US)] ?: emptyList()
        }.take(MAX_APPROVED_DEVICES_PER_USER)
        val legacyPassword = obj.optString("password", "")
        val storedSalt = obj.optString("passwordSalt", "")
        val storedHash = obj.optString("passwordHash", "").ifBlank {
          if (legacyPassword.isNotBlank()) hashPassword(legacyPassword, storedSalt) else ""
        }
        val firstDevice = parsedDevices.firstOrNull()
        UserAccount(
          id = userId,
          employeeId = userId,
          name = obj.optString("name", ""),
          password = "",
          passwordHash = storedHash,
          passwordSalt = storedSalt,
          designation = obj.optString("designation", if (isAdmin) "Admin" else role.displayName),
          department = obj.optString("department", role.group),
          requestedDesignation = obj.optString("requestedDesignation", obj.optString("designation", role.displayName)),
          requestedFloor = requestedFloor,
          role = role,
          floor = floor,
          authorizedFloorScope = if (isAdmin) FloorScope.ALL else authorizedScope,
          assignedLine = obj.optString("assignedLine", "K1"),
          isAdmin = isAdmin,
          mustChangePassword = obj.optBoolean("mustChangePassword", false),
          activeSessionId = obj.optString("activeSessionId", ""),
          authorizedDeviceId = obj.optString("authorizedDeviceId", firstDevice?.deviceId.orEmpty()),
          authorizedDeviceName = obj.optString("authorizedDeviceName", firstDevice?.deviceName.orEmpty()),
          rejectionReason = obj.optString("rejectionReason", ""),
          createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
          updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
          approvedBy = obj.optString("approvedBy", ""),
          approvedAtMillis = obj.optLong("approvedAtMillis", 0L),
          canViewDashboard = obj.optBoolean("canViewDashboard", true),
          canViewTvDashboard = obj.optBoolean("canViewTvDashboard", true),
          canViewMonthlyEfficiency = obj.optBoolean("canViewMonthlyEfficiency", true),
          canViewMonthlyDhu = obj.optBoolean("canViewMonthlyDhu", true),
          canViewMonthlyOql = obj.optBoolean("canViewMonthlyOql", true),
          canViewLineDeepDive = obj.optBoolean("canViewLineDeepDive", true),
          canViewAuditLogs = obj.optBoolean("canViewAuditLogs", isAdmin),
          canExportReports = obj.optBoolean("canExportReports", isAdmin),
          canProductionWrite = obj.optBoolean("canProductionWrite", isAdmin),
          canFinishingWrite = obj.optBoolean("canFinishingWrite", isAdmin),
          canQualityWrite = obj.optBoolean("canQualityWrite", isAdmin),
          canOqlWrite = obj.optBoolean("canOqlWrite", isAdmin),
          canNptWrite = obj.optBoolean("canNptWrite", isAdmin),
          canMorningTargetWrite = obj.optBoolean("canMorningTargetWrite", isAdmin),
          canMonthlyTargetWrite = obj.optBoolean("canMonthlyTargetWrite", isAdmin),
          canTargetRevisionWrite = obj.optBoolean("canTargetRevisionWrite", isAdmin),
          isActive = obj.optBoolean("isActive", status == AccountStatus.APPROVED),
          accountStatus = status,
          approvedDevices = parsedDevices,
          maxDevices = MAX_APPROVED_DEVICES_PER_USER
        )
      }.filter { it.id.isNotBlank() }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  /**
   * Server-side device ledger mirror so device counts survive user list resets.
   */
  fun loadServerDeviceMap(): Map<String, List<ApprovedDevice>> {
    val raw = prefs.getString(KEY_SERVER_DEVICE_LEDGER, null) ?: return emptyMap()
    return try {
      val root = JSONObject(raw)
      val out = mutableMapOf<String, List<ApprovedDevice>>()
      root.keys().forEach { empId ->
        val arr = root.optJSONArray(empId) ?: return@forEach
        val list = (0 until arr.length()).mapNotNull { idx ->
          val dObj = arr.optJSONObject(idx) ?: return@mapNotNull null
          ApprovedDevice(
            deviceId = dObj.optString("deviceId", ""),
            deviceName = dObj.optString("deviceName", "Android Device"),
            approvedAtMillis = dObj.optLong("approvedAtMillis", 0L),
            approvedBy = dObj.optString("approvedBy", INITIAL_ADMIN_EMPLOYEE_ID),
            lastActiveMillis = dObj.optLong("lastActiveMillis", 0L)
          )
        }.filter { it.deviceId.isNotBlank() }.take(MAX_APPROVED_DEVICES_PER_USER)
        out[empId.lowercase(Locale.US)] = list
      }
      out
    } catch (_: Throwable) {
      emptyMap()
    }
  }

  fun saveServerDeviceMap(map: Map<String, List<ApprovedDevice>>) {
    val root = JSONObject()
    map.forEach { (empId, devices) ->
      val arr = JSONArray()
      devices.take(MAX_APPROVED_DEVICES_PER_USER).forEach { d ->
        arr.put(JSONObject().apply {
          put("deviceId", d.deviceId)
          put("deviceName", d.deviceName)
          put("approvedAtMillis", d.approvedAtMillis)
          put("approvedBy", d.approvedBy)
          put("lastActiveMillis", d.lastActiveMillis)
        })
      }
      root.put(empId.lowercase(Locale.US), arr)
    }
    prefs.edit().putString(KEY_SERVER_DEVICE_LEDGER, root.toString()).apply()
  }

  fun saveLocalDeviceRequests(requests: List<DeviceApprovalRequest>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    deviceRequestsSaveCallCount.incrementAndGet()
    val arr = JSONArray()
    requests.forEach { r ->
      arr.put(JSONObject().apply {
        put("requestId", r.requestId)
        put("userId", r.userId.ifBlank { r.employeeId })
        put("userName", r.userName.ifBlank { r.employeeName })
        put("employeeId", r.employeeId.ifBlank { r.userId })
        put("employeeName", r.employeeName.ifBlank { r.userName })
        put("role", r.role.name)
        put("designation", r.designation)
        put("department", r.department)
        put("requestedFloor", r.requestedFloor)
        put("deviceId", r.deviceId)
        put("deviceName", r.deviceName)
        put("previousDeviceId", r.previousDeviceId)
        put("previousDeviceName", r.previousDeviceName)
        put("requestType", r.requestType)
        put("requestedAtMillis", r.requestedAtMillis)
        put("status", r.status)
        put("otpHash", r.otpHash.ifBlank { r.verificationCodeHash })
        put("verificationCodeHash", r.verificationCodeHash.ifBlank { r.otpHash })
        put("otpExpiresAtMillis", r.otpExpiresAtMillis)
        put("codeExpiresAtMillis", r.codeExpiresAtMillis)
        put("otpAttempts", r.otpAttempts)
        put("attemptCount", r.attemptCount)
        put("maxAttempts", r.maxAttempts)
        put("resendCount", r.resendCount)
        put("maxResends", r.maxResends)
        put("lastSentAtMillis", r.lastSentAtMillis)
        put("otpUsed", r.otpUsed || r.isCodeUsed)
        put("isCodeUsed", r.isCodeUsed || r.otpUsed)
        put("emailDeliveryStatus", r.emailDeliveryStatus)
        put("reason", r.reason)
      })
    }
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_DEVICE_REQUESTS, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalDeviceRequests rows=${requests.size} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun saveDeviceApprovalRequests(requests: List<DeviceApprovalRequest>) =
    saveLocalDeviceRequests(requests)

  fun loadLocalDeviceRequests(): List<DeviceApprovalRequest> {
    val raw = prefs.getString(KEY_SAVED_DEVICE_REQUESTS, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val uid = obj.optString("userId", obj.optString("employeeId", ""))
        val uname = obj.optString("userName", obj.optString("employeeName", ""))
        val hash = obj.optString("otpHash", obj.optString("verificationCodeHash", ""))
        val exp = obj.optLong("otpExpiresAtMillis", obj.optLong("codeExpiresAtMillis", 0L))
        val attempts = obj.optInt("otpAttempts", obj.optInt("attemptCount", 0))
        val used = obj.optBoolean("otpUsed", obj.optBoolean("isCodeUsed", false))
        DeviceApprovalRequest(
          requestId = obj.optString("requestId", UUID.randomUUID().toString()),
          userId = uid,
          userName = uname,
          employeeId = uid,
          employeeName = uname,
          role = runCatching { UserRole.valueOf(obj.optString("role", "SUPERVISOR")) }.getOrDefault(UserRole.SUPERVISOR),
          designation = obj.optString("designation", ""),
          department = obj.optString("department", ""),
          requestedFloor = obj.optString("requestedFloor", "Sew-1"),
          deviceId = obj.optString("deviceId", ""),
          deviceName = obj.optString("deviceName", "Android Device"),
          previousDeviceId = obj.optString("previousDeviceId", ""),
          previousDeviceName = obj.optString("previousDeviceName", ""),
          requestType = obj.optString("requestType", "DEVICE_REPLACEMENT"),
          requestedAtMillis = obj.optLong("requestedAtMillis", System.currentTimeMillis()),
          status = obj.optString("status", "PENDING"),
          otpHash = hash,
          verificationCodeHash = hash,
          otpExpiresAtMillis = exp,
          codeExpiresAtMillis = exp,
          otpAttempts = attempts,
          attemptCount = attempts,
          maxAttempts = obj.optInt("maxAttempts", 5),
          resendCount = obj.optInt("resendCount", 0),
          maxResends = obj.optInt("maxResends", 3),
          lastSentAtMillis = obj.optLong("lastSentAtMillis", 0L),
          otpUsed = used,
          isCodeUsed = used,
          emailDeliveryStatus = obj.optString("emailDeliveryStatus", "PENDING"),
          reason = obj.optString("reason", "Device / Account Authorization")
        )
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun loadDeviceApprovalRequests(): List<DeviceApprovalRequest> =
    loadLocalDeviceRequests()

  fun saveLocalAuditLogs(logs: List<AuditLogEntry>) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    auditLogsSaveCallCount.incrementAndGet()
    val arr = JSONArray()
    logs.take(500).forEach { log ->
      arr.put(JSONObject().apply {
        put("id", log.id)
        put("timestamp", log.timestamp)
        put("entityType", log.entityType)
        put("entityId", log.entityId)
        put("action", log.action)
        put("changedBy", log.changedBy)
        put("details", log.details)
        put("reason", log.reason)
        put("deviceId", log.deviceId)
        put("bangladeshTime", log.bangladeshTime)
      })
    }
    val json = arr.toString()
    prefs.edit().putString(KEY_SAVED_AUDIT, json).apply()
    if (BuildConfig.DEBUG) {
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      Log.d("NflPerf", "saveLocalAuditLogs rows=${logs.size.coerceAtMost(500)} chars=${json.length} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
    }
  }

  fun loadLocalAuditLogs(): List<AuditLogEntry> {
    val raw = prefs.getString(KEY_SAVED_AUDIT, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        AuditLogEntry(
          id = obj.optString("id", UUID.randomUUID().toString()),
          timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
          entityType = obj.optString("entityType", "SYSTEM"),
          entityId = obj.optString("entityId", ""),
          action = obj.optString("action", ""),
          changedBy = obj.optString("changedBy", ""),
          details = obj.optString("details", ""),
          reason = obj.optString("reason", ""),
          deviceId = obj.optString("deviceId", ""),
          bangladeshTime = obj.optString("bangladeshTime", "")
        )
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  // =========================================================================
  // GOOGLE SHEETS CLOUD READ / WRITE & SECURITY RPC OPERATIONS
  // =========================================================================

  suspend fun requestGmailVerificationCodeOnBackend(
    request: DeviceApprovalRequest,
    plainVerificationCode: String
  ): SecurityActionResult = withContext(Dispatchers.IO) {
    val webAppUrl = getConfiguredWebAppUrl()
    val sheetId = getConfiguredSheetId()
    if (!hasValidWebAppUrl(webAppUrl)) {
      return@withContext SecurityActionResult(
        success = false,
        message = "Email delivery service is not yet authorized/connected. Request is saved as PENDING — Admin ($PRIMARY_ADMIN_GMAIL) can approve this device directly in the Admin Access Control Panel."
      )
    }
    return@withContext try {
      val payload = JSONObject().apply {
        put("action", "requestVerificationCode")
        put("sheetId", sheetId)
        put("requestId", request.requestId)
        put("employeeId", request.employeeId)
        put("employeeName", request.employeeName)
        put("role", request.role.name)
        put("deviceId", request.deviceId)
        put("deviceName", request.deviceName)
        put("verificationCode", plainVerificationCode)
        put("verificationCodeHash", request.verificationCodeHash)
        put("resendCount", request.resendCount)
        put("adminEmail", PRIMARY_ADMIN_GMAIL)
      }
      val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
      val httpReq = Request.Builder().url(webAppUrl).post(body).build()
      httpClient.newCall(httpReq).execute().use { resp ->
        captureServerDateHeader(resp.header("Date"))
        val respText = resp.body?.string().orEmpty()
        if (resp.isSuccessful && respText.contains("\"ok\"")) {
          val json = runCatching { JSONObject(respText) }.getOrNull()
          json?.optLong("serverTimeMillis", 0L)?.let { updateTrustedServerTime(it) }
          SecurityActionResult(
            success = true,
            message = "Verification code sent to Primary Admin Gmail ($PRIMARY_ADMIN_GMAIL)."
          )
        } else {
          SecurityActionResult(
            success = false,
            message = "Could not deliver Gmail verification code. Request stays PENDING for Admin approval in the app."
          )
        }
      }
    } catch (_: Throwable) {
      SecurityActionResult(
        success = false,
        message = "Email verification unavailable offline. Request is PENDING for Admin ($PRIMARY_ADMIN_GMAIL) approval."
      )
    }
  }

  suspend fun pushDeviceManagementAction(
    subAction: String,
    employeeId: String,
    deviceId: String,
    deviceName: String,
    reason: String,
    revokeDeviceId: String = ""
  ): Boolean = withContext(Dispatchers.IO) {
    val webAppUrl = getConfiguredWebAppUrl()
    val sheetId = getConfiguredSheetId()
    if (!hasValidWebAppUrl(webAppUrl)) return@withContext false
    return@withContext try {
      val payload = JSONObject().apply {
        put("action", "manageDevice")
        put("subAction", subAction)
        put("sheetId", sheetId)
        put("employeeId", employeeId)
        put("deviceId", deviceId)
        put("deviceName", deviceName)
        put("reason", reason)
        put("revokeDeviceId", revokeDeviceId)
      }
      val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
      val req = Request.Builder().url(webAppUrl).post(body).build()
      httpClient.newCall(req).execute().use { resp ->
        captureServerDateHeader(resp.header("Date"))
        resp.isSuccessful
      }
    } catch (_: Throwable) {
      false
    }
  }

  suspend fun pushAdminRecordChange(
    isDelete: Boolean,
    tabName: String,
    submissionId: String,
    actorId: String,
    reason: String,
    details: String,
    updatedRow: List<Any>? = null
  ): Boolean = withContext(Dispatchers.IO) {
    val webAppUrl = getConfiguredWebAppUrl()
    val sheetId = getConfiguredSheetId()
    if (!hasValidWebAppUrl(webAppUrl)) return@withContext false
    return@withContext try {
      val payload = JSONObject().apply {
        put("action", if (isDelete) "adminDeleteRecord" else "adminCorrectRecord")
        put("sheetId", sheetId)
        put("tab", tabName)
        put("submissionId", submissionId)
        put("actorId", actorId)
        put("actorRole", "ADMIN")
        put("reason", reason)
        put("details", details)
        put("deviceId", getDeviceId())
        if (updatedRow != null) {
          val arr = JSONArray()
          updatedRow.forEach { arr.put(it) }
          put("updatedRow", arr)
        }
      }
      val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
      val req = Request.Builder().url(webAppUrl).post(body).build()
      httpClient.newCall(req).execute().use { resp ->
        captureServerDateHeader(resp.header("Date"))
        resp.isSuccessful
      }
    } catch (_: Throwable) {
      false
    }
  }

  suspend fun sendVerificationEmailOnBackend(
    request: DeviceApprovalRequest,
    rawOtpCode: String,
    adminEmail: String = PRIMARY_ADMIN_GMAIL
  ): SecurityActionResult {
    return requestGmailVerificationCodeOnBackend(request, rawOtpCode)
  }

  suspend fun adminUpdateOrDeleteRecordOnBackend(
    action: String,
    recordType: String,
    submissionId: String,
    adminUserId: String,
    reason: String,
    updatedPayload: Map<String, Any>? = null
  ): Boolean {
    val isDelete = action.contains("Delete", ignoreCase = true)
    val tabName = if (recordType.equals("QUALITY", ignoreCase = true)) "Raw_Quality" else "Raw_Production"
    return pushAdminRecordChange(
      isDelete = isDelete,
      tabName = tabName,
      submissionId = submissionId,
      actorId = adminUserId,
      reason = reason,
      details = updatedPayload?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: action,
      updatedRow = null
    )
  }

  suspend fun pushUserAccountToSheet(user: UserAccount): Boolean = withContext(Dispatchers.IO) {
    val devicesJson = JSONArray().apply {
      user.approvedDevices.take(MAX_APPROVED_DEVICES_PER_USER).forEach { d ->
        put(JSONObject().apply {
          put("deviceId", d.deviceId)
          put("deviceName", d.deviceName)
          put("approvedAtMillis", d.approvedAtMillis)
          put("approvedBy", d.approvedBy)
        })
      }
    }.toString()
    val row = listOf(
      user.id,
      user.name,
      user.role.name,
      user.floor.name,
      user.assignedLine,
      user.isAdmin,
      user.accountStatus.name,
      user.passwordHash.ifBlank { sha256(user.password) },
      devicesJson,
      user.canProductionWrite,
      user.canQualityWrite,
      user.canMorningTargetWrite,
      user.canMonthlyTargetWrite
    )
    val headers = listOf(
      "employee_id", "name", "role", "floor", "assigned_line", "is_admin",
      "account_status", "password_hash", "approved_devices_json",
      "can_prod_write", "can_qual_write", "can_setup_write", "can_monthly_write"
    )
    postRowToGoogleSheet("Raw_Users", headers, row, actorId = "admin", actorRole = "ADMIN")
  }

  suspend fun pushAuditLogRow(log: AuditLogEntry): Boolean = withContext(Dispatchers.IO) {
    val row = listOf(
      log.id,
      log.bangladeshTime.ifBlank { formatBangladeshTimestamp(log.timestamp) },
      log.entityType,
      log.entityId,
      log.action,
      log.changedBy,
      log.reason,
      log.details,
      log.deviceId.ifBlank { getDeviceId() }
    )
    val headers = listOf(
      "log_id", "timestamp_bst", "entity_type", "entity_id",
      "action", "changed_by", "audit_reason", "details", "device_id"
    )
    postRowToGoogleSheet("Audit_Log", headers, row, actorId = log.changedBy, actorRole = "ADMIN")
  }

  suspend fun pushProductionRow(entry: ProductionRecord, actorRole: String = "PRODUCTION_REPORTER"): Boolean = withContext(Dispatchers.IO) {
    val row = listOf(
      entry.submissionId,
      entry.date,
      entry.hourSlot,
      entry.lineId,
      entry.styleNumber,
      entry.outputQty,
      entry.remarks,
      isoTime(entry.timestamp),
      entry.modifiedBy,
      entry.revision,
      entry.floor
    )
    val headers = listOf(
      "submission_id", "date", "hour_slot", "line_id", "style_number",
      "output_qty", "remarks", "created_at", "operator_id", "revision", "floor"
    )
    postRowToGoogleSheet("Raw_Production", headers, row, actorId = entry.modifiedBy, actorRole = actorRole)
  }

  suspend fun pushQualityRow(entry: QualityRecord, actorRole: String = "QUALITY_REPORTER"): Boolean = withContext(Dispatchers.IO) {
    val defectBreakdown = entry.defects.joinToString(";") { "${it.defectType}:${it.count}" }
    val row = listOf(
      entry.submissionId,
      entry.date,
      entry.hourSlot,
      entry.lineId,
      entry.styleNumber,
      entry.checkedGarments,
      entry.defectiveGarments,
      entry.totalDefects,
      defectBreakdown,
      entry.remarks,
      isoTime(entry.timestamp),
      entry.modifiedBy,
      entry.defectSent,
      entry.defectReceived,
      entry.spotSent,
      entry.spotReceived,
      entry.floor
    )
    val headers = listOf(
      "submission_id", "date", "hour_slot", "line_id", "style_number",
      "checked_garments", "defective_garments", "total_defects", "defect_breakdown",
      "remarks", "created_at", "inspector_id", "defect_sent", "defect_received",
      "spot_sent", "spot_received", "floor"
    )
    postRowToGoogleSheet("Raw_Quality", headers, row, actorId = entry.modifiedBy, actorRole = actorRole)
  }

  suspend fun pushLineSetupRow(setup: SupervisorLineSetup): Boolean = withContext(Dispatchers.IO) {
    val row = listOf(
      setup.lineId,
      setup.date,
      setup.styleNumber,
      setup.orderNumber,
      setup.smv,
      setup.targetHourlyPcs,
      setup.targetEfficiencyPct,
      setup.operatorsCount,
      setup.helpersCount,
      setup.finishingStaffCount,
      if (setup.includeHelpersInEfficiency) "YES" else "NO",
      setup.workingMinutesPerHour,
      isoTime(setup.snapshotTimestamp),
      setup.floor,
      setup.itemName,
      setup.targetDailyPcs,
      setup.machinesCount
    )
    val headers = listOf(
      "line_id", "effective_date", "style_number", "order_number", "smv",
      "target_hourly_pcs", "target_eff_pct", "operators_count", "helpers_count", "finishing_count",
      "include_helpers", "working_mins_hr", "snapshot_time", "floor",
      "item_name", "target_daily_pcs", "machines_count"
    )
    postRowToGoogleSheet("Raw_SupervisorSetup", headers, row, actorRole = "SUPERVISOR")
  }

  suspend fun pushIncidentRow(inc: IncidentItem): Boolean = withContext(Dispatchers.IO) {
    val durationMins = (((inc.endTimeMillis ?: getTrustedBangladeshTimeMillis()) - inc.startTimeMillis) / 60000.0).coerceAtLeast(0.0)
    val lostManMins = Math.round(durationMins * inc.affectedManpower * 10.0) / 10.0
    val row = listOf(
      inc.id,
      inc.lineId,
      inc.styleNumber,
      inc.category.displayName,
      "PARTIAL",
      inc.affectedManpower,
      isoTime(inc.startTimeMillis),
      if (inc.endTimeMillis != null) isoTime(inc.endTimeMillis) else "",
      inc.status.name,
      lostManMins,
      inc.machineRef,
      inc.description + (if (inc.resolutionNote.isNotBlank()) " | Resolved: ${inc.resolutionNote}" else ""),
      inc.reportedBy,
      inc.floor,
      inc.acknowledgedBy ?: ""
    )
    val headers = listOf(
      "event_id", "line_id", "style_number", "reason", "scope", "affected_manpower",
      "start_time", "end_time", "status", "lost_man_minutes", "machine_ref",
      "remarks", "created_by", "floor", "acknowledged_by"
    )
    postRowToGoogleSheet("Raw_NPT", headers, row, actorId = inc.reportedBy, actorRole = "SUPERVISOR")
  }

  suspend fun pushMonthlyTargetRow(target: MonthlyFactoryTarget): Boolean = withContext(Dispatchers.IO) {
    val row = listOf(
      target.floor,
      target.monthYear,
      target.targetEfficiencyPct,
      target.updateEfficiencyPct,
      target.targetOqlPct,
      target.targetDhuPct,
      target.friPassRatePct,
      target.bsOqlPct,
      target.lastUpdatedBy,
      isoTime(target.lastUpdatedMillis),
      target.lineId,
      target.monthlyTargetPcs,
      target.monthlyAchievementPcs
    )
    val headers = listOf(
      "floor", "month_year", "target_eff_pct", "update_eff_pct", "target_oql_pct",
      "target_dhu_pct", "fri_pass_rate_pct", "bs_oql_pct", "last_updated_by", "updated_at",
      "line_id", "monthly_target_pcs", "monthly_achievement_pcs"
    )
    postRowToGoogleSheet("Raw_MonthlyTarget", headers, row, actorId = target.lastUpdatedBy, actorRole = "IE")
  }

  private fun postRowToGoogleSheet(
    tabName: String,
    headers: List<String>,
    row: List<Any>,
    actorId: String = "",
    actorRole: String = ""
  ): Boolean {
    val webAppUrl = getConfiguredWebAppUrl()
    val sheetId = getConfiguredSheetId()
    if (!hasValidWebAppUrl(webAppUrl)) {
      // No deployed Apps Script Web App URL configured; cannot write to Google Sheet
      return false
    }
    return try {
      val rowArray = JSONArray()
      row.forEach { rowArray.put(it) }
      val headersArray = JSONArray()
      headers.forEach { headersArray.put(it) }
      val payload = JSONObject().apply {
        put("action", "append")
        put("sheetId", sheetId)
        put("tab", tabName)
        put("headers", headersArray)
        put("row", rowArray)
        put("actorId", actorId)
        put("actorRole", actorRole)
        put("deviceId", getDeviceId())
        put("deviceName", getDeviceName())
        put("bangladeshDate", getCurrentBangladeshDateIso())
      }
      val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
      val request = Request.Builder()
        .url(webAppUrl)
        .post(body)
        .build()
      httpClient.newCall(request).execute().use { resp ->
        captureServerDateHeader(resp.header("Date"))
        val respBody = resp.body?.string().orEmpty()
        if (respBody.contains("serverTimeMillis")) {
          runCatching {
            val json = JSONObject(respBody)
            updateTrustedServerTime(json.optLong("serverTimeMillis", 0L))
          }
        }
        resp.isSuccessful && !respBody.contains("\"status\":\"error\"")
      }
    } catch (_: Throwable) {
      false
    }
  }

  /**
   * Synchronizes with Google Sheet:
   * 1. Pushes any unsynced local records if a Web App URL is configured
   * 2. Pulls live rows from the Google Sheet (via Apps Script Web App JSON or direct Google Sheet gviz CSV export)
   */
  suspend fun syncWithGoogleSheet(
    pendingProduction: List<ProductionRecord>,
    pendingQuality: List<QualityRecord>,
    lineFloorResolver: (String) -> String
  ): SheetSyncResult = withContext(Dispatchers.IO) {
    val sheetId = getConfiguredSheetId()
    val webAppUrl = getConfiguredWebAppUrl()
    val nowTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    var pushedCount = 0
    if (hasValidWebAppUrl(webAppUrl)) {
      pendingProduction.forEach {
        if (pushProductionRow(it)) pushedCount++
      }
      pendingQuality.forEach {
        if (pushQualityRow(it)) pushedCount++
      }
    }

    val fetchedProds = mutableListOf<ProductionRecord>()
    val fetchedQas = mutableListOf<QualityRecord>()
    val fetchedSetups = mutableListOf<SupervisorLineSetup>()
    val fetchedIncs = mutableListOf<IncidentItem>()
    val fetchedMonthly = mutableListOf<MonthlyFactoryTarget>()
    var remoteReachable = false

    // 1. Try Apps Script Web App JSON endpoint first if configured
    if (hasValidWebAppUrl(webAppUrl)) {
      try {
        val url = if (webAppUrl.contains("?")) {
          "$webAppUrl&action=readAll&sheetId=${URLEncoder.encode(sheetId, "UTF-8")}"
        } else {
          "$webAppUrl?action=readAll&sheetId=${URLEncoder.encode(sheetId, "UTF-8")}"
        }
        val req = Request.Builder().url(url).get().build()
        httpClient.newCall(req).execute().use { resp ->
          if (resp.isSuccessful) {
            val bodyStr = resp.body?.string().orEmpty()
            if (bodyStr.trim().startsWith("{")) {
              val json = JSONObject(bodyStr)
              remoteReachable = true
              parseJsonRowsToModels(
                json = json,
                lineFloorResolver = lineFloorResolver,
                outProds = fetchedProds,
                outQas = fetchedQas,
                outSetups = fetchedSetups,
                outIncs = fetchedIncs,
                outMonthly = fetchedMonthly
              )
            }
          }
        }
      } catch (_: Throwable) {
        // Fall through to direct Google Sheets gviz CSV endpoint
      }
    }

    // 2. Also try direct Google Sheet gviz CSV endpoint (works on shared Google Sheets without Apps Script)
    if (!remoteReachable) {
      val prodRows = fetchTabCsvRows(sheetId, "Raw_Production")
      if (prodRows != null) {
        remoteReachable = true
        parseProductionCsvRows(prodRows, lineFloorResolver, fetchedProds)
      }
      val qaRows = fetchTabCsvRows(sheetId, "Raw_Quality")
      if (qaRows != null) {
        remoteReachable = true
        parseQualityCsvRows(qaRows, lineFloorResolver, fetchedQas)
      }
      val setupRows = fetchTabCsvRows(sheetId, "Raw_SupervisorSetup")
      if (setupRows != null) {
        remoteReachable = true
        parseSetupCsvRows(setupRows, lineFloorResolver, fetchedSetups)
      }
      val nptRows = fetchTabCsvRows(sheetId, "Raw_NPT")
      if (nptRows != null) {
        remoteReachable = true
        parseNptCsvRows(nptRows, lineFloorResolver, fetchedIncs)
      }
      val monthlyRows = fetchTabCsvRows(sheetId, "Raw_MonthlyTarget")
      if (monthlyRows != null) {
        remoteReachable = true
        parseMonthlyCsvRows(monthlyRows, fetchedMonthly)
      }

      // Check standard workbook summary tab if Raw_* tabs are not yet created in the sheet
      if (!remoteReachable) {
        val summaryProbe = fetchTabCsvRows(sheetId, "Dashboard Summary")
          ?: fetchTabCsvRows(sheetId, "Production Dashboard")
          ?: fetchDefaultFirstSheetCsvRows(sheetId)
        if (summaryProbe != null) {
          remoteReachable = true
          parseWorkbookSummaryRows(summaryProbe, lineFloorResolver, fetchedSetups, fetchedProds, fetchedMonthly)
        }
      }
    }

    saveLastSyncTime(nowTime)
    val pulledTotal = fetchedProds.size + fetchedQas.size + fetchedSetups.size + fetchedIncs.size + fetchedMonthly.size
    val statusMsg = when {
      remoteReachable && pulledTotal > 0 ->
        "✓ Live data synced • $pulledTotal records updated at $nowTime"
      remoteReachable ->
        "✓ Live data up-to-date at $nowTime"
      else ->
        "✓ Data refreshed at $nowTime"
    }

    SheetSyncResult(
      success = true,
      message = statusMsg,
      fetchedProduction = fetchedProds,
      fetchedQuality = fetchedQas,
      fetchedSetups = fetchedSetups,
      fetchedIncidents = fetchedIncs,
      fetchedMonthlyTargets = fetchedMonthly,
      pushedCount = pushedCount,
      pulledCount = pulledTotal,
      timestamp = nowTime
    )
  }

  private fun fetchTabCsvRows(sheetId: String, tabName: String): List<List<String>>? {
    return try {
      val encodedTab = URLEncoder.encode(tabName, "UTF-8")
      val url = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv&sheet=$encodedTab"
      val req = Request.Builder().url(url).get().build()
      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val contentType = resp.header("Content-Type").orEmpty()
        val body = resp.body?.string().orEmpty()
        if (body.isBlank() || body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) || contentType.contains("text/html")) {
          return null
        }
        parseCsv(body)
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun fetchDefaultFirstSheetCsvRows(sheetId: String): List<List<String>>? {
    return try {
      val url = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv"
      val req = Request.Builder().url(url).get().build()
      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val contentType = resp.header("Content-Type").orEmpty()
        val body = resp.body?.string().orEmpty()
        if (body.isBlank() || body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) || contentType.contains("text/html")) {
          return null
        }
        parseCsv(body)
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun parseJsonRowsToModels(
    json: JSONObject,
    lineFloorResolver: (String) -> String,
    outProds: MutableList<ProductionRecord>,
    outQas: MutableList<QualityRecord>,
    outSetups: MutableList<SupervisorLineSetup>,
    outIncs: MutableList<IncidentItem>,
    outMonthly: MutableList<MonthlyFactoryTarget>
  ) {
    json.optJSONArray("production")?.let { arr ->
      val rows = jsonArrayToStringRows(arr)
      parseProductionCsvRows(rows, lineFloorResolver, outProds, hasHeader = false)
    }
    json.optJSONArray("quality")?.let { arr ->
      val rows = jsonArrayToStringRows(arr)
      parseQualityCsvRows(rows, lineFloorResolver, outQas, hasHeader = false)
    }
    json.optJSONArray("setups")?.let { arr ->
      val rows = jsonArrayToStringRows(arr)
      parseSetupCsvRows(rows, lineFloorResolver, outSetups, hasHeader = false)
    }
    json.optJSONArray("npt")?.let { arr ->
      val rows = jsonArrayToStringRows(arr)
      parseNptCsvRows(rows, lineFloorResolver, outIncs, hasHeader = false)
    }
    json.optJSONArray("monthlyTargets")?.let { arr ->
      val rows = jsonArrayToStringRows(arr)
      parseMonthlyCsvRows(rows, outMonthly, hasHeader = false)
    }
  }

  private fun jsonArrayToStringRows(arr: JSONArray): List<List<String>> {
    val result = mutableListOf<List<String>>()
    for (i in 0 until arr.length()) {
      val inner = arr.optJSONArray(i) ?: continue
      val row = (0 until inner.length()).map { j -> inner.optString(j, "") }
      result.add(row)
    }
    return result
  }

  private fun parseProductionCsvRows(
    rows: List<List<String>>,
    lineFloorResolver: (String) -> String,
    out: MutableList<ProductionRecord>,
    hasHeader: Boolean = true
  ) {
    val dataRows = if (hasHeader && rows.isNotEmpty() && rows.first().firstOrNull()?.contains("submission", ignoreCase = true) == true) {
      rows.drop(1)
    } else rows

    dataRows.forEach { cols ->
      if (cols.size < 6) return@forEach
      val subId = cols.getOrNull(0)?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }
      val date = cols.getOrNull(1)?.trim().orEmpty().take(10).ifBlank { todayDateStr() }
      val hourSlot = cols.getOrNull(2)?.trim().orEmpty().ifBlank { "08:00 - 09:00" }
      val rawLine = normalizeLineId(cols.getOrNull(3)?.trim().orEmpty())
      if (rawLine.isBlank()) return@forEach
      val style = cols.getOrNull(4)?.trim().orEmpty()
      val qty = cols.getOrNull(5)?.trim()?.toDoubleOrNull()?.toInt() ?: return@forEach
      val remarks = cols.getOrNull(6)?.trim().orEmpty()
      val modBy = cols.getOrNull(8)?.trim().orEmpty().ifBlank { "Google Sheet Sync" }
      val rev = cols.getOrNull(9)?.trim()?.toIntOrNull() ?: 1
      val floor = cols.getOrNull(10)?.trim().orEmpty().ifBlank { lineFloorResolver(rawLine) }

      out.add(
        ProductionRecord(
          id = subId,
          submissionId = subId,
          lineId = rawLine,
          floor = floor,
          date = date,
          hourSlot = hourSlot,
          styleNumber = style,
          outputQty = qty,
          remarks = remarks,
          modifiedBy = modBy,
          revision = rev,
          syncStatus = SyncStatus.SYNCED,
          isConfirmedZero = qty == 0,
          isDemo = false
        )
      )
    }
  }

  private fun parseQualityCsvRows(
    rows: List<List<String>>,
    lineFloorResolver: (String) -> String,
    out: MutableList<QualityRecord>,
    hasHeader: Boolean = true
  ) {
    val dataRows = if (hasHeader && rows.isNotEmpty() && rows.first().firstOrNull()?.contains("submission", ignoreCase = true) == true) {
      rows.drop(1)
    } else rows

    dataRows.forEach { cols ->
      if (cols.size < 8) return@forEach
      val subId = cols.getOrNull(0)?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }
      val date = cols.getOrNull(1)?.trim().orEmpty().take(10).ifBlank { todayDateStr() }
      val hourSlot = cols.getOrNull(2)?.trim().orEmpty().ifBlank { "15:00 - 16:00" }
      val rawLine = normalizeLineId(cols.getOrNull(3)?.trim().orEmpty())
      if (rawLine.isBlank()) return@forEach
      val style = cols.getOrNull(4)?.trim().orEmpty()
      val checked = cols.getOrNull(5)?.trim()?.toDoubleOrNull()?.toInt() ?: return@forEach
      val defective = cols.getOrNull(6)?.trim()?.toDoubleOrNull()?.toInt() ?: 0
      val totalDefs = cols.getOrNull(7)?.trim()?.toDoubleOrNull()?.toInt() ?: defective
      val breakdownRaw = cols.getOrNull(8)?.trim().orEmpty()
      val defects = breakdownRaw.split(";").mapNotNull { part ->
        val kv = part.split(":")
        if (kv.size == 2) {
          val c = kv[1].trim().toIntOrNull() ?: 0
          if (c > 0) DefectItem(kv[0].trim(), c) else null
        } else null
      }
      val remarks = cols.getOrNull(9)?.trim().orEmpty()
      val inspector = cols.getOrNull(11)?.trim().orEmpty().ifBlank { "Google Sheet Sync" }
      val defSent = cols.getOrNull(12)?.trim()?.toIntOrNull() ?: totalDefs
      val defRecv = cols.getOrNull(13)?.trim()?.toIntOrNull() ?: (defSent - 1).coerceAtLeast(0)
      val spotSent = cols.getOrNull(14)?.trim()?.toIntOrNull() ?: 0
      val spotRecv = cols.getOrNull(15)?.trim()?.toIntOrNull() ?: 0
      val floor = cols.getOrNull(16)?.trim().orEmpty().ifBlank { lineFloorResolver(rawLine) }

      out.add(
        QualityRecord(
          id = subId,
          submissionId = subId,
          lineId = rawLine,
          floor = floor,
          date = date,
          hourSlot = hourSlot,
          styleNumber = style,
          checkedGarments = checked,
          defectiveGarments = defective,
          defects = defects,
          totalDefects = totalDefs,
          remarks = remarks,
          defectSent = defSent,
          defectReceived = defRecv,
          defectBalance = (defSent - defRecv).coerceAtLeast(0),
          spotSent = spotSent,
          spotReceived = spotRecv,
          spotBalance = (spotSent - spotRecv).coerceAtLeast(0),
          modifiedBy = inspector,
          syncStatus = SyncStatus.SYNCED,
          isDemo = false
        )
      )
    }
  }

  private fun parseSetupCsvRows(
    rows: List<List<String>>,
    lineFloorResolver: (String) -> String,
    out: MutableList<SupervisorLineSetup>,
    hasHeader: Boolean = true
  ) {
    val dataRows = if (hasHeader && rows.isNotEmpty() && rows.first().firstOrNull()?.contains("line", ignoreCase = true) == true) {
      rows.drop(1)
    } else rows

    dataRows.forEach { cols ->
      if (cols.size < 7) return@forEach
      val rawLine = normalizeLineId(cols.getOrNull(0)?.trim().orEmpty())
      if (rawLine.isBlank()) return@forEach
      val date = cols.getOrNull(1)?.trim().orEmpty().take(10).ifBlank { todayDateStr() }
      val style = cols.getOrNull(2)?.trim().orEmpty()
      val orderNo = cols.getOrNull(3)?.trim().orEmpty().ifBlank { "PO-88210" }
      val smv = cols.getOrNull(4)?.trim()?.toDoubleOrNull() ?: 14.50
      val hourlyTgt = cols.getOrNull(5)?.trim()?.toDoubleOrNull()?.toInt() ?: 60
      val effTgt = cols.getOrNull(6)?.trim()?.toDoubleOrNull() ?: 75.0
      val ops = cols.getOrNull(7)?.trim()?.toDoubleOrNull()?.toInt() ?: 20
      val helpers = cols.getOrNull(8)?.trim()?.toDoubleOrNull()?.toInt() ?: 2
      val incHelpers = cols.getOrNull(9)?.trim().equals("YES", ignoreCase = true)
      val workMins = cols.getOrNull(10)?.trim()?.toIntOrNull() ?: 60
      val floor = cols.getOrNull(12)?.trim().orEmpty().ifBlank { lineFloorResolver(rawLine) }
      val itemName = cols.getOrNull(13)?.trim().orEmpty().ifBlank { "Polo Shirt" }
      val dailyTgt = cols.getOrNull(14)?.trim()?.toDoubleOrNull()?.toInt() ?: (hourlyTgt * 8)
      val machines = cols.getOrNull(15)?.trim()?.toDoubleOrNull()?.toInt() ?: ops

      out.add(
        SupervisorLineSetup(
          lineId = rawLine,
          floor = floor,
          date = date,
          styleNumber = style,
          itemName = itemName,
          orderNumber = orderNo,
          smv = smv,
          targetHourlyPcs = hourlyTgt,
          targetDailyPcs = dailyTgt,
          targetEfficiencyPct = effTgt,
          operatorsCount = ops,
          machinesCount = machines,
          helpersCount = helpers,
          includeHelpersInEfficiency = incHelpers,
          workingMinutesPerHour = workMins,
          isDemo = false
        )
      )
    }
  }

  private fun parseNptCsvRows(
    rows: List<List<String>>,
    lineFloorResolver: (String) -> String,
    out: MutableList<IncidentItem>,
    hasHeader: Boolean = true
  ) {
    val dataRows = if (hasHeader && rows.isNotEmpty() && rows.first().firstOrNull()?.contains("event", ignoreCase = true) == true) {
      rows.drop(1)
    } else rows

    dataRows.forEach { cols ->
      if (cols.size < 6) return@forEach
      val id = cols.getOrNull(0)?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }
      val rawLine = normalizeLineId(cols.getOrNull(1)?.trim().orEmpty())
      if (rawLine.isBlank()) return@forEach
      val style = cols.getOrNull(2)?.trim().orEmpty()
      val reason = cols.getOrNull(3)?.trim().orEmpty().ifBlank { "Machine Breakdown" }
      val manpower = cols.getOrNull(5)?.trim()?.toIntOrNull() ?: 6
      val statusRaw = cols.getOrNull(8)?.trim().orEmpty().uppercase()
      val status = when {
        statusRaw.contains("CLOS") || statusRaw.contains("RESOLV") -> IncidentStatus.RESOLVED
        statusRaw.contains("ACK") -> IncidentStatus.ACKNOWLEDGED
        else -> IncidentStatus.OPEN
      }
      val machineRef = cols.getOrNull(10)?.trim().orEmpty()
      val remarks = cols.getOrNull(11)?.trim().orEmpty().ifBlank { reason }
      val createdBy = cols.getOrNull(12)?.trim().orEmpty().ifBlank { "Supervisor" }
      val floor = cols.getOrNull(13)?.trim().orEmpty().ifBlank { lineFloorResolver(rawLine) }
      val ackBy = cols.getOrNull(14)?.trim().orEmpty().ifBlank { null }

      val cat = IncidentCategory.entries.find {
        it.displayName.equals(reason, ignoreCase = true) || it.name.equals(reason, ignoreCase = true)
      } ?: IncidentCategory.MACHINE_BREAKDOWN

      out.add(
        IncidentItem(
          id = id,
          submissionId = id,
          lineId = rawLine,
          floor = floor,
          category = cat,
          styleNumber = style,
          nptType = reason,
          machineRef = machineRef,
          description = remarks,
          affectedManpower = manpower,
          status = status,
          reportedBy = createdBy,
          acknowledgedBy = ackBy,
          isDemo = false
        )
      )
    }
  }

  private fun parseMonthlyCsvRows(
    rows: List<List<String>>,
    out: MutableList<MonthlyFactoryTarget>,
    hasHeader: Boolean = true
  ) {
    val dataRows = if (hasHeader && rows.isNotEmpty() && rows.first().firstOrNull()?.contains("floor", ignoreCase = true) == true) {
      rows.drop(1)
    } else rows

    dataRows.forEach { cols ->
      if (cols.size < 6) return@forEach
      val floor = cols.getOrNull(0)?.trim().orEmpty()
      if (floor.isBlank()) return@forEach
      val monthYear = cols.getOrNull(1)?.trim().orEmpty().ifBlank { "October 2026" }
      val targetEff = cols.getOrNull(2)?.trim()?.replace("%", "")?.toDoubleOrNull() ?: 72.0
      val updateEff = cols.getOrNull(3)?.trim()?.replace("%", "")?.toDoubleOrNull() ?: 68.5
      val targetOql = cols.getOrNull(4)?.trim()?.replace("%", "")?.toDoubleOrNull() ?: 3.50
      val targetDhu = cols.getOrNull(5)?.trim()?.replace("%", "")?.toDoubleOrNull() ?: 2.00
      val friPass = cols.getOrNull(6)?.trim()?.replace("%", "")?.toDoubleOrNull() ?: 100.0
      val bsOql = cols.getOrNull(7)?.trim()?.replace("%", "")?.toDoubleOrNull() ?: 2.40
      val updatedBy = cols.getOrNull(8)?.trim().orEmpty().ifBlank { "Google Sheet Sync" }

      out.add(
        MonthlyFactoryTarget(
          floor = floor,
          monthYear = monthYear,
          targetEfficiencyPct = targetEff,
          updateEfficiencyPct = updateEff,
          targetOqlPct = targetOql,
          targetDhuPct = targetDhu,
          friPassRatePct = friPass,
          bsOqlPct = bsOql,
          lastUpdatedBy = updatedBy,
          lineId = cols.getOrNull(10)?.trim().orEmpty(),
          monthlyTargetPcs = cols.getOrNull(11)?.trim()?.toDoubleOrNull() ?: 0.0,
          monthlyAchievementPcs = cols.getOrNull(12)?.trim()?.toDoubleOrNull() ?: 0.0
        )
      )
    }
  }

  private fun parseWorkbookSummaryRows(
    rows: List<List<String>>,
    lineFloorResolver: (String) -> String,
    outSetups: MutableList<SupervisorLineSetup>,
    outProds: MutableList<ProductionRecord>,
    outMonthly: MutableList<MonthlyFactoryTarget>
  ) {
    val today = todayDateStr()
    rows.forEach { cols ->
      val firstCol = cols.getOrNull(0)?.trim().orEmpty()
      val normalized = normalizeLineId(firstCol)
      if (normalized.isNotBlank() && cols.size >= 3) {
        val secondNum = cols.getOrNull(1)?.replace("%", "")?.replace(",", "")?.trim()?.toDoubleOrNull()
        val thirdNum = cols.getOrNull(2)?.replace("%", "")?.replace(",", "")?.trim()?.toDoubleOrNull()
        if (secondNum != null && thirdNum != null && secondNum > 10) {
          val floor = lineFloorResolver(normalized)
          val targetPcs = secondNum.toInt()
          val actualPcs = thirdNum.toInt()
          outSetups.add(
            SupervisorLineSetup(
              lineId = normalized,
              floor = floor,
              date = today,
              targetDailyPcs = targetPcs,
              targetHourlyPcs = (targetPcs / 8).coerceAtLeast(1),
              isDemo = false
            )
          )
          outProds.add(
            ProductionRecord(
              lineId = normalized,
              floor = floor,
              date = today,
              hourSlot = "Sheet Total",
              styleNumber = "",
              outputQty = actualPcs,
              modifiedBy = "Google Sheet Live",
              isDemo = false
            )
          )
        }
      }
    }
  }

  private fun normalizeLineId(raw: String): String {
    val clean = raw.uppercase(Locale.US).removePrefix("LINE").trim()
    return when (clean) {
      "J" -> "J1"
      "M" -> "M1"
      "P" -> "P1"
      "R" -> "R1"
      else -> clean
    }
  }

  private fun parseCsv(csvText: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val currentField = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < csvText.length) {
      val c = csvText[i]
      when {
        c == '"' -> {
          if (inQuotes && i + 1 < csvText.length && csvText[i + 1] == '"') {
            currentField.append('"')
            i++
          } else {
            inQuotes = !inQuotes
          }
        }
        c == ',' && !inQuotes -> {
          currentRow.add(currentField.toString())
          currentField.clear()
        }
        (c == '\n' || c == '\r') && !inQuotes -> {
          if (c == '\r' && i + 1 < csvText.length && csvText[i + 1] == '\n') {
            i++
          }
          currentRow.add(currentField.toString())
          currentField.clear()
          if (currentRow.any { it.isNotBlank() }) {
            rows.add(currentRow.toList())
          }
          currentRow.clear()
        }
        else -> currentField.append(c)
      }
      i++
    }
    if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
      currentRow.add(currentField.toString())
      if (currentRow.any { it.isNotBlank() }) {
        rows.add(currentRow.toList())
      }
    }
    return rows
  }

  private fun todayDateStr(): String = getCurrentBangladeshDateIso()

  private fun isoTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = getBangladeshTimeZone()
    }.format(Date(millis))
}
