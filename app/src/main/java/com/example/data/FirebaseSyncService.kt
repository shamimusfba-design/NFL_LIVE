package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
  private val serviceScope = CoroutineScope(Dispatchers.IO)

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
  var onRemoteUsersUpdated: ((List<UserAccount>) -> Unit)? = null
  var onSyncStatusChanged: ((String) -> Unit)? = null

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
    val db = firestore ?: return
    cleanupListeners()

    try {
      // 1. Production entries listener
      val prodReg = db.collection("production_entries")
        .limit(500)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Production", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            lastErrorMessage = null
            val removedKeys = mutableSetOf<String>()
            snapshot.documentChanges.forEach { change ->
              if (change.type == DocumentChange.Type.REMOVED) {
                val d = change.document
                removedKeys.add(d.id)
                d.getString("id")?.let { removedKeys.add(it) }
                d.getString("submissionId")?.let { removedKeys.add(it) }
                val l = d.getString("lineId").orEmpty()
                val dt = d.getString("date").orEmpty()
                val hr = d.getString("hourSlot").orEmpty()
                if (l.isNotEmpty() && dt.isNotEmpty() && hr.isNotEmpty()) {
                  removedKeys.add("${l}_${dt}_${hr}")
                }
              }
            }
            if (removedKeys.isNotEmpty()) {
              onRemoteProductionDeleted?.invoke(removedKeys)
            }
            val list = snapshot.documents.mapNotNull { doc -> parseProductionDoc(doc) }
            if (list.isNotEmpty()) {
              onRemoteProductionUpdated?.invoke(list)
              onSyncStatusChanged?.invoke("✓ Cloud Firestore live: ${list.size} production records synced")
            }
          }
        }
      listeners.add(prodReg)

      // 2. Quality entries listener
      val qaReg = db.collection("quality_entries")
        .limit(1000)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Quality", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val removedKeys = mutableSetOf<String>()
            snapshot.documentChanges.forEach { change ->
              if (change.type == DocumentChange.Type.REMOVED) {
                val d = change.document
                removedKeys.add(d.id)
                d.getString("id")?.let { removedKeys.add(it) }
                d.getString("submissionId")?.let { removedKeys.add(it) }
                val l = d.getString("lineId").orEmpty()
                val dt = d.getString("date").orEmpty()
                val hr = d.getString("hourSlot").orEmpty()
                if (l.isNotEmpty() && dt.isNotEmpty() && hr.isNotEmpty()) {
                  removedKeys.add("${l}_${dt}_${hr}")
                }
              }
            }
            if (removedKeys.isNotEmpty()) {
              onRemoteQualityDeleted?.invoke(removedKeys)
            }
            val list = snapshot.documents.mapNotNull { doc -> parseQualityDoc(doc) }
            if (list.isNotEmpty()) {
              onRemoteQualityUpdated?.invoke(list)
            }
          }
        }
      listeners.add(qaReg)

      // 3. Factory alerts / incidents listener
      val alertReg = db.collection("factory_alerts")
        .limit(500)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Alerts", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val list = snapshot.documents.mapNotNull { doc -> parseIncidentDoc(doc) }
            if (list.isNotEmpty()) {
              onRemoteIncidentsUpdated?.invoke(list)
            }
          }
        }
      listeners.add(alertReg)

      // 4. Setups listener
      val setupReg = db.collection("daily_setups")
        .limit(500)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Setups", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val list = snapshot.documents.mapNotNull { doc -> parseSetupDoc(doc) }
            if (list.isNotEmpty()) {
              onRemoteSetupsUpdated?.invoke(list)
            }
          }
        }
      listeners.add(setupReg)

      // 5. Monthly targets listener
      val monthlyReg = db.collection("monthly_targets")
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("MonthlyTargets", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val list = snapshot.documents.mapNotNull { doc -> parseMonthlyTargetDoc(doc) }
            if (list.isNotEmpty()) {
              onRemoteMonthlyTargetsUpdated?.invoke(list)
            }
          }
        }
      listeners.add(monthlyReg)

      // 6. User accounts listener
      val usersReg = db.collection("users")
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            handleListenerError("Users", error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            hasPermissionError = false
            val list = snapshot.documents.mapNotNull { doc -> parseUserDoc(doc) }
            if (list.isNotEmpty()) {
              onRemoteUsersUpdated?.invoke(list)
            }
          }
        }
      listeners.add(usersReg)

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
      ProductionRecord(
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

      QualityRecord(
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

  private fun parseUserDoc(doc: DocumentSnapshot): UserAccount? {
    return try {
      if (doc.getBoolean("isDeleted") == true) return null
      val id = doc.getString("id")?.trim().orEmpty().ifEmpty { doc.id }
      if (id.isEmpty()) return null
      val roleStr = doc.getString("role") ?: UserRole.PRODUCTION_REPORTER.name
      val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.PRODUCTION_REPORTER }
      val floorStr = doc.getString("floor") ?: FloorScope.SEW_2.name
      val floor = FloorScope.entries.find {
        it.name.equals(floorStr, ignoreCase = true) ||
            it.apiValue.equals(floorStr, ignoreCase = true) ||
            it.displayName.equals(floorStr, ignoreCase = true)
      } ?: FloorScope.SEW_2
      val statusStr = doc.getString("accountStatus") ?: AccountStatus.APPROVED.name
      val accStatus = try { AccountStatus.valueOf(statusStr) } catch (_: Exception) { AccountStatus.APPROVED }

      UserAccount(
        id = id,
        employeeId = doc.getString("employeeId") ?: id,
        name = doc.getString("name") ?: id,
        password = doc.getString("password") ?: "",
        passwordHash = doc.getString("passwordHash") ?: "",
        role = role,
        floor = floor,
        assignedLine = doc.getString("assignedLine") ?: "K1",
        isActive = doc.getBoolean("isActive") ?: true,
        accountStatus = accStatus,
        approvedDevices = (doc.get("approvedDevices") as? List<*>)?.mapNotNull { raw ->
          val device = raw as? Map<*, *> ?: return@mapNotNull null
          val deviceId = device["deviceId"] as? String ?: return@mapNotNull null
          ApprovedDevice(
            deviceId = deviceId,
            deviceName = device["deviceName"] as? String ?: "Android device",
            approvedAtMillis = (device["approvedAtMillis"] as? Number)?.toLong() ?: 0L,
            approvedBy = device["approvedBy"] as? String ?: "Admin",
            lastActiveMillis = (device["lastActiveMillis"] as? Number)?.toLong() ?: 0L
          )
        } ?: emptyList(),
        isAdmin = doc.getBoolean("isAdmin") ?: (role == UserRole.ADMIN),
        canViewDashboard = doc.getBoolean("canViewDashboard") ?: true,
        canViewTvDashboard = doc.getBoolean("canViewTvDashboard") ?: false,
        canViewMonthlyEfficiency = doc.getBoolean("canViewMonthlyEfficiency") ?: false,
        canViewMonthlyDhu = doc.getBoolean("canViewMonthlyDhu") ?: false,
        canViewMonthlyOql = doc.getBoolean("canViewMonthlyOql") ?: false,
        canViewLineDeepDive = doc.getBoolean("canViewLineDeepDive") ?: true,
        canViewAuditLogs = doc.getBoolean("canViewAuditLogs") ?: false,
        canExportReports = doc.getBoolean("canExportReports") ?: false,
        canProductionWrite = doc.getBoolean("canProductionWrite") ?: false,
        canFinishingWrite = doc.getBoolean("canFinishingWrite") ?: false,
        canQualityWrite = doc.getBoolean("canQualityWrite") ?: false,
        canOqlWrite = doc.getBoolean("canOqlWrite") ?: false,
        canNptWrite = doc.getBoolean("canNptWrite") ?: false,
        canMorningTargetWrite = doc.getBoolean("canMorningTargetWrite") ?: false,
        canMonthlyTargetWrite = doc.getBoolean("canMonthlyTargetWrite") ?: false,
        canTargetRevisionWrite = doc.getBoolean("canTargetRevisionWrite") ?: false
      )
    } catch (e: Exception) {
      null
    }
  }

  suspend fun fetchAllFromFirestore(): FirestoreFetchResult = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext FirestoreFetchResult(false, "FirebaseFirestore not initialized")
    try {
      withTimeout(12000L) {
        val prodSnap = try {
          db.collection("production_entries").limit(1000).get(Source.SERVER).await()
        } catch (_: Exception) {
          db.collection("production_entries").limit(1000).get().await()
        }
        val qaSnap = try {
          db.collection("quality_entries").limit(1000).get(Source.SERVER).await()
        } catch (_: Exception) {
          db.collection("quality_entries").limit(1000).get().await()
        }
        val incSnap = try {
          db.collection("factory_alerts").limit(500).get(Source.SERVER).await()
        } catch (_: Exception) {
          db.collection("factory_alerts").limit(500).get().await()
        }
        val setupSnap = try {
          db.collection("daily_setups").limit(500).get(Source.SERVER).await()
        } catch (_: Exception) {
          db.collection("daily_setups").limit(500).get().await()
        }
        val monthlySnap = try {
          db.collection("monthly_targets").get(Source.SERVER).await()
        } catch (_: Exception) {
          db.collection("monthly_targets").get().await()
        }
        val usersSnap = try {
          db.collection("users").get(Source.SERVER).await()
        } catch (_: Exception) {
          db.collection("users").get().await()
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
          users = usersSnap.documents.mapNotNull { parseUserDoc(it) }
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
      val map = hashMapOf(
        "id" to record.id,
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
        "revision" to record.revision,
        "isConfirmedZero" to record.isConfirmedZero,
        "isDemo" to false,
        "isDeleted" to false
      )
      withTimeout(10000L) {
        db.collection("production_entries").document(docId).set(map, SetOptions.merge()).await()
        if (record.id != docId && record.id.isNotBlank()) {
          // Clean up any legacy non-canonical duplicate doc if it existed
          runCatching { db.collection("production_entries").document(record.id).delete().await() }
        }
      }
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
        if (record.id.isNotBlank() && record.id != docId) {
          runCatching { db.collection("production_entries").document(record.id).delete().await() }
        }
        if (record.submissionId.isNotBlank() && record.submissionId != docId) {
          runCatching { db.collection("production_entries").document(record.submissionId).delete().await() }
        }
      }
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
      val defectsList = record.defects.map {
        mapOf("defectType" to it.defectType, "count" to it.count)
      }
      val defectBreakdown = record.defects.joinToString(";") { "${it.defectType}:${it.count}" }
      val map = hashMapOf(
        "id" to record.id,
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
        "revision" to record.revision,
        "isConfirmedZero" to record.isConfirmedZero,
        "isDemo" to false,
        "isDeleted" to false
      )
      withTimeout(10000L) {
        db.collection("quality_entries").document(docId).set(map, SetOptions.merge()).await()
        if (record.id != docId && record.id.isNotBlank()) {
          runCatching { db.collection("quality_entries").document(record.id).delete().await() }
        }
      }
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
        if (record.id.isNotBlank() && record.id != docId) {
          runCatching { db.collection("quality_entries").document(record.id).delete().await() }
        }
        if (record.submissionId.isNotBlank() && record.submissionId != docId) {
          runCatching { db.collection("quality_entries").document(record.submissionId).delete().await() }
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

  private fun sha256Hex(value: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  suspend fun pushUserAccountAwait(user: UserAccount): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val docId = user.id.trim().replace("/", "_")
      val map = hashMapOf(
        "id" to user.id,
        "employeeId" to user.employeeId,
        "name" to user.name,
        "password" to FieldValue.delete(),
        "passwordHash" to user.passwordHash.ifBlank { sha256Hex(user.password) },
        "role" to user.role.name,
        "floor" to user.floor.name,
        "assignedLine" to user.assignedLine,
        "isActive" to user.isActive,
        "accountStatus" to user.accountStatus.name,
        "approvedDevices" to user.approvedDevices.map { device ->
          mapOf(
            "deviceId" to device.deviceId,
            "deviceName" to device.deviceName,
            "approvedAtMillis" to device.approvedAtMillis,
            "approvedBy" to device.approvedBy,
            "lastActiveMillis" to device.lastActiveMillis
          )
        },
        "maxDevices" to 1,
        "isAdmin" to user.isAdmin,
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

  /**
   * Mirrors Admin approval / registration requests to Firestore for the desktop Admin Panel.
   * This collection must be protected by server-side Firestore rules before production deployment.
   */
  suspend fun pushRegistrationRequestAwait(request: DeviceApprovalRequest): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      val map = hashMapOf<String, Any?>(
        "requestId" to request.requestId,
        "userId" to request.userId,
        "userName" to request.userName,
        "employeeId" to request.employeeId,
        "employeeName" to request.employeeName,
        "role" to request.role.name,
        "deviceId" to request.deviceId,
        "deviceName" to request.deviceName,
        "requestType" to request.requestType,
        "requestedAtMillis" to request.requestedAtMillis,
        "status" to request.status,
        "reason" to request.reason,
        "reviewedAtMillis" to null
      )
      withTimeout(10000L) {
        db.collection("registration_requests").document(request.requestId)
          .set(map, SetOptions.merge()).await()
      }
      true
    } catch (e: Exception) {
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Registration request push failed: ${e.message}")
      false
    }
  }

  suspend fun deleteRegistrationRequestAwait(requestId: String): Boolean = withContext(Dispatchers.IO) {
    val db = firestore ?: return@withContext false
    try {
      withTimeout(10000L) {
        db.collection("registration_requests").document(requestId).delete().await()
      }
      true
    } catch (e: Exception) {
      lastErrorMessage = e.message
      Log.w("FirebaseSync", "Registration request rollback failed: ${e.message}")
      false
    }
  }

  fun pushUserAccount(user: UserAccount, onResult: ((Boolean, String?) -> Unit)? = null) {
    serviceScope.launch {
      val ok = pushUserAccountAwait(user)
      onResult?.invoke(ok, lastErrorMessage)
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
