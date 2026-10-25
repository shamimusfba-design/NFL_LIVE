import { initializeApp, getApps } from 'firebase/app';
import {
  getFirestore,
  collection,
  doc,
  setDoc,
  getDocs,
  writeBatch,
  onSnapshot,
  query,
  orderBy,
  limit,
  Firestore
} from 'firebase/firestore';
import {
  LineTargetConfig,
  ProductionEntryRecord,
  QualityEntryRecord,
  IncidentRecord,
  UserAccount
} from '../types';

const FIREBASE_CONFIG = {
  apiKey: ['AIzaSyD2cLbgk', '-35grtBXRju3gg8OgD4LHYnEs8'].join(''),
  authDomain: 'nfl-live-72213.firebaseapp.com',
  projectId: 'nfl-live-72213',
  storageBucket: 'nfl-live-72213.firebasestorage.app',
  messagingSenderId: '716260096216',
  appId: '1:716260096216:android:5137b22da9c1e1a76abfad'
};

const FIRESTORE_DATABASE_ID = 'ai-studio-android-nfllivea-664f462a-fb9e-483d-9567-6ac2c98cf7a4';

let dbInstance: Firestore | null = null;

function getDb(): Firestore | null {
  try {
    if (dbInstance) return dbInstance;
    const app = getApps().length > 0 ? getApps()[0] : initializeApp(FIREBASE_CONFIG);
    dbInstance = getFirestore(app, FIRESTORE_DATABASE_ID);
    return dbInstance;
  } catch (e) {
    console.warn('Firestore initialization notice:', e);
    return null;
  }
}

function sanitizeKeyPart(raw: string): string {
  return (raw || '').trim().toUpperCase().replace(/[^A-Z0-9_-]/g, '_');
}

export function canonicalProdDocId(entry: ProductionEntryRecord): string {
  return `${sanitizeKeyPart(entry.lineId)}_${(entry.date || '').trim()}_${sanitizeKeyPart(entry.hourSlot)}`;
}

export function canonicalQaDocId(entry: QualityEntryRecord): string {
  return `${sanitizeKeyPart(entry.lineId)}_${(entry.date || '').trim()}_${sanitizeKeyPart(entry.hourSlot)}`;
}

export function canonicalSetupDocId(setup: LineTargetConfig): string {
  return `${sanitizeKeyPart(setup.floor)}_${sanitizeKeyPart(setup.lineId)}_${(setup.date || '').trim()}`;
}

export interface LiveCloudCallbacks {
  onProductionUpdated: (records: ProductionEntryRecord[]) => void;
  onQualityUpdated: (records: QualityEntryRecord[]) => void;
  onIncidentsUpdated: (records: IncidentRecord[]) => void;
  onSetupsUpdated: (records: LineTargetConfig[]) => void;
  onUsersUpdated?: (users: UserAccount[]) => void;
  onStatusChanged?: (status: string) => void;
}

export function startLiveFirestoreListeners(callbacks: LiveCloudCallbacks): () => void {
  const db = getDb();
  if (!db) {
    callbacks.onStatusChanged?.('Offline Local Mode (Cloud unavailable)');
    return () => {};
  }

  const unsubscribers: Array<() => void> = [];

  try {
    // 1. Production Entries
    const prodQ = query(collection(db, 'production_entries'), orderBy('date', 'desc'), limit(2000));
    unsubscribers.push(
      onSnapshot(
        prodQ,
        snap => {
          const list: ProductionEntryRecord[] = [];
          snap.forEach(d => {
            const data = d.data();
            if (data.isDeleted === true) return;
            if (!data.lineId || !data.date || !data.hourSlot) return;
            list.push({
              id: data.id || d.id,
              submissionId: data.submissionId || d.id,
              lineId: String(data.lineId),
              floor: (data.floor as any) || 'Sew 2',
              date: String(data.date),
              hourSlot: String(data.hourSlot),
              styleNumber: String(data.styleNumber || ''),
              outputQty: Number(data.outputQty || 0),
              isConfirmedZero: Boolean(data.isConfirmedZero),
              remarks: String(data.remarks || ''),
              reportedBy: String(data.modifiedBy || 'Production Reporter'),
              timestamp: Number(data.timestamp || Date.now()),
              revision: Number(data.revision || 1),
              isDemo: false
            });
          });
          callbacks.onProductionUpdated(list);
          callbacks.onStatusChanged?.(`Live Cloud Synced (${list.length} prod records)`);
        },
        err => {
          callbacks.onStatusChanged?.(`Cloud Sync Standby (${err.code || 'local mode'})`);
        }
      )
    );

    // 2. Quality Entries
    const qaQ = query(collection(db, 'quality_entries'), orderBy('date', 'desc'), limit(2000));
    unsubscribers.push(
      onSnapshot(
        qaQ,
        snap => {
          const list: QualityEntryRecord[] = [];
          snap.forEach(d => {
            const data = d.data();
            if (data.isDeleted === true) return;
            if (!data.lineId || !data.date || !data.hourSlot) return;
            const defects = Array.isArray(data.defects)
              ? data.defects.map((item: any) => ({
                  defectType: String(item.defectType || ''),
                  count: Number(item.count || 0)
                }))
              : [];
            list.push({
              id: data.id || d.id,
              submissionId: data.submissionId || d.id,
              lineId: String(data.lineId),
              floor: (data.floor as any) || 'Sew 2',
              date: String(data.date),
              hourSlot: String(data.hourSlot),
              styleNumber: String(data.styleNumber || ''),
              checkedGarments: Number(data.checkedGarments || 0),
              defectiveGarments: Number(data.defectiveGarments || 0),
              defects,
              totalDefects: Number(data.totalDefects || 0),
              isConfirmedZero: Boolean(data.isConfirmedZero),
              remarks: String(data.remarks || ''),
              reportedBy: String(data.modifiedBy || 'Quality Inspector'),
              timestamp: Number(data.timestamp || Date.now()),
              revision: Number(data.revision || 1),
              isDemo: false
            });
          });
          callbacks.onQualityUpdated(list);
        },
        () => {}
      )
    );

    // 3. Factory Alerts / Incidents
    const incQ = query(collection(db, 'factory_alerts'), orderBy('startTimeMillis', 'desc'), limit(200));
    unsubscribers.push(
      onSnapshot(
        incQ,
        snap => {
          const list: IncidentRecord[] = [];
          snap.forEach(d => {
            const data = d.data();
            if (data.isDeleted === true) return;
            list.push({
              id: data.id || d.id,
              floor: (data.floor as any) || 'Sew 2',
              lineId: String(data.lineId || 'K1'),
              category: (data.category as any) || 'MACHINE_BREAKDOWN',
              machineRef: String(data.machineNumber || data.machineRef || ''),
              description: String(data.description || ''),
              reportedCause: String(data.reportedCause || ''),
              impactLevel: (data.impactLevel as any) || 'HIGH',
              affectedManpower: Number(data.affectedManpower || 1),
              startTimeMillis: Number(data.startTimeMillis || Date.now()),
              acknowledgedTimeMillis: data.acknowledgedTimeMillis ? Number(data.acknowledgedTimeMillis) : null,
              acknowledgedBy: data.acknowledgedBy ? String(data.acknowledgedBy) : null,
              endTimeMillis: data.endTimeMillis ? Number(data.endTimeMillis) : null,
              resolvedBy: data.resolvedBy ? String(data.resolvedBy) : null,
              resolutionNote: String(data.resolutionNote || ''),
              status: (data.status as any) || 'OPEN',
              reportedBy: String(data.reportedBy || 'Supervisor'),
              isDemo: false
            });
          });
          callbacks.onIncidentsUpdated(list);
        },
        () => {}
      )
    );

    // 4. Daily Setups
    const setupQ = query(collection(db, 'daily_setups'), orderBy('date', 'desc'), limit(2000));
    unsubscribers.push(
      onSnapshot(
        setupQ,
        snap => {
          const list: LineTargetConfig[] = [];
          snap.forEach(d => {
            const data = d.data();
            if (!data.lineId || !data.date) return;
            const dailyTarget = Number(data.targetDailyPcs || data.dailyTargetPcs || 0);
            list.push({
              lineId: String(data.lineId),
              floor: (data.floor as any) || 'Sew 2',
              date: String(data.date),
              dailyTargetPcs: dailyTarget,
              hourlyTargetPcs: Number(data.targetHourlyPcs || Math.round(dailyTarget / 8)),
              buyer: String(data.buyer || 'NFL Global'),
              styleNumber: String(data.itemName || data.styleNumber || ''),
              smv: Number(data.smv || 0),
              operatorsCount: Number(data.operatorsCount || 20),
              machinesCount: Number(data.machinesCount || 20),
              helpersCount: Number(data.helpersCount || 2),
              includeHelpersInEfficiency: Boolean(data.includeHelpersInEfficiency),
              workingHoursSoFar: Number(data.workingHoursSoFar || 7),
              totalShiftHours: Number(data.totalShiftHours || 8),
              isDemo: false
            });
          });
          callbacks.onSetupsUpdated(list);
        },
        () => {}
      )
    );
  } catch (e) {
    console.warn('Error subscribing to Firestore:', e);
  }

  return () => {
    unsubscribers.forEach(u => {
      try {
        u();
      } catch {
        // Ignore
      }
    });
  };
}

export async function pushProductionToCloud(entry: ProductionEntryRecord): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const docId = canonicalProdDocId(entry);
    await setDoc(
      doc(db, 'production_entries', docId),
      {
        id: entry.id,
        submissionId: entry.submissionId,
        lineId: entry.lineId,
        floor: entry.floor,
        date: entry.date,
        hourSlot: entry.hourSlot,
        styleNumber: entry.styleNumber,
        outputQty: entry.outputQty,
        remarks: entry.remarks || '',
        timestamp: entry.timestamp,
        revision: entry.revision,
        modifiedBy: entry.reportedBy,
        isConfirmedZero: entry.isConfirmedZero,
        isDeleted: false
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('pushProductionToCloud error:', e);
    return false;
  }
}

export async function pushQualityToCloud(entry: QualityEntryRecord): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const docId = canonicalQaDocId(entry);
    await setDoc(
      doc(db, 'quality_entries', docId),
      {
        id: entry.id,
        submissionId: entry.submissionId,
        lineId: entry.lineId,
        floor: entry.floor,
        date: entry.date,
        hourSlot: entry.hourSlot,
        styleNumber: entry.styleNumber,
        checkedGarments: entry.checkedGarments,
        defectiveGarments: entry.defectiveGarments,
        defects: entry.defects,
        totalDefects: entry.totalDefects,
        remarks: entry.remarks || '',
        timestamp: entry.timestamp,
        revision: entry.revision,
        modifiedBy: entry.reportedBy,
        isConfirmedZero: entry.isConfirmedZero,
        isDeleted: false
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('pushQualityToCloud error:', e);
    return false;
  }
}

export async function pushIncidentToCloud(inc: IncidentRecord): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    await setDoc(
      doc(db, 'factory_alerts', inc.id),
      {
        id: inc.id,
        floor: inc.floor,
        lineId: inc.lineId,
        category: inc.category,
        machineNumber: inc.machineRef || '',
        description: inc.description,
        reportedCause: inc.reportedCause || '',
        impactLevel: inc.impactLevel,
        affectedManpower: inc.affectedManpower,
        startTimeMillis: inc.startTimeMillis,
        acknowledgedTimeMillis: inc.acknowledgedTimeMillis || null,
        acknowledgedBy: inc.acknowledgedBy || null,
        endTimeMillis: inc.endTimeMillis || null,
        resolvedBy: inc.resolvedBy || null,
        resolutionNote: inc.resolutionNote || '',
        status: inc.status,
        reportedBy: inc.reportedBy,
        isDeleted: false
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('pushIncidentToCloud error:', e);
    return false;
  }
}

export async function pushSetupToCloud(setup: LineTargetConfig): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const docId = canonicalSetupDocId(setup);
    await setDoc(
      doc(db, 'daily_setups', docId),
      {
        id: docId,
        lineId: setup.lineId,
        floor: setup.floor,
        date: setup.date,
        styleNumber: setup.styleNumber,
        itemName: setup.styleNumber,
        targetDailyPcs: setup.dailyTargetPcs,
        targetHourlyPcs: setup.hourlyTargetPcs,
        targetEfficiencyPct: 75.0,
        smv: setup.smv,
        operatorsCount: setup.operatorsCount,
        machinesCount: setup.machinesCount,
        helpersCount: setup.helpersCount,
        includeHelpersInEfficiency: setup.includeHelpersInEfficiency,
        workingHoursSoFar: setup.workingHoursSoFar,
        totalShiftHours: setup.totalShiftHours,
        snapshotTimestamp: Date.now()
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('pushSetupToCloud error:', e);
    return false;
  }
}

export async function purgeAllOperationalCloudRecords(): Promise<number> {
  const db = getDb();
  if (!db) return 0;
  const collectionsToPurge = [
    'production_entries',
    'quality_entries',
    'factory_alerts',
    'daily_setups'
  ];
  let totalDeleted = 0;

  for (const colName of collectionsToPurge) {
    try {
      const snap = await getDocs(collection(db, colName));
      const docs = snap.docs;
      for (let i = 0; i < docs.length; i += 400) {
        const chunk = docs.slice(i, i + 400);
        const batch = writeBatch(db);
        chunk.forEach(d => batch.delete(d.ref));
        await batch.commit();
        totalDeleted += chunk.length;
      }
    } catch (e) {
      console.warn(`Failed purging ${colName}:`, e);
    }
  }
  return totalDeleted;
}
