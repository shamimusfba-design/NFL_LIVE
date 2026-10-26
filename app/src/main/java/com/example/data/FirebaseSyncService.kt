package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import android.os.SystemClock
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class CollectionReadStats(
  val collection: String,
  val serverChangesReceived: Int = 0,
  val cacheChangesReceived: Int = 0,
  val snapshotEvents: Int = 0,
  val lastIsFromCache: Boolean = false
) {
  val totalChangesReceived: Int
    get() = serverChangesReceived + cacheChangesReceived
}

data class FirestoreFetchResult(
  val success: Boolean,
  val errorMessage: String? = null,
  val production: List<ProductionRecord> = emptyList(),
  val quality: List<QualityRecord> = emptyList(),
  val incidents: List<IncidentItem> = emptyList(),
  val setups: List<SupervisorLineSetup> = emptyList(),
  val monthlyTargets: List<MonthlyFactoryTarget> = emptyList(),
  val users: List<UserAccount> = emptyList()
)

class FirebaseSyncService(private val context: Context) {

  private val firestore: FirebaseFirestore? = try {
    val dbId = try {
      context.applicationContext.getString(com.example.R.string.firestore_database_id)
    } catch (_: Exception) {
      "ai-studio-android-nfllivea-664f462a-fb9e-483d-9567-6ac2c98cf7a4"
    }
    if (dbId.isNotBlank() && dbId != "(default)") {
      FirebaseFirestore.getInstance(dbId)
    } else {
      FirebaseFirestore.getInstance()
    }
  } catch (e: Exception) {
    Log.w("FirebaseSync", "FirebaseFirestore unavailable: ${e.message}")
    null
  }

  private val listeners = mutableListOf<ListenerRegistration>()
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

  @Volatile
  var hasPermissionError: Boolean = false
    private set

  @Volatile
  var lastErrorMessage: String? = null
    private set

  var onRemoteProductionUpdated: ((List<ProductionRecord>) -> Unit)? = null
  var onRemoteProductionDeleted: ((Set<String>) -> Unit)? = null
  var onRemoteQualityUpdated: ((List<QualityRecord>) -> Unit)? = null
  var onRemoteQualityDeleted: ((Set<String>) -> Unit)? = null
  var onRemoteIncidentsUpdated: ((List<IncidentItem>) -> Unit)? = null
  var onRemoteSetupsUpdated: ((List<SupervisorLineSetup>) -> Unit)? = null
  var onRemoteMonthlyTargetsUpdated: ((List<MonthlyFactoryTarget>) -> Unit)? = null
  var onRemoteWeeklyQualityUpdated: ((List<WeeklyQualityEntry>) -> Unit)? = null
  var onRemoteUsersUpdated: ((List<UserAccount>) -> Unit)? = null
  var onRemoteDeviceRequestsUpdated: ((List<DeviceApprovalRequest>) -> Unit)? = null
  var onRemoteAdminNotificationsUpdated: ((List<AdminNotificationPayload>) -> Unit)? = null
  var onSyncStatusChanged: ((String) -> Unit)? = null

  @Volatile
  var activeUserIdForListener: String = ""

  @Volatile
  var isActiveUserAdmin: Boolean = false

  private val knownLegacyProductionDocIds = ConcurrentHashMap.newKeySet<String>()
  private val knownLegacyQualityDocIds = ConcurrentHashMap.newKeySet<String>()
  private val lastPushedProductionSignatures = ConcurrentHashMap<String, String>()
  private val lastPushedQualitySignatures = ConcurrentHashMap<String, String>()

  private val _sessionReadStats = MutableStateFlow<Map<String, CollectionReadStats>>(
    mapOf(
      "production_entries" to CollectionReadStats("production_entries"),
      "quality_entries" to CollectionReadStats("quality_entries"),
      "daily_setups" to CollectionReadStats("daily_setups"),
      "factory_alerts" to CollectionReadStats("factory_alerts"),
      "monthly_targets" to CollectionReadStats("monthly_targets"),
      "users" to CollectionReadStats("users")
    )
  )
  val sessionReadStats: StateFlow<Map<String, CollectionReadStats>> = _sessionReadStats.asStateFlow()

  @Volatile
  var lastListenerWindowStartIso: String = ""
    private set

  fun recordCollectionRead(collection: String, changesCount: Int, isFromCache: Boolean) {
    var updatedStats = CollectionReadStats(collection)
    _sessionReadStats.update { current ->
      val currentMap = current.toMutableMap()
      val prev = currentMap[collection] ?: CollectionReadStats(collection)
      val updated = prev.copy(
        serverChangesReceived = prev.serverChangesReceived + if (isFromCache) 0 else changesCount,
        cacheChangesReceived = prev.cacheChangesReceived + if (isFromCache) changesCount else 0,
        snapshotEvents = prev.snapshotEvents + 1,
        lastIsFromCache = isFromCache
      )
      updatedStats = updated
      currentMap[collection] = updated
      currentMap
    }
    Log.d(
      "FirestoreUsage",
      "collection=$collection documentChanges=$changesCount isFromCache=$isFromCache totalDocs=${updatedStats.totalChangesReceived} (server=${updatedStats.serverChangesReceived}, cache=${updatedStats.cacheChangesReceived})"
    )
  }

  fun getBangladeshDateDaysAgoIso(daysAgo: Int): String {
    val tz = TimeZone.getTimeZone("Asia/Dhaka")
    val cal = Calendar.getInstance(tz)
    if (daysAgo > 0) {
      cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
    }
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
    return fmt.format(cal.time)
  }

  fun getBangladeshCurrentMonthStartIso(): String {
    val tz = TimeZone.getTimeZone("Asia/Dhaka")
    val cal = Calendar.getInstance(tz)
    val fmt = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = tz }
    return "${fmt.format(cal.time)}-01"
  }

  fun registerKnownLegacyProductionDocId(docId: String) {
    if (docId.isNotBlank() && !docId.startsWith("PROD_")) {
      knownLegacyProductionDocIds.add(docId)
    }
  }

  fun registerKnownLegacyQualityDocId(docId: String) {
    if (docId.isNotBlank() && !docId.startsWith("QA_")) {
      knownLegacyQualityDocIds.add(docId)
    }
  }

  fun shouldDeleteLegacyProductionDoc(record: ProductionRecord, canonicalDocId: String): Boolean {
    return record.id.isNotBlank() &&
        record.id != canonicalDocId &&
        knownLegacyProductionDocIds.contains(record.id)
  }

  fun shouldDeleteLegacyQualityDoc(record: QualityRecord, canonicalDocId: String): Boolean {
    return record.id.isNotBlank() &&
        record.id != canonicalDocId &&
        knownLegacyQualityDocIds.contains(record.id)
  }

  fun productionPayloadSignature(record: ProductionRecord): String {
    val docId = canonicalProductionDocId(record)
    return "$docId|${record.submissionId}|${record.lineId}|${record.floor}|${record.date}|${record.hourSlot}|${record.styleNumber}|${record.outputQty}|${record.remarks}|${record.modifiedBy}|${record.revision}|${record.isConfirmedZero}"
  }

  fun qualityPayloadSignature(record: QualityRecord): String {
    val docId = canonicalQualityDocId(record)
    val defectBreakdown = record.defects.joinToString(";") { "${it.defectType}:${it.count}" }
    return "$docId|${record.submissionId}|${record.lineId}|${record.floor}|${record.date}|${record.hourSlot}|${record.styleNumber}|${record.checkedGarments}|${record.defectiveGarments}|${record.totalDefects}|$defectBreakdown|${record.remarks}|${record.defectSent}|${record.defectReceived}|${record.defectBalance}|${record.spotSent}|${record.spotReceived}|${record.spotBalance}|${record.modifiedBy}|${record.revision}|${record.isConfirmedZero}"
  }

  fun isAvailable(): Boolean = firestore != null

  fun canonicalProductionDocId(record: ProductionRecord): String {
    val cleanLine = record.lineId.trim().replace("/", "_")
    val cleanDate = record.date.trim()
    val cleanHour = record.hourSlot.trim().replace(" ", "").replace(":", "")
    return if (cleanLine.isNotEmpty() && cleanDate.isNotEmpty() && cleanHour.isNotEmpty()) {
      "PROD_${cleanLine}_${cleanDate}_${cleanHour}"
    } else {
      record.id.ifBlank { record.submissionId }
    }
  }

  fun canonicalQualityDocId(record: QualityRecord): String {
    val cleanLine = record.lineId.trim().replace("/", "_")
    val cleanDate = record.date.trim()
    val cleanHour = record.hourSlot.trim().replace(" ", "").replace(":", "")
    return if (cleanLine.isNotEmpty() && cleanDate.isNotEmpty() && cleanHour.isNotEmpty()) {
      "QA_${cleanLine}_${cleanDate}_${cleanHour}"
    } else {
      record.id.ifBlank { record.submissionId }
    }
  }

  private fun handleListenerError(channelName: String, error: FirebaseFirestoreException) {
    lastErrorMessage = error.message
    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
      if (!hasPermissionError) {
        hasPermissionError = true
        Log.w(
          "FirebaseSync",
          "Firestore security rules on nfl-live-72213 restricted access ($channelName): ${error.message}. Deploy firestore.rules to enable multi-device Firestore sync."
        )
        cleanupListeners()
        onSyncStatusChanged?.invoke(
          "⚠️ Firestore permission denied on nfl-live-72213 (update firestore.rules in Firebase Console) • Using Google Sheets / Local cache"
        )
      }
    } else {
      Log.w("FirebaseSync", "$channelName listener notice: ${error.message}")
    }
  }

  fun hasActiveListeners(): Boolean = listeners.isNotEmpty() && !hasPermissionError

  fun startRealtimeListeners(forceRetry: Boolean = false) {
    if (listeners.isNotEmpty() && !hasPermissionError) {
      return
    }
    if (hasPermissionError && !forceRetry) {
      onSyncStatusChanged?.invoke(
        "⚠️ Firestore rules restricted on nfl-live-72213 • Tap Refresh after updating firestore.rules"
      )
      return
    }
    if (forceRetry) {
      hasPermissionError = false
      lastErrorMessage = null
    }
    val sevenDaysAgoIso = getBangladeshDateDaysAgoIso(7)
    lastListenerWindowStartIso = sevenDaysAgoIso
    val db = firestore ?: return
    cleanupListeners()

    try {
      // 1. Production entries listener (bounded to last 7 days in Asia/Dhaka, ordered by date DESC, limit 2000)
      val prodReg = db.collection("production_entries")
        .whereGreaterThanOrEqualTo("date", sevenDaysAgoIso)
        .orderBy("date", Query.Direction.DESCENDING)
        .limit(2000)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Production", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            lastErrorMessage = null
            val docChanges = snapshot.documentChanges
            val docs = snapshot.documents
            val isFromCache = snapshot.metadata.isFromCache
            snapshotScope.launch {
              val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
              recordCollectionRead(
                collection = "production_entries",
                changesCount = docChanges.size,
                isFromCache = isFromCache
              )
              val removedKeys = mutableSetOf<String>()
              docChanges.forEach { change ->
                val d = change.document
                val isDel = d.getBoolean("isDeleted") ?: false
                val dt = d.getString("date")?.trim().orEmpty()
                val isWithinWindow = dt.isEmpty() || dt >= sevenDaysAgoIso
                // Only treat as deleted if explicitly marked isDeleted or deleted while within the 7-day window
                if (isDel || (change.type == DocumentChange.Type.REMOVED && isWithinWindow)) {
                  removedKeys.add(d.id)
                  d.getString("id")?.let { removedKeys.add(it) }
                  d.getString("submissionId")?.let { removedKeys.add(it) }
                  val l = d.getString("lineId").orEmpty()
                  val hr = d.getString("hourSlot").orEmpty()
                  if (l.isNotEmpty() && dt.isNotEmpty() && hr.isNotEmpty()) {
                    removedKeys.add("${l}_${dt}_${hr}")
                  }
                }
              }
              if (removedKeys.isNotEmpty()) {
                onRemoteProductionDeleted?.invoke(removedKeys)
              }
              val list = docs.mapNotNull { doc -> parseProductionDoc(doc) }
              if (list.isNotEmpty()) {
                onRemoteProductionUpdated?.invoke(list)
                onSyncStatusChanged?.invoke("✓ Cloud Firestore live: ${list.size} recent production records active")
              }
              if (BuildConfig.DEBUG) {
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                Log.d("NflPerf", "snapshotCallback[production_entries] docs=${docs.size} changes=${docChanges.size} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
              }
            }
          }
        }
      listeners.add(prodReg)

      // 2. Quality entries listener (bounded to last 7 days in Asia/Dhaka, ordered by date DESC, limit 2000)
      val qaReg = db.collection("quality_entries")
        .whereGreaterThanOrEqualTo("date", sevenDaysAgoIso)
        .orderBy("date", Query.Direction.DESCENDING)
        .limit(2000)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Quality", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val docChanges = snapshot.documentChanges
            val docs = snapshot.documents
            val isFromCache = snapshot.metadata.isFromCache
            snapshotScope.launch {
              val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
              recordCollectionRead(
                collection = "quality_entries",
                changesCount = docChanges.size,
                isFromCache = isFromCache
              )
              val removedKeys = mutableSetOf<String>()
              docChanges.forEach { change ->
                val d = change.document
                val isDel = d.getBoolean("isDeleted") ?: false
                val dt = d.getString("date")?.trim().orEmpty()
                val isWithinWindow = dt.isEmpty() || dt >= sevenDaysAgoIso
                if (isDel || (change.type == DocumentChange.Type.REMOVED && isWithinWindow)) {
                  removedKeys.add(d.id)
                  d.getString("id")?.let { removedKeys.add(it) }
                  d.getString("submissionId")?.let { removedKeys.add(it) }
                  val l = d.getString("lineId").orEmpty()
                  val hr = d.getString("hourSlot").orEmpty()
                  if (l.isNotEmpty() && dt.isNotEmpty() && hr.isNotEmpty()) {
                    removedKeys.add("${l}_${dt}_${hr}")
                  }
                }
              }
              if (removedKeys.isNotEmpty()) {
                onRemoteQualityDeleted?.invoke(removedKeys)
              }
              val list = docs.mapNotNull { doc -> parseQualityDoc(doc) }
              if (list.isNotEmpty()) {
                onRemoteQualityUpdated?.invoke(list)
              }
              if (BuildConfig.DEBUG) {
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                Log.d("NflPerf", "snapshotCallback[quality_entries] docs=${docs.size} changes=${docChanges.size} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
              }
            }
          }
        }
      listeners.add(qaReg)

      // 3. Factory alerts / incidents listener (ordered by startTimeMillis DESC, limit 200)
      val alertReg = db.collection("factory_alerts")
        .orderBy("startTimeMillis", Query.Direction.DESCENDING)
        .limit(200)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Alerts", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val docChanges = snapshot.documentChanges
            val docs = snapshot.documents
            val isFromCache = snapshot.metadata.isFromCache
            snapshotScope.launch {
              val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
              recordCollectionRead(
                collection = "factory_alerts",
                changesCount = docChanges.size,
                isFromCache = isFromCache
              )
              val list = docs.mapNotNull { doc -> parseIncidentDoc(doc) }
              if (list.isNotEmpty()) {
                onRemoteIncidentsUpdated?.invoke(list)
              }
              if (BuildConfig.DEBUG) {
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                Log.d("NflPerf", "snapshotCallback[factory_alerts] docs=${docs.size} changes=${docChanges.size} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
              }
            }
          }
        }
      listeners.add(alertReg)

      // 4. Setups listener (bounded to last 7 days in Asia/Dhaka, ordered by date DESC, limit 2000)
      val setupReg = db.collection("daily_setups")
        .whereGreaterThanOrEqualTo("date", sevenDaysAgoIso)
        .orderBy("date", Query.Direction.DESCENDING)
        .limit(2000)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Setups", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val docChanges = snapshot.documentChanges
            val docs = snapshot.documents
            val isFromCache = snapshot.metadata.isFromCache
            snapshotScope.launch {
              val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
              recordCollectionRead(
                collection = "daily_setups",
                changesCount = docChanges.size,
                isFromCache = isFromCache
              )
              val list = docs.mapNotNull { doc -> parseSetupDoc(doc) }
              if (list.isNotEmpty()) {
                onRemoteSetupsUpdated?.invoke(list)
              }
              if (BuildConfig.DEBUG) {
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                Log.d("NflPerf", "snapshotCallback[daily_setups] docs=${docs.size} changes=${docChanges.size} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
              }
            }
          }
        }
      listeners.add(setupReg)

      // 5. Monthly targets listener (small collection)
      val monthlyReg = db.collection("monthly_targets")
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("MonthlyTargets", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val docChanges = snapshot.documentChanges
            val docs = snapshot.documents
            val isFromCache = snapshot.metadata.isFromCache
            snapshotScope.launch {
              val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
              recordCollectionRead(
                collection = "monthly_targets",
                changesCount = docChanges.size,
                isFromCache = isFromCache
              )
              val list = docs.mapNotNull { doc -> parseMonthlyTargetDoc(doc) }
              if (list.isNotEmpty()) {
                onRemoteMonthlyTargetsUpdated?.invoke(list)
              }
              if (BuildConfig.DEBUG) {
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                Log.d("NflPerf", "snapshotCallback[monthly_targets] docs=${docs.size} changes=${docChanges.size} took=${elapsedMs}ms thread=${Thread.currentThread().name}")
              }
            }
          }
        }
      listeners.add(monthlyReg)

      // 5b. Weekly BS OQL / NFL AQC (one small document per week, newest 26 weeks only)
      val weeklyReg = db.collection("weekly_quality")
        .orderBy("weekStartIso", Query.Direction.DESCENDING)
        .limit(26)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("WeeklyQuality", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val docChanges = snapshot.documentChanges
            val docs = snapshot.documents
            val isFromCache = snapshot.metadata.isFromCache
            snapshotScope.launch {
              recordCollectionRead(
                collection = "weekly_quality",
                changesCount = docChanges.size,
                isFromCache = isFromCache
              )
              val list = docs.mapNotNull { doc -> parseWeeklyQualityDoc(doc) }
              if (list.isNotEmpty()) {
                onRemoteWeeklyQualityUpdated?.invoke(list)
              }
            }
          }
        }
      listeners.add(weeklyReg)

      // 6. User accounts listener:
      // - Admin listens to full /users, /device_approval_requests, and /admin_notifications collections
      // - Non-admin listens only to their own /users/{employeeId} document (least-privilege RBAC)
      if (isActiveUserAdmin) {
        val usersReg = db.collection("users")
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              handleListenerError("Users", error)
              return@addSnapshotListener
            }
            if (snapshot != null) {
              hasPermissionError = false
              val docChanges = snapshot.documentChanges
              val docs = snapshot.documents
              val isFromCache = snapshot.metadata.isFromCache
              snapshotScope.launch {
                recordCollectionRead(
                  collection = "users",
                  changesCount = docChanges.size,
                  isFromCache = isFromCache
                )
                val list = docs.mapNotNull { doc -> parseUserDoc(doc) }
                if (list.isNotEmpty()) {
                  onRemoteUsersUpdated?.invoke(list)
                }
              }
            }
          }
        listeners.add(usersReg)

        val devReqReg = db.collection("device_approval_requests")
          .addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
              val docs = snapshot.documents
              snapshotScope.launch {
                val list = docs.mapNotNull { doc -> parseDeviceApprovalRequestDoc(doc) }
                if (list.isNotEmpty()) {
                  onRemoteDeviceRequestsUpdated?.invoke(list)
                }
              }
            }
          }
        listeners.add(devReqReg)

        val adminNotifReg = db.collection("admin_notifications")
          .whereEqualTo("status", "PENDING")
          .addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
              val docs = snapshot.documents
              snapshotScope.launch {
                val list = docs.mapNotNull { doc -> parseAdminNotificationDoc(doc) }
                if (list.isNotEmpty()) {
                  onRemoteAdminNotificationsUpdated?.invoke(list)
                }
              }
            }
          }
        listeners.add(adminNotifReg)
      } else if (activeUserIdForListener.isNotBlank()) {
        val selfDocId = activeUserIdForListener.trim().replace("/", "_")
        val selfUserReg = db.collection("users").document(selfDocId)
          .addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
              snapshotScope.launch {
                parseUserDoc(snapshot)?.let { parsedSelf ->
                  onRemoteUsersUpdated?.invoke(listOf(parsedSelf))
                }
              }
            }
          }
        listeners.add(selfUserReg)
      }

    } catch (e: Exception) {
      Log.w("FirebaseSync", "Notice setting up Firestore listeners: ${e.message}")
    }
  }

  private fun parseProductionDoc(doc: DocumentSnapshot): ProductionRecord? {
    return try {
      if (doc.getBoolean("isDeleted") == true) return null
      val lineId = doc.getString("lineId")?.trim().orEmpty()
      val date = doc.getString("date")?.trim().orEmpty()
      val hourSlot = doc.getString("hourSlot")?.trim().orEmpty()
      if (lineId.isEmpty() || date.isEmpty() || hourSlot.isEmpty()) return null
      val parsed = ProductionRecord(
        id = doc.getString("id") ?: doc.id,
        submissionId = doc.getString("submissionId") ?: doc.id,
        lineId = lineId,
        floor = doc.getString("floor") ?: "Sew 2",
        date = date,
        hourSlot = hourSlot,
        styleNumber = doc.getString("styleNumber") ?: "",
        outputQty = doc.getLong("outputQty")?.toInt() ?: 0,
        remarks = doc.getString("remarks") ?: "",
        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
        syncStatus = SyncStatus.SYNCED,
        revision = doc.getLong("revision")?.toInt() ?: 1,
        modifiedBy = doc.getString("modifiedBy") ?: "Production Reporter",
        isConfirmedZero = doc.getBoolean("isConfirmedZero") ?: false,
        isDemo = false
      )
      val canonicalId = canonicalProductionDocId(parsed)
      if (doc.id != canonicalId) {
        knownLegacyProductionDocIds.add(doc.id)
      }
      lastPushedProductionSignatures[canonicalId] = productionPayloadSignature(parsed)
      parsed
    } catch (e: Exception) {
      null
    }
  }

  private fun parseQualityDoc(doc: DocumentSnapshot): QualityRecord? {
    return try {
      if (doc.getBoolean("isDeleted") == true) return null
      val lineId = doc.getString("lineId")?.trim().orEmpty()
      val date = doc.getString("date")?.trim().orEmpty()
      val hourSlot = doc.getString("hourSlot")?.trim().orEmpty()
      if (lineId.isEmpty() || date.isEmpty() || hourSlot.isEmpty()) return null

      val parsedDefects = mutableListOf<DefectItem>()
      val rawDefectsList = doc.get("defects") as? List<*>
      if (rawDefectsList != null) {
        rawDefectsList.forEach { item ->
          val map = item as? Map<*, *>
          if (map != null) {
            val dType = (map["defectType"] as? String)?.trim().orEmpty()
            val dCount = (map["count"] as? Number)?.toInt() ?: 0
            if (dType.isNotEmpty() && dCount > 0) {
              parsedDefects.add(DefectItem(dType, dCount))
            }
          }
        }
      }
      if (parsedDefects.isEmpty()) {
        val breakdownStr = doc.getString("defectBreakdown").orEmpty()
        if (breakdownStr.isNotBlank()) {
          breakdownStr.split(";").forEach { token ->
            val parts = token.split(":")
            if (parts.size >= 2) {
              val dType = parts[0].trim()
              val dCount = parts[1].trim().toIntOrNull() ?: 0
              if (dType.isNotEmpty() && dCount > 0) {
                parsedDefects.add(DefectItem(dType, dCount))
              }
            }
          }
        }
      }

      val defectiveGarments = doc.getLong("defectiveGarments")?.toInt() ?: 0
      val totalDefects = doc.getLong("totalDefects")?.toInt()
        ?: parsedDefects.sumOf { it.count }.let { if (it > 0) it else defectiveGarments }
      val defectSent = doc.getLong("defectSent")?.toInt() ?: 0
      val defectReceived = doc.getLong("defectReceived")?.toInt() ?: 0
      val defectBalance = doc.getLong("defectBalance")?.toInt() ?: (defectSent - defectReceived).coerceAtLeast(0)
      val spotSent = doc.getLong("spotSent")?.toInt() ?: 0
      val spotReceived = doc.getLong("spotReceived")?.toInt() ?: 0
      val spotBalance = doc.getLong("spotBalance")?.toInt() ?: (spotSent - spotReceived).coerceAtLeast(0)

      val parsed = QualityRecord(
        id = doc.getString("id") ?: doc.id,
        submissionId = doc.getString("submissionId") ?: doc.id,
        lineId = lineId,
        floor = doc.getString("floor") ?: "Sew 2",
        date = date,
        hourSlot = hourSlot,
        styleNumber = doc.getString("styleNumber") ?: "",
        checkedGarments = doc.getLong("checkedGarments")?.toInt() ?: 0,
        defectiveGarments = defectiveGarments,
        defects = parsedDefects,
        totalDefects = totalDefects,
        remarks = doc.getString("remarks") ?: "",
        defectSent = defectSent,
        defectReceived = defectReceived,
        defectBalance = defectBalance,
        spotSent = spotSent,
        spotReceived = spotReceived,
        spotBalance = spotBalance,
        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
        syncStatus = SyncStatus.SYNCED,
        revision = doc.getLong("revision")?.toInt() ?: 1,
        modifiedBy = doc.getString("modifiedBy") ?: "Quality Inspector",
        isConfirmedZero = doc.getBoolean("isConfirmedZero") ?: false,
        isDemo = false
      )
      val canonicalId = canonicalQualityDocId(parsed)
      if (doc.id.isNotBlank() && doc.id != canonicalId) {
        knownLegacyQualityDocIds.add(doc.id)
      }
      lastPushedQualitySignatures[canonicalId] = qualityPayloadSignature(parsed)
      parsed
    } catch (e: Exception) {
      null
    }
  }

  private fun parseIncidentDoc(doc: DocumentSnapshot): IncidentItem? {
    return try {
      val catStr = doc.getString("category") ?: IncidentCategory.MACHINE_BREAKDOWN.name
      val cat = try { IncidentCategory.valueOf(catStr) } catch (_: Exception) { IncidentCategory.MACHINE_BREAKDOWN }
      val statusStr = doc.getString("status") ?: IncidentStatus.OPEN.name
      val st = try { IncidentStatus.valueOf(statusStr) } catch (_: Exception) { IncidentStatus.OPEN }
      val sevStr = doc.getString("severity") ?: AlertSeverity.HIGH.name
      val sev = try { AlertSeverity.valueOf(sevStr) } catch (_: Exception) { AlertSeverity.HIGH }

      val machineId = doc.getString("machineId") ?: doc.getString("machineRef") ?: ""
      val machineRef = doc.getString("machineRef") ?: machineId

      IncidentItem(
        id = doc.getString("id") ?: doc.id,
        submissionId = doc.getString("submissionId") ?: doc.id,
        lineId = doc.getString("lineId") ?: "",
        floor = doc.getString("floor") ?: "Sew 2",
        category = cat,
        styleNumber = doc.getString("styleNumber") ?: "",
        nptType = doc.getString("nptType") ?: cat.displayName,
        machineId = machineId,
        machineRef = machineRef,
        description = doc.getString("description") ?: "",
        reportedCause = doc.getString("reportedCause") ?: "",
        impactLevel = doc.getString("impactLevel") ?: sev.name,
        severity = sev,
        status = st,
        affectedManpower = doc.getLong("affectedManpower")?.toInt() ?: 6,
        startTimeMillis = doc.getLong("startTimeMillis") ?: System.currentTimeMillis(),
        endTimeMillis = doc.getLong("endTimeMillis"),
        acknowledgedTimeMillis = doc.getLong("acknowledgedTimeMillis"),
        acknowledgedBy = doc.getString("acknowledgedBy"),
        mechanicStartTimeMillis = doc.getLong("mechanicStartTimeMillis"),
        inProgressTimeMillis = doc.getLong("inProgressTimeMillis"),
        mechanicName = doc.getString("mechanicName"),
        isMaintenanceStarted = doc.getBoolean("isMaintenanceStarted") ?: false,
        resolvedBy = doc.getString("resolvedBy"),
        resolutionNote = doc.getString("resolutionNote") ?: "",
        verifiedBy = doc.getString("verifiedBy"),
        verifiedTimeMillis = doc.getLong("verifiedTimeMillis"),
        correctiveAction = doc.getString("correctiveAction") ?: "",
        isEscalated = doc.getBoolean("isEscalated") ?: false,
        escalationReason = doc.getString("escalationReason") ?: "",
        escalatedAtMillis = doc.getLong("escalatedAtMillis"),
        measuredQualityValue = doc.getDouble("measuredQualityValue"),
        qualityThresholdValue = doc.getDouble("qualityThresholdValue"),
        defectDetails = doc.getString("defectDetails") ?: "",
        reportedBy = doc.getString("reportedBy") ?: "Supervisor",
        isDemo = false
      )
    } catch (e: Exception) {
      null
    }
  }

  private fun parseSetupDoc(doc: DocumentSnapshot): SupervisorLineSetup? {
    return try {
      val lineId = doc.getString("lineId") ?: doc.id.substringBefore("_")
      val date = doc.getString("date") ?: doc.id.substringAfter("_", "")
      SupervisorLineSetup(
        id = doc.getString("id") ?: "${lineId}_${date}",
        lineId = lineId,
        floor = doc.getString("floor") ?: "Sew 2",
        date = date,
        styleNumber = doc.getString("styleNumber") ?: "",
        itemName = doc.getString("itemName") ?: "Polo Shirt",
        orderNumber = doc.getString("orderNumber") ?: "PO-88210",
        smv = doc.getDouble("smv") ?: 14.5,
        targetHourlyPcs = doc.getLong("targetHourlyPcs")?.toInt() ?: 60,
        targetDailyPcs = doc.getLong("targetDailyPcs")?.toInt() ?: 480,
        targetEfficiencyPct = doc.getDouble("targetEfficiencyPct") ?: 75.0,
        operatorsCount = doc.getLong("operatorsCount")?.toInt() ?: 20,
        machinesCount = doc.getLong("machinesCount")?.toInt() ?: 20,
        helpersCount = doc.getLong("helpersCount")?.toInt() ?: 2,
        finishingStaffCount = doc.getLong("finishingStaffCount")?.toInt() ?: 4,
        includeHelpersInEfficiency = doc.getBoolean("includeHelpersInEfficiency") ?: false,
        workingMinutesPerHour = doc.getLong("workingMinutesPerHour")?.toInt() ?: 60,
        workingHoursSoFar = doc.getLong("workingHoursSoFar")?.toInt() ?: 7,
        totalShiftHours = doc.getLong("totalShiftHours")?.toInt() ?: 8,
        snapshotTimestamp = doc.getLong("snapshotTimestamp") ?: System.currentTimeMillis(),
        isDemo = false
      )
    } catch (e: Exception) {
      null
    }
  }

  private fun parseMonthlyTargetDoc(doc: DocumentSnapshot): MonthlyFactoryTarget? {
    return try {
      MonthlyFactoryTarget(
        floor = doc.getString("floor") ?: doc.id,
        monthYear = doc.getString("monthYear") ?: "October 2026",
        targetEfficiencyPct = doc.getDouble("targetEfficiencyPct") ?: 72.0,
        updateEfficiencyPct = doc.getDouble("updateEfficiencyPct") ?: 68.5,
        targetOqlPct = doc.getDouble("targetOqlPct") ?: 3.50,
        targetDhuPct = doc.getDouble("targetDhuPct") ?: 2.00,
        friPassRatePct = doc.getDouble("friPassRatePct") ?: 100.0,
        bsOqlPct = doc.getDouble("bsOqlPct") ?: 2.40,
        lastUpdatedBy = doc.getString("lastUpdatedBy") ?: "Management / IE",
        lastUpdatedMillis = doc.getLong("lastUpdatedMillis") ?: System.currentTimeMillis(),
        lineId = doc.getString("lineId") ?: "",
        monthlyTargetPcs = doc.getDouble("monthlyTargetPcs") ?: 0.0,
        monthlyAchievementPcs = doc.getDouble("monthlyAchievementPcs") ?: 0.0
      )
    } catch (e: Exception) {
      null
    }
  }

  private fun parseWeeklyQualityDoc(doc: DocumentSnapshot): WeeklyQualityEntry? {
    return try {
      val key = doc.getString("weekKey")?.takeIf { it.isNotBlank() } ?: doc.id
      val start = doc.getString("weekStartIso") ?: return null
      WeeklyQualityEntry(
        weekKey = key,
        weekStartIso = start,
        bsOqlPct = doc.getDouble("bsOqlPct"),
        nflAqcPct = doc.getDouble("nflAqcPct"),
        lastUpdatedBy = doc.getString("lastUpdatedBy") ?: "",
        lastUpdatedMillis = doc.getLong("lastUpdatedMillis") ?: 0L
      )
    } catch (e: Exception) {
      null
    }
  }

  private fun parseFloorScope(floorStr: String?, fallback: FloorScope): FloorScope {
    if (floorStr.isNullOrBlank()) return fallback
    return FloorScope.entries.find {
      it.name.equals(floorStr, ignoreCase = true) ||
          it.apiValue.equals(floorStr, ignoreCase = true) ||
          it.displayName.equals(floorStr, ignoreCase = true)
    } ?: fallback
  }

  private fun parseUserDoc(doc: DocumentSnapshot): UserAccount? {
    return try {
      if (doc.getBoolean("isDeleted") == true) return null
      val id = doc.getString("id")?.trim().orEmpty().ifEmpty { doc.id }
      if (id.isEmpty()) return null
      val roleStr = doc.getString("role") ?: UserRole.PRODUCTION_REPORTER.name
      val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.PRODUCTION_REPORTER }
      val floor = parseFloorScope(doc.getString("floor"), FloorScope.SEW_2)
      val requestedFloor = parseFloorScope(doc.getString("requestedFloor"), floor)
      val isAdmin = doc.getBoolean("isAdmin") ?: (role == UserRole.ADMIN)
      val authorizedScope = if (isAdmin) {
        FloorScope.ALL
      } else {
        parseFloorScope(doc.getString("authorizedFloorScope"), floor)
      }
      val statusStr = doc.getString("accountStatus") ?: AccountStatus.APPROVED.name
      val accStatus = try { AccountStatus.valueOf(statusStr) } catch (_: Exception) { AccountStatus.APPROVED }

      val legacyPlainPassword = doc.getString("password").orEmpty()
      val salt = doc.getString("passwordSalt").orEmpty()
      val hash = doc.getString("passwordHash").orEmpty().ifBlank {
        if (legacyPlainPassword.isNotBlank()) {
          GoogleSheetsStorageService.hashPassword(legacyPlainPassword, salt)
        } else {
          ""
        }
      }
      val authDevId = doc.getString("authorizedDeviceId").orEmpty()
      val authDevName = doc.getString("authorizedDeviceName").orEmpty().ifBlank { "Android Device" }
      val approvedDevices = if (authDevId.isNotBlank()) {
        listOf(
          ApprovedDevice(
            deviceId = authDevId,
            deviceName = authDevName,
            approvedAtMillis = doc.getLong("approvedAtMillis") ?: System.currentTimeMillis(),
            approvedBy = doc.getString("approvedBy").orEmpty().ifBlank { com.example.model.INITIAL_ADMIN_EMPLOYEE_ID }
          )
        )
      } else {
        emptyList()
      }

      UserAccount(
        id = id,
        employeeId = doc.getString("employeeId") ?: id,
        name = doc.getString("name") ?: id,
        password = "",
        passwordHash = hash,
        passwordSalt = salt,
        designation = doc.getString("designation") ?: (if (isAdmin) "Admin" else role.displayName),
        department = doc.getString("department") ?: role.group,
        requestedDesignation = doc.getString("requestedDesignation") ?: (doc.getString("designation") ?: role.displayName),
        requestedFloor = requestedFloor,
        role = role,
        floor = floor,
        authorizedFloorScope = authorizedScope,
        assignedLine = doc.getString("assignedLine") ?: "K1",
        isActive = doc.getBoolean("isActive") ?: (accStatus == AccountStatus.APPROVED),
        accountStatus = accStatus,
        isAdmin = isAdmin,
        mustChangePassword = doc.getBoolean("mustChangePassword") ?: false,
        activeSessionId = doc.getString("activeSessionId") ?: "",
        authorizedDeviceId = authDevId,
        authorizedDeviceName = if (authDevId.isNotBlank()) authDevName else "",
        rejectionReason = doc.getString("rejectionReason") ?: "",
        createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis(),
        updatedAtMillis = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis(),
        approvedBy = doc.getString("approvedBy") ?: "",
        approvedAtMillis = doc.getLong("approvedAtMillis") ?: 0L,
        canViewDashboard = doc.getBoolean("canViewDashboard") ?: true,
        canViewTvDashboard = doc.getBoolean("canViewTvDashboard") ?: true,
        canViewMonthlyEfficiency = doc.getBoolean("canViewMonthlyEfficiency") ?: true,
        canViewMonthlyDhu = doc.getBoolean("canViewMonthlyDhu") ?: true,
        canViewMonthlyOql = doc.getBoolean("canViewMonthlyOql") ?: true,
        canViewLineDeepDive = doc.getBoolean("canViewLineDeepDive") ?: true,
        canViewAuditLogs = doc.getBoolean("canViewAuditLogs") ?: isAdmin,
        canExportReports = doc.getBoolean("canExportReports") ?: isAdmin,
        canProductionWrite = doc.getBoolean("canProductionWrite") ?: false,
        canFinishingWrite = doc.getBoolean("canFinishingWrite") ?: false,
        canQualityWrite = doc.getBoolean("canQualityWrite") ?: false,
        canOqlWrite = doc.getBoolean("canOqlWrite") ?: false,
        canNptWrite = doc.getBoolean("canNptWrite") ?: false,
        canMorningTargetWrite = doc.getBoolean("canMorningTargetWrite") ?: false,
        canMonthlyTargetWrite = doc.getBoolean("canMonthlyTargetWrite") ?: false,
        canTargetRevisionWrite = doc.getBoolean("canTargetRevisionWrite") ?: false,
        approvedDevices = approvedDevices,
        firebaseUid = doc.getString("firebaseUid").orEmpty(),
        googleEmail = doc.getString("googleEmail").orEmpty(),
        fcmToken = doc.getString("fcmToken").orEmpty(),
        fcmDeviceId = doc.getString("fcmDeviceId").orEmpty(),
        fcmUpdatedAtMillis = doc.getLong("fcmUpdatedAtMillis") ?: 0L
      )
    } catch (e: Exception) {
      null
    }
  }

  private fun parseDeviceApprovalRequestDoc(doc: DocumentSnapshot): DeviceApprovalRequest? {
    return try {
      val reqId = doc.getString("requestId")?.ifBlank { doc.id } ?: doc.id
      val devId = doc.getString("deviceId").orEmpty()
      if (devId.isBlank()) return null
      val userId = doc.getString("userId")?.ifBlank { devId } ?: devId
      val userName = doc.getString("deviceName") ?: doc.getString("userName") ?: devId
      DeviceApprovalRequest(
        requestId = reqId,
        userId = userId,
        userName = userName,
        employeeId = doc.getString("employeeId")?.ifBlank { userId } ?: userId,
        employeeName = doc.getString("employeeName")?.ifBlank { userName } ?: userName,
        requestedFloor = doc.getString("requestedFloor") ?: "Sew-1",
        deviceId = devId,
        deviceName = doc.getString("deviceName") ?: userName,
        requestType = doc.getString("requestType") ?: "TV_DEVICE_APPROVAL",
        requestedAtMillis = doc.getLong("createdAtMillis") ?: doc.getLong("requestedAtMillis") ?: System.currentTimeMillis(),
        status = doc.getString("status") ?: "PENDING",
        reason = doc.getString("reason") ?: "Smart TV / Device Authorization"
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun parseAdminNotificationDoc(doc: DocumentSnapshot): AdminNotificationPayload? {
    return try {
      val reqId = doc.getString("requestId").orEmpty()
      val targetUserId = doc.getString("targetUserId").orEmpty()
      if (reqId.isBlank() || targetUserId.isBlank()) return null
      AdminNotificationPayload(
        notificationId = doc.getString("notificationId")?.ifBlank { doc.id } ?: doc.id,
        requestId = reqId,
        requestType = doc.getString("requestType") ?: "EMPLOYEE_REGISTRATION",
        targetUserId = targetUserId,
        title = doc.getString("title") ?: "New Approval Request",
        summary = doc.getString("summary") ?: "Pending admin review",
        requestedRoleOrDevice = doc.getString("requestedRoleOrDevice").orEmpty(),
        requestedFloor = doc.getString("requestedFloor").orEmpty(),
        status = doc.getString("status") ?: "PENDING",
        createdByUid = doc.getString("createdByUid").orEmpty(),
        createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
      )
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Bounded, cache-friendly month-level fetch for current month (`yyyy-MM-01` in Asia/Dhaka).
   * Called at most once per hour when a Monthly screen or Monthly filter is opened.
   */
  suspend fun fetchCurrentMonthFromFirestore(
    monthStartIso: String = getBangladeshCurrentMonthStartIso()
  ): FirestoreFetchResult = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext FirestoreFetchResult(false, "FirebaseFirestore not initialized")
    try {
      withTimeout(12000L) {
        val prodSnap = db.collection("production_entries")
          .whereGreaterThanOrEqualTo("date", monthStartIso)
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(2000)
          .get()
          .await()
        recordCollectionRead("production_entries", prodSnap.documentChanges.size, prodSnap.metadata.isFromCache)

        val qaSnap = db.collection("quality_entries")
          .whereGreaterThanOrEqualTo("date", monthStartIso)
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(2000)
          .get()
          .await()
        recordCollectionRead("quality_entries", qaSnap.documentChanges.size, qaSnap.metadata.isFromCache)

        val setupSnap = db.collection("daily_setups")
          .whereGreaterThanOrEqualTo("date", monthStartIso)
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(2000)
          .get()
          .await()
        recordCollectionRead("daily_setups", setupSnap.documentChanges.size, setupSnap.metadata.isFromCache)

        hasPermissionError = false
        lastErrorMessage = null

        FirestoreFetchResult(
          success = true,
          production = prodSnap.documents.mapNotNull { parseProductionDoc(it) },
          quality = qaSnap.documents.mapNotNull { parseQualityDoc(it) },
          setups = setupSnap.documents.mapNotNull { parseSetupDoc(it) }
        )
      }
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore monthly fetch notice: ${e.message}")
      FirestoreFetchResult(false, e.message ?: "Firestore monthly fetch failed")
    }
  }

  suspend fun fetchAllFromFirestore(): FirestoreFetchResult = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext FirestoreFetchResult(false, "FirebaseFirestore not initialized")
    val sevenDaysAgoIso = getBangladeshDateDaysAgoIso(7)
    try {
      withTimeout(12000L) {
        val prodSnap = db.collection("production_entries")
          .whereGreaterThanOrEqualTo("date", sevenDaysAgoIso)
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(2000)
          .get()
          .await()
        recordCollectionRead("production_entries", prodSnap.documentChanges.size, prodSnap.metadata.isFromCache)

        val qaSnap = db.collection("quality_entries")
          .whereGreaterThanOrEqualTo("date", sevenDaysAgoIso)
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(2000)
          .get()
          .await()
        recordCollectionRead("quality_entries", qaSnap.documentChanges.size, qaSnap.metadata.isFromCache)

        val incSnap = db.collection("factory_alerts")
          .orderBy("startTimeMillis", Query.Direction.DESCENDING)
          .limit(200)
          .get()
          .await()
        recordCollectionRead("factory_alerts", incSnap.documentChanges.size, incSnap.metadata.isFromCache)

        val setupSnap = db.collection("daily_setups")
          .whereGreaterThanOrEqualTo("date", sevenDaysAgoIso)
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(2000)
          .get()
          .await()
        recordCollectionRead("daily_setups", setupSnap.documentChanges.size, setupSnap.metadata.isFromCache)

        val monthlySnap = db.collection("monthly_targets").get().await()
        recordCollectionRead("monthly_targets", monthlySnap.documentChanges.size, monthlySnap.metadata.isFromCache)

        val fetchedUsers = if (isActiveUserAdmin) {
          val usersSnap = db.collection("users").get().await()
          recordCollectionRead("users", usersSnap.documentChanges.size, usersSnap.metadata.isFromCache)
          usersSnap.documents.mapNotNull { parseUserDoc(it) }
        } else if (activeUserIdForListener.isNotBlank()) {
          val selfDoc = db.collection("users").document(activeUserIdForListener.trim().replace("/", "_")).get().await()
          listOfNotNull(if (selfDoc.exists()) parseUserDoc(selfDoc) else null)
        } else {
          emptyList()
        }

        hasPermissionError = false
        lastErrorMessage = null

        FirestoreFetchResult(
          success = true,
          production = prodSnap.documents.mapNotNull { parseProductionDoc(it) },
          quality = qaSnap.documents.mapNotNull { parseQualityDoc(it) },
          incidents = incSnap.documents.mapNotNull { parseIncidentDoc(it) },
          setups = setupSnap.documents.mapNotNull { parseSetupDoc(it) },
          monthlyTargets = monthlySnap.documents.mapNotNull { parseMonthlyTargetDoc(it) },
          users = fetchedUsers
        )
      }
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore fetchAll notice: ${e.message}")
      FirestoreFetchResult(false, e.message ?: "Firestore connection failed")
    }
  }

  suspend fun pushProductionRecordAwait(record: ProductionRecord): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = canonicalProductionDocId(record)
      val sig = productionPayloadSignature(record)
      if (lastPushedProductionSignatures[docId] == sig && !shouldDeleteLegacyProductionDoc(record, docId)) {
        return@withContext true
      }
      val map = hashMapOf(
        "id" to docId,
        "submissionId" to record.submissionId,
        "lineId" to record.lineId,
        "floor" to record.floor,
        "date" to record.date,
        "hourSlot" to record.hourSlot,
        "styleNumber" to record.styleNumber,
        "outputQty" to record.outputQty,
        "remarks" to record.remarks,
        "modifiedBy" to record.modifiedBy,
        "timestamp" to record.timestamp,
        "timestampMillis" to record.timestamp,
        "revision" to record.revision,
        "isConfirmedZero" to record.isConfirmedZero,
        "isDemo" to false,
        "isDeleted" to false
      )
      withTimeout(10000L) {
        db.collection("production_entries").document(docId).set(map, SetOptions.merge()).await()
        if (shouldDeleteLegacyProductionDoc(record, docId)) {
          // Only clean up a legacy non-canonical duplicate doc if it was actually observed in Firestore
          knownLegacyProductionDocIds.remove(record.id)
          runCatching { db.collection("production_entries").document(record.id).delete().await() }
        }
      }
      lastPushedProductionSignatures[docId] = sig
      hasPermissionError = false
      lastErrorMessage = null
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore production push failed: ${e.message}")
      false
    }
  }

  fun pushProductionRecord(record: ProductionRecord, onResult: ((Boolean, String?) -> Unit)? = null) {
    serviceScope.launch {
      val ok = pushProductionRecordAwait(record)
      onResult?.invoke(ok, lastErrorMessage)
    }
  }

  suspend fun deleteProductionRecordAwait(record: ProductionRecord): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = canonicalProductionDocId(record)
      withTimeout(10000L) {
        db.collection("production_entries").document(docId).delete().await()
        if (shouldDeleteLegacyProductionDoc(record, docId)) {
          knownLegacyProductionDocIds.remove(record.id)
          runCatching { db.collection("production_entries").document(record.id).delete().await() }
        }
      }
      lastPushedProductionSignatures.remove(docId)
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore delete production failed: ${e.message}")
      false
    }
  }

  suspend fun pushQualityRecordAwait(record: QualityRecord): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = canonicalQualityDocId(record)
      val sig = qualityPayloadSignature(record)
      if (lastPushedQualitySignatures[docId] == sig && !shouldDeleteLegacyQualityDoc(record, docId)) {
        return@withContext true
      }
      val defectsList = record.defects.map {
        mapOf("defectType" to it.defectType, "count" to it.count)
      }
      val defectBreakdown = record.defects.joinToString(";") { "${it.defectType}:${it.count}" }
      val map = hashMapOf(
        "id" to docId,
        "submissionId" to record.submissionId,
        "lineId" to record.lineId,
        "floor" to record.floor,
        "date" to record.date,
        "hourSlot" to record.hourSlot,
        "styleNumber" to record.styleNumber,
        "checkedGarments" to record.checkedGarments,
        "defectiveGarments" to record.defectiveGarments,
        "totalDefects" to record.totalDefects,
        "defects" to defectsList,
        "defectBreakdown" to defectBreakdown,
        "remarks" to record.remarks,
        "defectSent" to record.defectSent,
        "defectReceived" to record.defectReceived,
        "defectBalance" to record.defectBalance,
        "spotSent" to record.spotSent,
        "spotReceived" to record.spotReceived,
        "spotBalance" to record.spotBalance,
        "modifiedBy" to record.modifiedBy,
        "timestamp" to record.timestamp,
        "timestampMillis" to record.timestamp,
        "revision" to record.revision,
        "isConfirmedZero" to record.isConfirmedZero,
        "isDemo" to false,
        "isDeleted" to false
      )
      withTimeout(10000L) {
        db.collection("quality_entries").document(docId).set(map, SetOptions.merge()).await()
        if (shouldDeleteLegacyQualityDoc(record, docId)) {
          knownLegacyQualityDocIds.remove(record.id)
          runCatching { db.collection("quality_entries").document(record.id).delete().await() }
        }
      }
      lastPushedQualitySignatures[docId] = sig
      hasPermissionError = false
      lastErrorMessage = null
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore quality push failed: ${e.message}")
      false
    }
  }

  fun pushQualityRecord(record: QualityRecord, onResult: ((Boolean, String?) -> Unit)? = null) {
    serviceScope.launch {
      val ok = pushQualityRecordAwait(record)
      onResult?.invoke(ok, lastErrorMessage)
    }
  }

  suspend fun deleteQualityRecordAwait(record: QualityRecord): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = canonicalQualityDocId(record)
      withTimeout(10000L) {
        db.collection("quality_entries").document(docId).delete().await()
        lastPushedQualitySignatures.remove(docId)
        if (shouldDeleteLegacyQualityDoc(record, docId)) {
          runCatching { db.collection("quality_entries").document(record.id).delete().await() }
          knownLegacyQualityDocIds.remove(record.id)
        }
      }
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore delete quality failed: ${e.message}")
      false
    }
  }

  suspend fun pushIncidentAwait(incident: IncidentItem): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val map = hashMapOf(
        "id" to incident.id,
        "submissionId" to incident.submissionId,
        "lineId" to incident.lineId,
        "floor" to incident.floor,
        "category" to incident.category.name,
        "styleNumber" to incident.styleNumber,
        "nptType" to incident.nptType,
        "machineId" to incident.machineId.ifBlank { incident.machineRef },
        "machineRef" to incident.machineRef.ifBlank { incident.machineId },
        "description" to incident.description,
        "reportedCause" to incident.reportedCause,
        "impactLevel" to incident.impactLevel,
        "severity" to incident.severity.name,
        "status" to incident.status.name,
        "affectedManpower" to incident.affectedManpower,
        "startTimeMillis" to incident.startTimeMillis,
        "endTimeMillis" to incident.endTimeMillis,
        "acknowledgedTimeMillis" to incident.acknowledgedTimeMillis,
        "acknowledgedBy" to incident.acknowledgedBy,
        "mechanicStartTimeMillis" to incident.mechanicStartTimeMillis,
        "inProgressTimeMillis" to incident.inProgressTimeMillis,
        "mechanicName" to incident.mechanicName,
        "isMaintenanceStarted" to incident.isMaintenanceStarted,
        "resolvedBy" to incident.resolvedBy,
        "resolutionNote" to incident.resolutionNote,
        "verifiedBy" to incident.verifiedBy,
        "verifiedTimeMillis" to incident.verifiedTimeMillis,
        "correctiveAction" to incident.correctiveAction,
        "isEscalated" to incident.isEscalated,
        "escalationReason" to incident.escalationReason,
        "escalatedAtMillis" to incident.escalatedAtMillis,
        "measuredQualityValue" to incident.measuredQualityValue,
        "qualityThresholdValue" to incident.qualityThresholdValue,
        "defectDetails" to incident.defectDetails,
        "reportedBy" to incident.reportedBy,
        "isDemo" to false
      )
      withTimeout(10000L) {
        db.collection("factory_alerts").document(incident.id).set(map, SetOptions.merge()).await()
      }
      hasPermissionError = false
      lastErrorMessage = null
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore incident push failed: ${e.message}")
      false
    }
  }

  fun pushIncident(incident: IncidentItem, onResult: ((Boolean, String?) -> Unit)? = null) {
    serviceScope.launch {
      val ok = pushIncidentAwait(incident)
      onResult?.invoke(ok, lastErrorMessage)
    }
  }

  suspend fun pushLineSetupAwait(setup: SupervisorLineSetup): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = "${setup.lineId.trim()}_${setup.date.trim()}"
      val map = hashMapOf(
        "id" to docId,
        "lineId" to setup.lineId,
        "floor" to setup.floor,
        "date" to setup.date,
        "styleNumber" to setup.styleNumber,
        "itemName" to setup.itemName,
        "orderNumber" to setup.orderNumber,
        "smv" to setup.smv,
        "targetHourlyPcs" to setup.targetHourlyPcs,
        "targetDailyPcs" to setup.targetDailyPcs,
        "targetEfficiencyPct" to setup.targetEfficiencyPct,
        "operatorsCount" to setup.operatorsCount,
        "machinesCount" to setup.machinesCount,
        "helpersCount" to setup.helpersCount,
        "finishingStaffCount" to setup.finishingStaffCount,
        "includeHelpersInEfficiency" to setup.includeHelpersInEfficiency,
        "workingMinutesPerHour" to setup.workingMinutesPerHour,
        "workingHoursSoFar" to setup.workingHoursSoFar,
        "totalShiftHours" to setup.totalShiftHours,
        "snapshotTimestamp" to setup.snapshotTimestamp,
        "isDemo" to false
      )
      withTimeout(10000L) {
        db.collection("daily_setups").document(docId).set(map, SetOptions.merge()).await()
      }
      hasPermissionError = false
      lastErrorMessage = null
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore setup push failed: ${e.message}")
      false
    }
  }

  fun pushLineSetup(setup: SupervisorLineSetup, onResult: ((Boolean, String?) -> Unit)? = null) {
    serviceScope.launch {
      val ok = pushLineSetupAwait(setup)
      onResult?.invoke(ok, lastErrorMessage)
    }
  }

  suspend fun pushMonthlyTargetAwait(target: MonthlyFactoryTarget): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val map = hashMapOf(
        "floor" to target.floor,
        "monthYear" to target.monthYear,
        "targetEfficiencyPct" to target.targetEfficiencyPct,
        "updateEfficiencyPct" to target.updateEfficiencyPct,
        "targetOqlPct" to target.targetOqlPct,
        "targetDhuPct" to target.targetDhuPct,
        "friPassRatePct" to target.friPassRatePct,
        "bsOqlPct" to target.bsOqlPct,
        "lastUpdatedBy" to target.lastUpdatedBy,
        "lastUpdatedMillis" to target.lastUpdatedMillis,
        "lineId" to target.lineId,
        "monthlyTargetPcs" to target.monthlyTargetPcs,
        "monthlyAchievementPcs" to target.monthlyAchievementPcs
      )
      val documentId = if (target.lineId.isBlank()) target.floor else "${target.floor}_${target.lineId}"
      withTimeout(10000L) {
        db.collection("monthly_targets").document(documentId).set(map, SetOptions.merge()).await()
      }
      hasPermissionError = false
      lastErrorMessage = null
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore monthly target push failed: ${e.message}")
      false
    }
  }

  fun pushMonthlyTarget(target: MonthlyFactoryTarget, onResult: ((Boolean, String?) -> Unit)? = null) {
    serviceScope.launch {
      val ok = pushMonthlyTargetAwait(target)
      onResult?.invoke(ok, lastErrorMessage)
    }
  }

  suspend fun pushWeeklyQualityAwait(entry: WeeklyQualityEntry): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val map = hashMapOf<String, Any>(
        "weekKey" to entry.weekKey,
        "weekStartIso" to entry.weekStartIso,
        "lastUpdatedBy" to entry.lastUpdatedBy,
        "lastUpdatedMillis" to entry.lastUpdatedMillis
      )
      // Only write values that were actually entered; never write nulls.
      entry.bsOqlPct?.let { map["bsOqlPct"] = it }
      entry.nflAqcPct?.let { map["nflAqcPct"] = it }
      withTimeout(10000L) {
        db.collection("weekly_quality").document(entry.weekKey).set(map, SetOptions.merge()).await()
      }
      hasPermissionError = false
      lastErrorMessage = null
      true
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        hasPermissionError = true
      }
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore weekly quality push failed: ${e.message}")
      false
    }
  }

  suspend fun pushUserAccountAwait(user: UserAccount): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = user.id.trim().replace("/", "_")
      val effectiveSalt = user.passwordSalt
      val effectiveHash = when {
        user.passwordHash.isNotBlank() -> user.passwordHash
        user.password.isNotBlank() -> GoogleSheetsStorageService.hashPassword(user.password, effectiveSalt)
        else -> ""
      }
      val primaryDevice = user.approvedDevices.firstOrNull()
      val map = hashMapOf(
        "id" to user.id,
        "employeeId" to user.employeeId.ifBlank { user.id },
        "name" to user.name,
        // Never store plaintext password in Firestore
        "password" to "",
        "passwordHash" to effectiveHash,
        "passwordSalt" to effectiveSalt,
        "designation" to user.designation,
        "department" to user.department,
        "requestedDesignation" to user.requestedDesignation,
        "requestedFloor" to user.requestedFloor.name,
        "role" to user.role.name,
        "floor" to user.floor.name,
        "assignedFloor" to user.authorizedFloorScope.displayName,
        "authorizedFloorScope" to user.authorizedFloorScope.name,
        "assignedLine" to user.assignedLine,
        "isActive" to user.isActive,
        "accountStatus" to user.accountStatus.name,
        "isAdmin" to user.isAdmin,
        "mustChangePassword" to user.mustChangePassword,
        "activeSessionId" to user.activeSessionId,
        "authorizedDeviceId" to user.authorizedDeviceId.ifBlank { primaryDevice?.deviceId.orEmpty() },
        "authorizedDeviceName" to user.authorizedDeviceName.ifBlank { primaryDevice?.deviceName.orEmpty() },
        "rejectionReason" to user.rejectionReason,
        "createdAtMillis" to user.createdAtMillis,
        "updatedAtMillis" to System.currentTimeMillis(),
        "approvedBy" to user.approvedBy,
        "approvedAtMillis" to user.approvedAtMillis,
        "firebaseUid" to user.firebaseUid,
        "googleEmail" to user.googleEmail,
        "fcmToken" to user.fcmToken,
        "fcmDeviceId" to user.fcmDeviceId,
        "fcmUpdatedAtMillis" to user.fcmUpdatedAtMillis,
        "canViewDashboard" to user.canViewDashboard,
        "canViewTvDashboard" to user.canViewTvDashboard,
        "canViewMonthlyEfficiency" to user.canViewMonthlyEfficiency,
        "canViewMonthlyDhu" to user.canViewMonthlyDhu,
        "canViewMonthlyOql" to user.canViewMonthlyOql,
        "canViewLineDeepDive" to user.canViewLineDeepDive,
        "canViewAuditLogs" to user.canViewAuditLogs,
        "canExportReports" to user.canExportReports,
        "canProductionWrite" to user.canProductionWrite,
        "canFinishingWrite" to user.canFinishingWrite,
        "canQualityWrite" to user.canQualityWrite,
        "canOqlWrite" to user.canOqlWrite,
        "canNptWrite" to user.canNptWrite,
        "canMorningTargetWrite" to user.canMorningTargetWrite,
        "canMonthlyTargetWrite" to user.canMonthlyTargetWrite,
        "canTargetRevisionWrite" to user.canTargetRevisionWrite,
        "isDeleted" to false
      )
      withTimeout(10000L) {
        db.collection("users").document(docId).set(map, SetOptions.merge()).await()
      }
      hasPermissionError = false
      lastErrorMessage = null
      true
    } catch (e: Exception) {
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Firestore user push failed: ${e.message}")
      false
    }
  }

  fun pushUserAccount(user: UserAccount, onResult: ((Boolean, String?) -> Unit)? = null) {
    serviceScope.launch {
      val ok = pushUserAccountAwait(user)
      onResult?.invoke(ok, lastErrorMessage)
    }
  }

  suspend fun bindFirebaseUidAwait(
    firebaseUid: String,
    employeeId: String,
    deviceId: String
  ): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    if (firebaseUid.isBlank() || employeeId.isBlank()) return@withContext false
    try {
      val bindingMap = hashMapOf(
        "employeeId" to employeeId.trim(),
        "firebaseUid" to firebaseUid.trim(),
        "deviceId" to deviceId,
        "boundAtMillis" to System.currentTimeMillis()
      )
      withTimeout(8000L) {
        db.collection("auth_bindings").document(firebaseUid.trim())
          .set(bindingMap, SetOptions.merge())
          .await()
      }
      true
    } catch (e: Exception) {
      Log.w("FirebaseSync", "bindFirebaseUidAwait notice: ${e.message}")
      false
    }
  }

  suspend fun fetchSingleUserByEmployeeIdAwait(employeeId: String): UserAccount? = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext null
    if (employeeId.isBlank()) return@withContext null
    try {
      val docId = employeeId.trim().replace("/", "_")
      val doc = withTimeout(8000L) {
        db.collection("users").document(docId).get().await()
      }
      if (doc.exists()) parseUserDoc(doc) else null
    } catch (e: Exception) {
      null
    }
  }

  suspend fun updateUserFcmTokenAwait(
    employeeId: String,
    firebaseUid: String,
    fcmToken: String,
    deviceId: String
  ): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    if (employeeId.isBlank() || fcmToken.isBlank()) return@withContext false
    try {
      val docId = employeeId.trim().replace("/", "_")
      val updateMap = mutableMapOf<String, Any>(
        "fcmToken" to fcmToken,
        "fcmDeviceId" to deviceId,
        "fcmUpdatedAtMillis" to System.currentTimeMillis()
      )
      if (firebaseUid.isNotBlank()) {
        updateMap["firebaseUid"] = firebaseUid
      }
      withTimeout(8000L) {
        db.collection("users").document(docId).set(updateMap, SetOptions.merge()).await()
      }
      true
    } catch (e: Exception) {
      Log.w("FirebaseSync", "updateUserFcmTokenAwait notice: ${e.message}")
      false
    }
  }

  suspend fun pushRegistrationRequestAwait(
    user: UserAccount,
    requestedByUid: String
  ): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val requestId = "REG_${user.id.trim()}"
      val reqMap = hashMapOf(
        "requestId" to requestId,
        "employeeId" to user.id.trim(),
        "name" to user.name,
        "requestedRole" to user.role.name,
        "assignedFloor" to user.requestedFloor.displayName,
        "department" to user.department,
        "status" to "PENDING",
        "requestedByUid" to requestedByUid,
        "createdAtMillis" to System.currentTimeMillis(),
        "updatedAtMillis" to System.currentTimeMillis()
      )
      val notifId = "NOTIF_$requestId"
      val notifMap = hashMapOf(
        "notificationId" to notifId,
        "requestId" to requestId,
        "requestType" to "EMPLOYEE_REGISTRATION",
        "targetUserId" to user.id.trim(),
        "title" to "👤 New Employee Registration",
        "summary" to "${user.name} (ID: ${user.id}) • ${user.requestedDesignation} • ${user.requestedFloor.displayName}",
        "requestedRoleOrDevice" to user.requestedDesignation,
        "requestedFloor" to user.requestedFloor.displayName,
        "status" to "PENDING",
        "createdByUid" to requestedByUid,
        "createdAtMillis" to System.currentTimeMillis()
      )
      withTimeout(10000L) {
        db.collection("registration_requests").document(requestId).set(reqMap, SetOptions.merge()).await()
        if (requestedByUid.isNotBlank()) {
          db.collection("admin_notifications").document(notifId).set(notifMap, SetOptions.merge()).await()
        }
      }
      true
    } catch (e: Exception) {
      Log.w("FirebaseSync", "pushRegistrationRequestAwait notice: ${e.message}")
      false
    }
  }

  suspend fun pushDeviceApprovalRequestAwait(
    request: DeviceApprovalRequest,
    requestedByUid: String
  ): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val requestId = request.requestId.ifBlank { "DEV_${request.userId.ifBlank { request.deviceId }}" }
      val reqMap = hashMapOf(
        "requestId" to requestId,
        "userId" to request.userId,
        "userName" to request.userName,
        "employeeId" to request.employeeId.ifBlank { request.userId },
        "employeeName" to request.employeeName.ifBlank { request.userName },
        "deviceId" to request.deviceId,
        "deviceName" to request.deviceName,
        "requestedFloor" to request.requestedFloor,
        "requestType" to request.requestType,
        "status" to request.status,
        "requestedByUid" to requestedByUid,
        "createdAtMillis" to request.requestedAtMillis,
        "updatedAtMillis" to System.currentTimeMillis()
      )
      val notifId = "NOTIF_$requestId"
      val notifMap = hashMapOf(
        "notificationId" to notifId,
        "requestId" to requestId,
        "requestType" to "TV_DEVICE_APPROVAL",
        "targetUserId" to request.userId.ifBlank { request.deviceId },
        "title" to "📺 Smart TV / Device Approval Request",
        "summary" to "${request.userName} • ${request.deviceName} • ${request.requestedFloor}",
        "requestedRoleOrDevice" to request.deviceName,
        "requestedFloor" to request.requestedFloor,
        "status" to "PENDING",
        "createdByUid" to requestedByUid,
        "createdAtMillis" to System.currentTimeMillis()
      )
      withTimeout(10000L) {
        db.collection("device_approval_requests").document(requestId).set(reqMap, SetOptions.merge()).await()
        if (requestedByUid.isNotBlank()) {
          db.collection("admin_notifications").document(notifId).set(notifMap, SetOptions.merge()).await()
        }
      }
      true
    } catch (e: Exception) {
      Log.w("FirebaseSync", "pushDeviceApprovalRequestAwait notice: ${e.message}")
      false
    }
  }

  suspend fun markAdminRequestResolvedInFirestoreAwait(
    requestKey: String,
    requestType: String,
    targetUserId: String,
    newStatus: String,
    processedByAdminId: String
  ): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val now = System.currentTimeMillis()
      withTimeout(8000L) {
        if (requestType == "TV_DEVICE_APPROVAL" || requestKey.startsWith("DEV_") || requestKey.startsWith("TV_")) {
          val docId = requestKey.ifBlank { "DEV_$targetUserId" }
          runCatching {
            db.collection("device_approval_requests").document(docId).set(
              mapOf(
                "status" to newStatus,
                "processedByAdminId" to processedByAdminId,
                "updatedAtMillis" to now
              ),
              SetOptions.merge()
            ).await()
          }
        } else {
          val docId = requestKey.ifBlank { "REG_$targetUserId" }
          runCatching {
            db.collection("registration_requests").document(docId).set(
              mapOf(
                "status" to newStatus,
                "processedByAdminId" to processedByAdminId,
                "updatedAtMillis" to now
              ),
              SetOptions.merge()
            ).await()
          }
        }
        val notifId = "NOTIF_$requestKey"
        runCatching {
          db.collection("admin_notifications").document(notifId).set(
            mapOf("status" to "HANDLED", "updatedAtMillis" to now),
            SetOptions.merge()
          ).await()
        }
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun deleteUserAccountAwait(userId: String): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = userId.trim().replace("/", "_")
      withTimeout(10000L) {
        db.collection("users").document(docId).delete().await()
      }
      true
    } catch (e: Exception) {
      lastErrorMessage = e.message
      false
    }
  }

  suspend fun deleteAllOperationalRecordsAwait(): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext Pair(0, false)
    val operationalCollections = listOf(
      "production_entries",
      "quality_entries",
      "factory_alerts",
      "daily_setups"
    )
    var totalDeleted = 0
    var allSucceeded = true
    lastPushedProductionSignatures.clear()
    lastPushedQualitySignatures.clear()
    knownLegacyProductionDocIds.clear()
    knownLegacyQualityDocIds.clear()

    for (collectionName in operationalCollections) {
      try {
        val snap = withTimeout(15000L) {
          db.collection(collectionName).get().await()
        }
        val docs = snap.documents
        if (docs.isNotEmpty()) {
          docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc ->
              batch.delete(doc.reference)
            }
            withTimeout(15000L) {
              batch.commit().await()
            }
            totalDeleted += chunk.size
          }
        }
      } catch (e: Exception) {
        allSucceeded = false
        lastErrorMessage = e.message
        Log.w("FirebaseSync", "Failed purging collection $collectionName: ${e.message}")
      }
    }
    Pair(totalDeleted, allSucceeded)
  }

  fun deleteAllOperationalRecords(onComplete: ((Int, Boolean) -> Unit)? = null) {
    serviceScope.launch {
      val (deletedCount, ok) = deleteAllOperationalRecordsAwait()
      onComplete?.invoke(deletedCount, ok)
    }
  }

  fun cleanupListeners() {
    listeners.forEach {
      try {
        it.remove()
      } catch (_: Exception) {
      }
    }
    listeners.clear()
  }
}
