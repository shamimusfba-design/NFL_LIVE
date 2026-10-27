import { initializeApp, getApps } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithCustomToken,
  signInWithEmailAndPassword,
  onAuthStateChanged,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signOut as firebaseSignOut,
  Auth,
  User as FirebaseUser
} from 'firebase/auth';
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
  where,
  getDoc,
  Firestore
} from 'firebase/firestore';
import {
  LineTargetConfig,
  ProductionEntryRecord,
  QualityEntryRecord,
  IncidentRecord,
  UserAccount,
  WeeklyQualityRecord,
  MonthlyTargetConfig
} from '../types';
import { normalizeHourSlot24h } from '../data/masterData';

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
let authInstance: Auth | null = null;

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

export function getFirebaseAuth(): Auth | null {
  try {
    if (authInstance) return authInstance;
    const app = getApps().length > 0 ? getApps()[0] : initializeApp(FIREBASE_CONFIG);
    authInstance = getAuth(app);
    return authInstance;
  } catch (e) {
    console.warn('Firebase Auth initialization notice:', e);
    return null;
  }
}

export async function signInWebWithGoogle(employeeIdToBind?: string): Promise<FirebaseUser | null> {
  const auth = getFirebaseAuth();
  if (!auth) return null;
  const provider = new GoogleAuthProvider();
  const cred = await signInWithPopup(auth, provider);
  if (cred.user && employeeIdToBind) {
    await bindFirebaseUidToEmployee(cred.user.uid, employeeIdToBind, cred.user.email || '');
  }
  return cred.user;
}

export async function signInWebWithCustomToken(customToken: string, employeeIdToBind?: string): Promise<FirebaseUser | null> {
  const auth = getFirebaseAuth();
  if (!auth || !customToken) return null;
  const cred = await signInWithCustomToken(auth, customToken);
  if (cred.user && employeeIdToBind) {
    await bindFirebaseUidToEmployee(cred.user.uid, employeeIdToBind, cred.user.email || '');
  }
  return cred.user;
}

export async function bindFirebaseUidToEmployee(uid: string, employeeId: string, email = ''): Promise<boolean> {
  const db = getDb();
  if (!db || !uid || !employeeId) return false;
  try {
    const cleanId = employeeId.trim().replace(/\//g, '_');
    const now = Date.now();
    await setDoc(
      doc(db, 'auth_bindings', uid),
      {
        uid,
        firebaseUid: uid,
        employeeId: cleanId,
        email,
        boundAtMillis: now,
        updatedAtMillis: now
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('bindFirebaseUidToEmployee error:', e);
    return false;
  }
}

export async function signOutWebCloudSession(): Promise<void> {
  const auth = getFirebaseAuth();
  if (!auth) return;
  try {
    await firebaseSignOut(auth);
  } catch (e) {
    console.warn('signOutWebCloudSession notice:', e);
  }
}

/** The hidden Firebase e-mail of an employee. Same rule as the Android app. It is never shown and never receives mail. */
export function employeeEmail(employeeId: string): string {
  return `${employeeId.trim().toLowerCase()}@staff.nfl-live.app`;
}

export type EmployeeAuthOutcome =
  | { ok: true; user: FirebaseUser }
  | { ok: false; message: string; alreadyExists?: boolean };

function describeAuthError(err: any, whenCreating: boolean): EmployeeAuthOutcome {
  const code = String(err?.code || '');
  if (code === 'auth/network-request-failed') return { ok: false, message: 'No internet connection. This needs internet.' };
  if (code === 'auth/too-many-requests') return { ok: false, message: 'Too many attempts. Wait a few minutes and try again.' };
  if (whenCreating && code === 'auth/email-already-in-use') {
    return { ok: false, alreadyExists: true, message: 'This Employee ID already has an account. Sign in instead. If you forgot the password, ask the Administrator.' };
  }
  if (whenCreating && code === 'auth/weak-password') return { ok: false, message: 'The password is too weak. Use at least 6 characters.' };
  if (whenCreating) return { ok: false, message: 'Could not create the account. Check the internet and try again.' };
  const wrongCredential = ['auth/invalid-credential', 'auth/wrong-password', 'auth/user-not-found', 'auth/invalid-login-credentials', 'auth/invalid-email'];
  if (wrongCredential.includes(code)) return { ok: false, message: 'Employee ID or password is not correct.' };
  if (code === 'auth/user-disabled') return { ok: false, message: 'This account has been disabled. Please contact the Administrator.' };
  // Anything else is NOT a wrong password: show the real reason so it can be fixed.
  return { ok: false, message: `Sign-in could not be completed (${code || 'unknown error'}).` };
}

/** Employee ID + password, checked by Firebase. An e-mail address (owner) is used as typed. */
export async function signInWebEmployee(idOrEmail: string, password: string): Promise<EmployeeAuthOutcome> {
  const auth = getFirebaseAuth();
  if (!auth) return { ok: false, message: 'Cloud sign-in is not available. Check the internet connection.' };
  const input = idOrEmail.trim();
  const email = input.includes('@') ? input : employeeEmail(input);
  try {
    const cred = await signInWithEmailAndPassword(auth, email, password.trim());
    return { ok: true, user: cred.user };
  } catch (err: any) {
    return describeAuthError(err, false);
  }
}

/**
 * Sends a password-reset link to a real e-mail address (the project owner / Administrator).
 * Employee accounts have no mailbox: for them the Administrator resets the account.
 */
export async function sendOwnerPasswordReset(email: string): Promise<{ ok: boolean; message: string }> {
  const auth = getFirebaseAuth();
  if (!auth) return { ok: false, message: 'Cloud is not available. Check the internet connection.' };
  const clean = email.trim();
  if (!clean.includes('@')) return { ok: false, message: 'Type your e-mail address in the Employee ID box first.' };
  try {
    await sendPasswordResetEmail(auth, clean);
    return { ok: true, message: 'If this e-mail has an account, a password reset link was sent. Check your inbox and spam folder.' };
  } catch (err: any) {
    const code = String(err?.code || '');
    if (code === 'auth/invalid-email') return { ok: false, message: 'That e-mail address is not valid.' };
    if (code === 'auth/network-request-failed') return { ok: false, message: 'No internet connection. Try again.' };
    return { ok: false, message: 'Could not send the reset link. Try again later.' };
  }
}

/** Creates the Firebase account of a new employee. It has no access until the Administrator approves it. */
export async function createWebEmployeeAccount(employeeId: string, password: string): Promise<EmployeeAuthOutcome> {
  const auth = getFirebaseAuth();
  if (!auth) return { ok: false, message: 'Cloud sign-in is not available. Check the internet connection.' };
  try {
    const cred = await createUserWithEmailAndPassword(auth, employeeEmail(employeeId), password.trim());
    return { ok: true, user: cred.user };
  } catch (err: any) {
    return describeAuthError(err, true);
  }
}

/** Resolves with the signed-in Firebase user (the browser remembers the sign-in) or null. Never waits more than a few seconds. */
export function waitForAuthReady(): Promise<FirebaseUser | null> {
  const auth = getFirebaseAuth();
  if (!auth) return Promise.resolve(null);
  return new Promise(resolve => {
    let done = false;
    const finish = (u: FirebaseUser | null) => { if (!done) { done = true; resolve(u); } };
    const unsubscribe = onAuthStateChanged(auth, u => { unsubscribe(); finish(u); }, () => finish(null));
    setTimeout(() => finish(null), 5000);
  });
}

/**
 * Brings back the person who is still signed in from an earlier visit, so nobody has to sign in again and again.
 * The record is checked again: it must be approved, active and belong to this very sign-in.
 */
export async function restoreWebSession(): Promise<Record<string, any> | null> {
  const user = await waitForAuthReady();
  if (!user) return null;
  const db = getDb();
  if (!db) return null;
  try {
    const binding = await getDoc(doc(db, 'auth_bindings', user.uid));
    const employeeId = binding.exists() ? String((binding.data() as any).employeeId || '') : '';
    if (!employeeId) return null;
    const rec = await fetchOwnUserRecord(employeeId);
    if (!rec || rec.firebaseUid !== user.uid) return null;
    if (String(rec.accountStatus || '') !== 'APPROVED' || rec.isActive === false) return null;
    return rec;
  } catch (e) {
    console.warn('restoreWebSession notice:', e);
    return null;
  }
}

export function currentFirebaseUid(): string {
  return getFirebaseAuth()?.currentUser?.uid || '';
}

/** Reads one user record: undefined = could not read, null = no such record. */
export async function fetchOwnUserRecord(employeeId: string): Promise<Record<string, any> | null | undefined> {
  const db = getDb();
  if (!db) return undefined;
  try {
    const snap = await getDoc(doc(db, 'users', employeeId.trim().replace(/\//g, '_')));
    return snap.exists() ? (snap.data() as Record<string, any>) : null;
  } catch (e) {
    console.warn('fetchOwnUserRecord notice:', e);
    return undefined;
  }
}

/** Creates the Administrator record 1015198. The cloud rules allow this only for the project owner's Firebase account. */
export async function bootstrapOwnerAdminRecord(uid: string, email: string): Promise<Record<string, any> | null> {
  const db = getDb();
  if (!db || !uid) return null;
  const now = Date.now();
  const rec: Record<string, any> = {
    id: '1015198', employeeId: '1015198', name: 'Administrator', password: '', passwordHash: '', passwordSalt: '',
    designation: 'Admin', department: 'Administration', requestedDesignation: 'Admin', requestedFloor: 'SEW_1',
    role: 'ADMIN', floor: 'SEW_1', assignedFloor: 'All Floor', authorizedFloorScope: 'ALL', assignedLine: 'All',
    isActive: true, accountStatus: 'APPROVED', isAdmin: true, mustChangePassword: false,
    activeSessionId: '', authorizedDeviceId: '', authorizedDeviceName: '', rejectionReason: '',
    createdAtMillis: now, updatedAtMillis: now, approvedBy: 'owner', approvedAtMillis: now,
    firebaseUid: uid, googleEmail: email, fcmToken: '', fcmDeviceId: '', fcmUpdatedAtMillis: 0,
    canViewDashboard: true, canViewTvDashboard: true, canViewMonthlyEfficiency: true, canViewMonthlyDhu: true,
    canViewMonthlyOql: true, canViewLineDeepDive: true, canViewAuditLogs: true, canExportReports: true,
    canProductionWrite: true, canFinishingWrite: true, canQualityWrite: true, canOqlWrite: true,
    canNptWrite: true, canMorningTargetWrite: true, canMonthlyTargetWrite: true, canTargetRevisionWrite: true,
    isDeleted: false
  };
  try {
    await setDoc(doc(db, 'users', '1015198'), rec);
    return rec;
  } catch (e) {
    console.warn('bootstrapOwnerAdminRecord notice:', e);
    return null;
  }
}

export interface WebRegistrationInput {
  employeeId: string;
  name: string;
  designationName: string;     // e.g. "Production Supervisor"
  department: string;
  role: string;                // role name chosen by the designation policy, e.g. SUPERVISOR
  physicalFloor: 'SEW_1' | 'SEW_2' | 'SEW_3' | 'FINISHING';
  scopeName: string;           // FloorScope name, e.g. SEW_2 or ALL
  scopeDisplay: string;        // e.g. "Sew 2" or "All Floor"
}

/** Writes the pending user record and the approval request. Must be called while signed in as the new account. */
export async function submitWebRegistration(input: WebRegistrationInput): Promise<{ ok: boolean; message: string }> {
  const db = getDb();
  const uid = currentFirebaseUid();
  if (!db || !uid) return { ok: false, message: 'You are not signed in to the cloud. Please try again.' };
  const id = input.employeeId.trim();
  const docId = id.replace(/\//g, '_');
  const now = Date.now();
  try {
    await setDoc(doc(db, 'users', docId), {
      id, employeeId: id, name: input.name.trim(), password: '', passwordHash: '', passwordSalt: '',
      designation: input.designationName, department: input.department, requestedDesignation: input.designationName,
      requestedFloor: input.physicalFloor, role: input.role, floor: input.physicalFloor,
      assignedFloor: input.scopeDisplay, authorizedFloorScope: input.scopeName, assignedLine: 'K1',
      isActive: false, accountStatus: 'PENDING_APPROVAL', isAdmin: false, mustChangePassword: false,
      activeSessionId: '', authorizedDeviceId: '', authorizedDeviceName: '', rejectionReason: '',
      createdAtMillis: now, updatedAtMillis: now, approvedBy: '', approvedAtMillis: 0,
      firebaseUid: uid, googleEmail: '', fcmToken: '', fcmDeviceId: '', fcmUpdatedAtMillis: 0,
      canViewDashboard: true, canViewTvDashboard: true, canViewMonthlyEfficiency: true, canViewMonthlyDhu: true,
      canViewMonthlyOql: true, canViewLineDeepDive: true, canViewAuditLogs: false, canExportReports: false,
      canProductionWrite: false, canFinishingWrite: false, canQualityWrite: false, canOqlWrite: false,
      canNptWrite: false, canMorningTargetWrite: false, canMonthlyTargetWrite: false, canTargetRevisionWrite: false,
      isDeleted: false
    });
    const requestId = `REG_${id}`;
    await setDoc(doc(db, 'registration_requests', requestId), {
      requestId, employeeId: id, name: input.name.trim(), requestedRole: input.role,
      assignedFloor: input.scopeDisplay, department: input.department, status: 'PENDING',
      requestedByUid: uid, createdAtMillis: now, updatedAtMillis: now
    }, { merge: true });
    const notifId = `NOTIF_${requestId}`;
    await setDoc(doc(db, 'admin_notifications', notifId), {
      notificationId: notifId, requestId, requestType: 'EMPLOYEE_REGISTRATION', targetUserId: id,
      title: 'New Employee Registration',
      summary: `${input.name.trim()} (ID: ${id}) • ${input.designationName} • ${input.scopeDisplay}`,
      requestedRoleOrDevice: input.designationName, requestedFloor: input.scopeDisplay,
      status: 'PENDING', createdByUid: uid, createdAtMillis: now
    }, { merge: true });
    return { ok: true, message: `Registration submitted for Employee ID ${id}. Waiting for Admin approval.` };
  } catch (e: any) {
    console.warn('submitWebRegistration notice:', e);
    const code = String(e?.code || '');
    if (code === 'permission-denied') {
      return { ok: false, message: 'This Employee ID is already registered. Ask the Administrator.' };
    }
    return { ok: false, message: 'Could not send the registration. Check the internet and try again.' };
  }
}

function sanitizeKeyPart(raw: string): string {
  return (raw || '').trim().toUpperCase().replace(/[^A-Z0-9_-]/g, '_');
}

function cleanHourSlotKey(raw: string): string {
  const normSlot = normalizeHourSlot24h(raw);
  return (normSlot || '').trim().replace(/\s+/g, '').replace(/:/g, '').replace(/\//g, '_');
}

export function canonicalProdDocId(entry: ProductionEntryRecord): string {
  const cleanLine = (entry.lineId || '').trim().toUpperCase().replace(/\//g, '_');
  const cleanDate = (entry.date || '').trim().replace(/\//g, '_');
  const cleanHour = cleanHourSlotKey(entry.hourSlot);
  return `PROD_${cleanLine}_${cleanDate}_${cleanHour}`;
}

export function canonicalQaDocId(entry: QualityEntryRecord): string {
  const cleanLine = (entry.lineId || '').trim().toUpperCase().replace(/\//g, '_');
  const cleanDate = (entry.date || '').trim().replace(/\//g, '_');
  const cleanHour = cleanHourSlotKey(entry.hourSlot);
  return `QA_${cleanLine}_${cleanDate}_${cleanHour}`;
}

export function canonicalSetupDocId(setup: LineTargetConfig): string {
  const cleanLine = (setup.lineId || '').trim().replace(/\//g, '_');
  const cleanDate = (setup.date || '').trim().replace(/\//g, '_');
  return `${cleanLine}_${cleanDate}`;
}

export interface LiveCloudCallbacks {
  onConnectionChange?: (connected: boolean) => void;
  onProductionUpdated?: (records: ProductionEntryRecord[]) => void;
  onProductionUpdate?: (records: ProductionEntryRecord[]) => void;
  onQualityUpdated?: (records: QualityEntryRecord[]) => void;
  onQualityUpdate?: (records: QualityEntryRecord[]) => void;
  onIncidentsUpdated?: (records: IncidentRecord[]) => void;
  onIncidentUpdate?: (records: IncidentRecord[]) => void;
  onSetupsUpdated?: (records: LineTargetConfig[]) => void;
  onSetupUpdate?: (records: LineTargetConfig[]) => void;
  onWeeklyQualityUpdated?: (records: WeeklyQualityRecord[]) => void;
  onWeeklyQualityUpdate?: (records: WeeklyQualityRecord[]) => void;
  onMonthlyTargetsUpdated?: (records: MonthlyTargetConfig[]) => void;
  onMonthlyTargetsUpdate?: (records: MonthlyTargetConfig[]) => void;
  onUsersUpdated?: (users: UserAccount[]) => void;
  onStatusChanged?: (status: string) => void;
}

/** yyyy-MM-dd for "today minus N days" in Bangladesh time. */
function bangladeshIsoDaysAgo(days: number): string {
  const ms = Date.now() + 6 * 3600 * 1000 - days * 86400 * 1000;
  return new Date(ms).toISOString().slice(0, 10);
}

/** Login data of one employee, as stored by the Android app. Passwords are never stored in plain text. */
export interface CloudLoginUser {
  id: string;
  name: string;
  role: string;
  floor: string;
  passwordHash: string;
  passwordSalt: string;
  accountStatus: string;
  isActive: boolean;
  mustChangePassword: boolean;
}

export async function fetchCloudLoginUser(employeeId: string): Promise<CloudLoginUser | null> {
  const db = getDb();
  if (!db) throw new Error('Cloud is not reachable. Check the internet connection.');
  const clean = employeeId.trim().replace(/\//g, '_');
  if (!clean) return null;
  for (const candidate of Array.from(new Set([clean, clean.toLowerCase()]))) {
    const snap = await getDoc(doc(db, 'users', candidate));
    if (snap.exists()) {
      const d = snap.data();
      return {
        id: String(d.id || snap.id),
        name: String(d.name || snap.id),
        role: String(d.role || ''),
        floor: String(d.authorizedFloorScope || d.floor || 'ALL'),
        passwordHash: String(d.passwordHash || ''),
        passwordSalt: String(d.passwordSalt || ''),
        accountStatus: String(d.accountStatus || ''),
        isActive: d.isActive !== false,
        mustChangePassword: d.mustChangePassword === true
      };
    }
  }
  return null;
}

function sha256FallbackHex(ascii: string): string {
  const rightRotate = (value: number, amount: number) => (value >>> amount) | (value << (32 - amount));
  const mathPow = Math.pow;
  const maxWord = mathPow(2, 32);
  let result = '';
  const words: number[] = [];
  const utf8 = unescape(encodeURIComponent(ascii));
  const asciiBitLength = utf8.length * 8;

  let hash: number[] = [];
  const k: number[] = [];
  let primeCounter = 0;
  const isComposite: Record<number, number> = {};
  for (let candidate = 2; primeCounter < 64; candidate++) {
    if (!isComposite[candidate]) {
      for (let i = 0; i < 313; i += candidate) {
        isComposite[i] = candidate;
      }
      hash[primeCounter] = (mathPow(candidate, 0.5) * maxWord) | 0;
      k[primeCounter++] = (mathPow(candidate, 1 / 3) * maxWord) | 0;
    }
  }

  let str = utf8 + '\x80';
  while ((str.length % 64) - 56) str += '\x00';
  for (let i = 0; i < str.length; i++) {
    const j = str.charCodeAt(i);
    words[i >> 2] |= j << (((3 - i) % 4) * 8);
  }
  words[words.length] = (asciiBitLength / maxWord) | 0;
  words[words.length] = asciiBitLength;

  for (let j = 0; j < words.length; ) {
    const w = words.slice(j, (j += 16));
    const oldHash = hash.slice(0);
    hash = hash.slice(0, 8);

    for (let i = 0; i < 64; i++) {
      const w15 = w[i - 15];
      const w2 = w[i - 2];
      const a = hash[0];
      const e = hash[4];
      const temp1 =
        hash[7] +
        (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25)) +
        ((e & hash[5]) ^ (~e & hash[6])) +
        k[i] +
        (w[i] =
          i < 16
            ? w[i]
            : (w[i - 16] +
                (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3)) +
                w[i - 7] +
                (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))) |
              0);
      const temp2 =
        (rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)) +
        ((a & hash[1]) ^ (a & hash[2]) ^ (hash[1] & hash[2]));

      hash = [(temp1 + temp2) | 0].concat(hash);
      hash[4] = (hash[4] + temp1) | 0;
    }

    for (let i = 0; i < 8; i++) {
      hash[i] = (hash[i] + oldHash[i]) | 0;
    }
  }

  for (let i = 0; i < 8; i++) {
    for (let j = 3; j + 1; j--) {
      const b = (hash[i] >> (j * 8)) & 255;
      result += (b < 16 ? 0 : '') + b.toString(16);
    }
  }
  return result;
}

async function sha256Hex(text: string): Promise<string> {
  const subtle = (globalThis.crypto && globalThis.crypto.subtle) || null;
  if (subtle) {
    try {
      const bytes = new TextEncoder().encode(text);
      const digest = await subtle.digest('SHA-256', bytes);
      return Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('');
    } catch {
      return sha256FallbackHex(text);
    }
  }
  return sha256FallbackHex(text);
}

/** Same rule as the Android app: sha256("salt:password"), with the old unsalted hash still accepted. */
export async function verifyCloudPassword(input: string, storedHash: string, salt: string): Promise<boolean> {
  const clean = input.trim();
  if (!clean || !storedHash) return false;
  if (salt && (await sha256Hex(`${salt}:${clean}`)).toLowerCase() === storedHash.toLowerCase()) return true;
  return (await sha256Hex(clean)).toLowerCase() === storedHash.toLowerCase();
}

export function startLiveFirestoreListeners(callbacks: LiveCloudCallbacks): () => void {
  const db = getDb();
  if (!db) {
    callbacks.onConnectionChange?.(false);
    callbacks.onStatusChanged?.('Offline Local Mode (Cloud unavailable)');
    return () => {};
  }

  const unsubscribers: Array<() => void> = [];

  try {
    // 1. Production Entries
    const prodQ = query(collection(db, 'production_entries'), where('date', '>=', bangladeshIsoDaysAgo(7)), orderBy('date', 'desc'), limit(2000));
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
              hourSlot: normalizeHourSlot24h(String(data.hourSlot)),
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
          callbacks.onConnectionChange?.(true);
          callbacks.onProductionUpdated?.(list);
          callbacks.onProductionUpdate?.(list);
          callbacks.onStatusChanged?.(`Live Cloud Synced (${list.length} prod records)`);
        },
        err => {
          callbacks.onConnectionChange?.(false);
          callbacks.onStatusChanged?.(`Cloud Sync Standby (${err.code || 'local mode'})`);
        }
      )
    );

    // 2. Quality Entries (including 1-Hr DHU and 2-Hr Bi-Hourly OQL Audit)
    const qaQ = query(collection(db, 'quality_entries'), where('date', '>=', bangladeshIsoDaysAgo(7)), orderBy('date', 'desc'), limit(2000));
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
              hourSlot: normalizeHourSlot24h(String(data.hourSlot)),
              styleNumber: String(data.styleNumber || ''),
              checkedGarments: Number(data.checkedGarments || 0),
              defectiveGarments: Number(data.defectiveGarments || 0),
              defects,
              totalDefects: Number(data.totalDefects || 0),
              defectSent: Number(data.defectSent || 0),
              defectReceived: Number(data.defectReceived || 0),
              spotSent: Number(data.spotSent || 0),
              spotReceived: Number(data.spotReceived || 0),
              isConfirmedZero: Boolean(data.isConfirmedZero),
              remarks: String(data.remarks || ''),
              reportedBy: String(data.modifiedBy || 'Quality Inspector'),
              timestamp: Number(data.timestamp || Date.now()),
              revision: Number(data.revision || 1),
              isDemo: false
            });
          });
          callbacks.onQualityUpdated?.(list);
          callbacks.onQualityUpdate?.(list);
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
          callbacks.onIncidentsUpdated?.(list);
          callbacks.onIncidentUpdate?.(list);
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
              targetEfficiencyPct: Number(data.targetEfficiencyPct || 75),
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
          callbacks.onSetupsUpdated?.(list);
          callbacks.onSetupUpdate?.(list);
        },
        () => {}
      )
    );

    // 5. Weekly BS OQL & NFL AQC
    const weeklyQ = query(collection(db, 'weekly_quality'), limit(100));
    unsubscribers.push(
      onSnapshot(
        weeklyQ,
        snap => {
          const list: WeeklyQualityRecord[] = [];
          snap.forEach(d => {
            const data = d.data();
            const weekKey = String(data.weekKey || d.id);
            if (!weekKey) return;
            list.push({
              weekKey,
              weekStartIso: String(data.weekStartIso || ''),
              bsOqlPct: typeof data.bsOqlPct === 'number' ? data.bsOqlPct : null,
              nflAqcPct: typeof data.nflAqcPct === 'number' ? data.nflAqcPct : null,
              lastUpdatedBy: String(data.lastUpdatedBy || 'Auditor'),
              lastUpdatedMillis: Number(data.lastUpdatedMillis || Date.now()),
              isDemo: false
            });
          });
          callbacks.onWeeklyQualityUpdate?.(list);
        },
        () => {}
      )
    );

    // 6. Monthly Targets
    const monthlyQ = query(collection(db, 'monthly_targets'), limit(500));
    unsubscribers.push(
      onSnapshot(
        monthlyQ,
        snap => {
          const list: MonthlyTargetConfig[] = [];
          snap.forEach(d => {
            const data = d.data();
            list.push({
              id: d.id,
              floor: (data.floor as any) || 'Sew 2',
              lineId: String(data.lineId || ''),
              monthYear: String(data.monthYear || ''),
              targetEfficiencyPct: Number(data.targetEfficiencyPct || 75),
              targetDhuPct: Number(data.targetDhuPct || 2.0),
              targetOqlPct: Number(data.targetOqlPct || 4.5),
              bsQaOqlPct: Number(data.bsQaOqlPct || 2.4),
              friPassRatePct: Number(data.friPassRatePct || 98.5)
            });
          });
          callbacks.onMonthlyTargetsUpdate?.(list);
        },
        () => {}
      )
    );

    // 7. Users & Pending Registrations / TV Setup Requests
    const usersQ = query(collection(db, 'users'), limit(500));
    unsubscribers.push(
      onSnapshot(
        usersQ,
        snap => {
          const list: UserAccount[] = [];
          snap.forEach(d => {
            const data = d.data();
            const id = String(data.id || d.id);
            const rawFloor = String(data.authorizedFloorScope || data.floor || 'All');
            const normFloor =
              rawFloor.toUpperCase().includes('SEW_1') || rawFloor === 'Sew 1'
                ? 'Sew 1'
                : rawFloor.toUpperCase().includes('SEW_2') || rawFloor === 'Sew 2'
                ? 'Sew 2'
                : rawFloor.toUpperCase().includes('SEW_3') || rawFloor === 'Sew 3'
                ? 'Sew 3'
                : 'All';
            list.push({
              id,
              name: String(data.name || id),
              username: String(data.employeeId || id),
              role: (data.role as any) || 'SUPERVISOR',
              floor: normFloor as any,
              assignedLine: String(data.assignedLine || ''),
              designation: String(data.designation || ''),
              department: String(data.department || ''),
              accountStatus: String(data.accountStatus || 'APPROVED'),
              isActive: data.isActive !== false,
              authorizedDeviceId: String(data.authorizedDeviceId || ''),
              authorizedDeviceName: String(data.authorizedDeviceName || ''),
              rejectionReason: String(data.rejectionReason || ''),
              updatedAtMillis: Number(data.updatedAtMillis || 0)
            });
          });
          callbacks.onUsersUpdated?.(list);
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
    const normSlot = normalizeHourSlot24h(entry.hourSlot);
    const docId = canonicalProdDocId(entry);
    await setDoc(
      doc(db, 'production_entries', docId),
      {
        id: entry.id,
        submissionId: entry.submissionId,
        lineId: entry.lineId,
        floor: entry.floor,
        date: entry.date,
        hourSlot: normSlot,
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
    const normSlot = normalizeHourSlot24h(entry.hourSlot);
    const docId = canonicalQaDocId(entry);
    await setDoc(
      doc(db, 'quality_entries', docId),
      {
        id: entry.id,
        submissionId: entry.submissionId,
        lineId: entry.lineId,
        floor: entry.floor,
        date: entry.date,
        hourSlot: normSlot,
        styleNumber: entry.styleNumber,
        checkedGarments: entry.checkedGarments,
        defectiveGarments: entry.defectiveGarments,
        defects: entry.defects,
        totalDefects: entry.totalDefects,
        defectSent: entry.defectSent || 0,
        defectReceived: entry.defectReceived || 0,
        spotSent: entry.spotSent || 0,
        spotReceived: entry.spotReceived || 0,
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

export async function pushWeeklyQualityToCloud(record: WeeklyQualityRecord): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const docId = sanitizeKeyPart(record.weekKey);
    await setDoc(
      doc(db, 'weekly_quality', docId),
      {
        weekKey: record.weekKey,
        weekStartIso: record.weekStartIso,
        bsOqlPct: record.bsOqlPct,
        nflAqcPct: record.nflAqcPct,
        lastUpdatedBy: record.lastUpdatedBy,
        lastUpdatedMillis: record.lastUpdatedMillis
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('pushWeeklyQualityToCloud error:', e);
    return false;
  }
}

export async function pushMonthlyTargetToCloud(target: MonthlyTargetConfig): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const cleanFloor = (target.floor || '').trim().replace(/\//g, '_');
    const cleanLine = (target.lineId || '').trim().replace(/\//g, '_');
    const docId =
      cleanLine && cleanLine.toUpperCase() !== 'ALL'
        ? `${cleanFloor}_${cleanLine}`
        : cleanFloor;
    await setDoc(
      doc(db, 'monthly_targets', docId),
      {
        id: docId,
        floor: target.floor,
        lineId: target.lineId,
        monthYear: target.monthYear,
        targetEfficiencyPct: target.targetEfficiencyPct,
        targetDhuPct: target.targetDhuPct,
        targetOqlPct: target.targetOqlPct,
        bsQaOqlPct: target.bsQaOqlPct,
        friPassRatePct: target.friPassRatePct,
        updatedAt: Date.now()
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('pushMonthlyTargetToCloud error:', e);
    return false;
  }
}

export async function softDeleteCloudRecord(
  collectionName: 'production_entries' | 'quality_entries',
  docId: string,
  deletedBy: string,
  deleteReason: string
): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    await setDoc(
      doc(db, collectionName, docId),
      {
        isDeleted: true,
        deletedBy,
        deleteReason,
        timestamp: Date.now()
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('softDeleteCloudRecord error:', e);
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
        targetEfficiencyPct: setup.targetEfficiencyPct ?? 75.0,
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
    'daily_setups',
    'weekly_quality'
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

export async function approveCloudUserRegistration(
  userId: string,
  approvedBy: string
): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const clean = userId.trim().replace(/\//g, '_');
    const now = Date.now();
    await setDoc(
      doc(db, 'users', clean),
      {
        id: clean,
        employeeId: clean,
        password: '',
        accountStatus: 'APPROVED',
        isActive: true,
        rejectionReason: '',
        approvedBy,
        approvedAtMillis: now,
        updatedAtMillis: now
      },
      { merge: true }
    );
    await setDoc(
      doc(db, 'registration_requests', `reg_${clean}`),
      {
        id: `reg_${clean}`,
        employeeId: clean,
        status: 'APPROVED',
        reviewedBy: approvedBy,
        reviewedAtMillis: now
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('approveCloudUserRegistration error:', e);
    return false;
  }
}

export async function rejectCloudUserRegistration(
  userId: string,
  reason: string
): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const clean = userId.trim().replace(/\//g, '_');
    const now = Date.now();
    await setDoc(
      doc(db, 'users', clean),
      {
        id: clean,
        employeeId: clean,
        password: '',
        accountStatus: 'REJECTED',
        isActive: false,
        rejectionReason: reason || 'Rejected by Administrator',
        updatedAtMillis: now
      },
      { merge: true }
    );
    await setDoc(
      doc(db, 'registration_requests', `reg_${clean}`),
      {
        id: `reg_${clean}`,
        employeeId: clean,
        status: 'REJECTED',
        rejectionReason: reason || 'Rejected by Administrator',
        reviewedAtMillis: now
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('rejectCloudUserRegistration error:', e);
    return false;
  }
}

export async function approveCloudDeviceOrTvRequest(
  userId: string,
  deviceId: string,
  deviceName: string,
  approvedBy: string
): Promise<boolean> {
  const db = getDb();
  if (!db) return false;
  try {
    const clean = userId.trim().replace(/\//g, '_');
    const now = Date.now();
    const sessionId = `WEB-APPROVED-${now}`;
    await setDoc(
      doc(db, 'users', clean),
      {
        id: clean,
        employeeId: clean,
        name: clean === 'tv_display' ? 'Factory Floor Smart TV' : clean,
        password: '',
        accountStatus: 'APPROVED',
        isActive: true,
        authorizedDeviceId: deviceId,
        authorizedDeviceName: deviceName,
        activeSessionId: sessionId,
        approvedDevices: [
          {
            deviceId,
            deviceName,
            approvedAtMillis: now,
            approvedBy,
            lastActiveMillis: now
          }
        ],
        rejectionReason: '',
        approvedBy,
        approvedAtMillis: now,
        updatedAtMillis: now
      },
      { merge: true }
    );
    const reqId = `devreq_${clean}_${deviceId.replace(/\//g, '_')}`;
    await setDoc(
      doc(db, 'device_approval_requests', reqId),
      {
        id: reqId,
        employeeId: clean,
        deviceId,
        deviceName,
        status: 'APPROVED',
        reviewedBy: approvedBy,
        reviewedAtMillis: now
      },
      { merge: true }
    );
    return true;
  } catch (e) {
    console.warn('approveCloudDeviceOrTvRequest error:', e);
    return false;
  }
}


