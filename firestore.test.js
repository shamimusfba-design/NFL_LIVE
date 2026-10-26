import { test, describe, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs } = require('firebase/firestore');


let testEnv;
const TODAY_ISO = new Date().toISOString().slice(0, 10);

async function seedIdentity(employeeId, uid, role, assignedFloor, accountStatus = 'APPROVED') {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'auth_bindings', uid), {
      employeeId,
      firebaseUid: uid,
      boundAtMillis: Date.now(),
    });
    await setDoc(doc(db, 'users', employeeId), {
      employeeId,
      name: `User ${employeeId}`,
      department: 'Production',
      role,
      assignedFloor,
      accountStatus,
      firebaseUid: uid,
      passwordHash: 'hash_abc',
      passwordSalt: 'salt_abc',
    });
  });
}

describe('NFL LIVE Firestore Security Rules (Tier 1)', () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'nfl-live-security-test',
      firestore: {
        rules: readFileSync('firestore.rules', 'utf8'),
      },
    });
  });

  after(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  test('1. Unauthenticated reads and writes are rejected across all collections', async () => {
    await seedIdentity('1015198', 'uid_admin', 'ADMIN', 'All Floor', 'APPROVED');
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'production_entries', 'PROD_1'), {
        lineId: 'Sew-1_L1',
        floor: 'Sew-1',
        date: TODAY_ISO,
        hourSlot: '08:00-09:00',
        outputQty: 120,
        timestampMillis: Date.now(),
      });
    });

    const unauthedDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(unauthedDb, 'users', '1015198')));
    await assertFails(getDocs(collection(unauthedDb, 'users')));
    await assertFails(getDoc(doc(unauthedDb, 'production_entries', 'PROD_1')));
    await assertFails(
      setDoc(doc(unauthedDb, 'production_entries', 'PROD_2'), {
        lineId: 'Sew-1_L1',
        floor: 'Sew-1',
        date: TODAY_ISO,
        hourSlot: '09:00-10:00',
        outputQty: 110,
      })
    );
    await assertFails(deleteDoc(doc(unauthedDb, 'production_entries', 'PROD_1')));
    await assertFails(getDocs(collection(unauthedDb, 'quality_entries')));
    await assertFails(getDocs(collection(unauthedDb, 'factory_alerts')));
    await assertFails(getDocs(collection(unauthedDb, 'daily_setups')));
    await assertFails(getDocs(collection(unauthedDb, 'monthly_targets')));
    await assertFails(getDocs(collection(unauthedDb, 'weekly_quality')));
  });

  test('2. Non-admin employee cannot self-approve, self-promote to ADMIN, or read other users', async () => {
    await seedIdentity('1015198', 'uid_admin', 'ADMIN', 'All Floor', 'APPROVED');

    // New user registers as PENDING_APPROVAL
    const pendingDb = testEnv.authenticatedContext('uid_emp_2001').firestore();
    await assertSucceeds(
      setDoc(doc(pendingDb, 'auth_bindings', 'uid_emp_2001'), {
        employeeId: '2001',
        firebaseUid: 'uid_emp_2001',
        boundAtMillis: Date.now(),
      })
    );

    // Attempting to register directly as ADMIN or APPROVED must fail
    await assertFails(
      setDoc(doc(pendingDb, 'users', '2001'), {
        employeeId: '2001',
        name: 'Rogue User',
        department: 'Sewing',
        role: 'ADMIN',
        assignedFloor: 'All Floor',
        accountStatus: 'PENDING_APPROVAL',
        firebaseUid: 'uid_emp_2001',
      })
    );
    await assertFails(
      setDoc(doc(pendingDb, 'users', '2001'), {
        employeeId: '2001',
        name: 'Rogue User',
        department: 'Sewing',
        role: 'SUPERVISOR',
        assignedFloor: 'Sew-1',
        accountStatus: 'APPROVED',
        firebaseUid: 'uid_emp_2001',
      })
    );

    // Valid PENDING_APPROVAL registration succeeds
    await assertSucceeds(
      setDoc(doc(pendingDb, 'users', '2001'), {
        employeeId: '2001',
        name: 'Valid Supervisor',
        department: 'Sewing',
        role: 'SUPERVISOR',
        assignedFloor: 'Sew-1',
        accountStatus: 'PENDING_APPROVAL',
        firebaseUid: 'uid_emp_2001',
      })
    );

    // Pending user can create their own registration_requests doc
    await assertSucceeds(
      setDoc(doc(pendingDb, 'registration_requests', 'REQ_EMP_2001'), {
        requestId: 'REQ_EMP_2001',
        employeeId: '2001',
        name: 'Valid Supervisor',
        requestedRole: 'SUPERVISOR',
        assignedFloor: 'Sew-1',
        department: 'Sewing',
        status: 'PENDING',
        requestedByUid: 'uid_emp_2001',
        createdAtMillis: Date.now(),
      })
    );

    // Pending user cannot approve their own registration request or user record
    await assertFails(
      updateDoc(doc(pendingDb, 'registration_requests', 'REQ_EMP_2001'), {
        status: 'APPROVED',
      })
    );
    await assertFails(
      updateDoc(doc(pendingDb, 'users', '2001'), {
        accountStatus: 'APPROVED',
      })
    );
    // Pending user cannot read Admin's profile or list users
    await assertFails(getDoc(doc(pendingDb, 'users', '1015198')));
    await assertFails(getDocs(collection(pendingDb, 'users')));
    // Pending user cannot read production data before approval
    await assertFails(getDocs(collection(pendingDb, 'production_entries')));
  });

  test('3. Unapproved Smart TV cannot read factory data or approve itself; approved TV can read only', async () => {
    await seedIdentity('1015198', 'uid_admin', 'ADMIN', 'All Floor', 'APPROVED');
    await seedIdentity('TV-SEW1-01', 'uid_tv_1', 'TV_VIEWER', 'Sew-1', 'PENDING_APPROVAL');

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'production_entries', 'PROD_SEW1'), {
        lineId: 'Sew-1_L1',
        floor: 'Sew-1',
        date: TODAY_ISO,
        hourSlot: '08:00-09:00',
        outputQty: 135,
        timestampMillis: Date.now(),
      });
    });

    const tvDb = testEnv.authenticatedContext('uid_tv_1').firestore();

    // Unapproved TV can submit device approval request
    await assertSucceeds(
      setDoc(doc(tvDb, 'device_approval_requests', 'REQ_TV_1'), {
        requestId: 'REQ_TV_1',
        deviceId: 'TV-SEW1-01',
        deviceName: 'Sew-1 Main Display',
        requestedFloor: 'Sew-1',
        status: 'PENDING',
        requestedByUid: 'uid_tv_1',
        createdAtMillis: Date.now(),
      })
    );

    // Unapproved TV cannot approve its own device request or read production data
    await assertFails(
      updateDoc(doc(tvDb, 'device_approval_requests', 'REQ_TV_1'), {
        status: 'APPROVED',
      })
    );
    await assertFails(getDoc(doc(tvDb, 'production_entries', 'PROD_SEW1')));

    // Admin approves the TV device
    const adminDb = testEnv.authenticatedContext('uid_admin').firestore();
    await assertSucceeds(
      updateDoc(doc(adminDb, 'device_approval_requests', 'REQ_TV_1'), {
        status: 'APPROVED',
        processedByAdminId: '1015198',
      })
    );
    await assertSucceeds(
      updateDoc(doc(adminDb, 'users', 'TV-SEW1-01'), {
        accountStatus: 'APPROVED',
      })
    );

    // Approved TV can now read production data, but still cannot write or delete it
    await assertSucceeds(getDoc(doc(tvDb, 'production_entries', 'PROD_SEW1')));
    await assertFails(
      updateDoc(doc(tvDb, 'production_entries', 'PROD_SEW1'), {
        outputQty: 999,
      })
    );
    await assertFails(deleteDoc(doc(tvDb, 'production_entries', 'PROD_SEW1')));
  });

  test('4. Supervisor is restricted to assigned floor, current operational window, and allowed collections', async () => {
    await seedIdentity('SUP_SEW1', 'uid_sup_sew1', 'SUPERVISOR', 'Sew-1', 'APPROVED');
    const supDb = testEnv.authenticatedContext('uid_sup_sew1').firestore();
    const nowMillis = Date.now();

    // Allowed: create current-day production entry on assigned floor Sew-1
    await assertSucceeds(
      setDoc(doc(supDb, 'production_entries', 'PROD_SEW1_NOW'), {
        lineId: 'Sew-1_L1',
        floor: 'Sew-1',
        date: TODAY_ISO,
        hourSlot: '08:00-09:00',
        outputQty: 140,
        timestampMillis: nowMillis,
      })
    );

    // Denied: write production entry on unassigned floor Sew-2
    await assertFails(
      setDoc(doc(supDb, 'production_entries', 'PROD_SEW2_NOW'), {
        lineId: 'Sew-2_L1',
        floor: 'Sew-2',
        date: TODAY_ISO,
        hourSlot: '08:00-09:00',
        outputQty: 140,
        timestampMillis: nowMillis,
      })
    );

    // Denied: write quality entry (Supervisor is not QUALITY/ADMIN/IE/AUDITOR)
    await assertFails(
      setDoc(doc(supDb, 'quality_entries', 'QA_SEW1_NOW'), {
        lineId: 'Sew-1_L1',
        floor: 'Sew-1',
        date: TODAY_ISO,
        hourSlot: '08:00-09:00',
        checkedGarments: 100,
        timestampMillis: nowMillis,
      })
    );

    // Denied: write monthly_targets or weekly_quality
    await assertFails(
      setDoc(doc(supDb, 'monthly_targets', 'Sew-1'), {
        floor: 'Sew-1',
        monthlyTargetPcs: 50000,
      })
    );
    await assertFails(
      setDoc(doc(supDb, 'weekly_quality', '2026-W44'), {
        weekKey: '2026-W44',
        weekStartIso: TODAY_ISO,
      })
    );

    // Denied: edit historical production record (timestamp 5 days ago)
    const fiveDaysAgoMillis = nowMillis - 5 * 86400000;
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'production_entries', 'PROD_SEW1_OLD'), {
        lineId: 'Sew-1_L1',
        floor: 'Sew-1',
        date: '2026-10-15',
        hourSlot: '08:00-09:00',
        outputQty: 100,
        timestampMillis: fiveDaysAgoMillis,
      });
    });

    await assertFails(
      updateDoc(doc(supDb, 'production_entries', 'PROD_SEW1_OLD'), {
        outputQty: 250,
      })
    );

    // Denied: delete production record
    await assertFails(deleteDoc(doc(supDb, 'production_entries', 'PROD_SEW1_NOW')));
  });

  test('5. Quality inspector can write quality_entries on assigned floor only, and Admin has full governance', async () => {
    await seedIdentity('QA_SEW2', 'uid_qa_sew2', 'QUALITY', 'Sew-2', 'APPROVED');
    await seedIdentity('1015198', 'uid_admin', 'ADMIN', 'All Floor', 'APPROVED');

    const qaDb = testEnv.authenticatedContext('uid_qa_sew2').firestore();
    const adminDb = testEnv.authenticatedContext('uid_admin').firestore();
    const nowMillis = Date.now();

    // Quality inspector allowed on Sew-2 quality_entries
    await assertSucceeds(
      setDoc(doc(qaDb, 'quality_entries', 'QA_SEW2_1'), {
        lineId: 'Sew-2_L1',
        floor: 'Sew-2',
        date: TODAY_ISO,
        hourSlot: '10:00-11:00',
        checkedGarments: 120,
        defectiveGarments: 4,
        timestampMillis: nowMillis,
      })
    );

    // Quality inspector denied on Sew-1 quality_entries and denied on production_entries
    await assertFails(
      setDoc(doc(qaDb, 'quality_entries', 'QA_SEW1_1'), {
        lineId: 'Sew-1_L1',
        floor: 'Sew-1',
        date: TODAY_ISO,
        hourSlot: '10:00-11:00',
        checkedGarments: 120,
        timestampMillis: nowMillis,
      })
    );
    await assertFails(
      setDoc(doc(qaDb, 'production_entries', 'PROD_SEW2_1'), {
        lineId: 'Sew-2_L1',
        floor: 'Sew-2',
        date: TODAY_ISO,
        hourSlot: '10:00-11:00',
        outputQty: 120,
        timestampMillis: nowMillis,
      })
    );

    // Admin allowed to update monthly_targets, weekly_quality, and delete operational entries
    await assertSucceeds(
      setDoc(doc(adminDb, 'monthly_targets', 'Sew-2'), {
        floor: 'Sew-2',
        monthlyTargetPcs: 180000,
        workingDays: 26,
      })
    );
    await assertSucceeds(
      setDoc(doc(adminDb, 'weekly_quality', '2026-W44'), {
        weekKey: '2026-W44',
        weekStartIso: TODAY_ISO,
        bsOqlPercent: 1.2,
        nflAqcPercent: 1.5,
      })
    );
    await assertSucceeds(deleteDoc(doc(adminDb, 'quality_entries', 'QA_SEW2_1')));

    // Initial Admin 1015198 cannot be deleted or demoted even by Admin
    await assertFails(deleteDoc(doc(adminDb, 'users', '1015198')));
    await assertFails(
      updateDoc(doc(adminDb, 'users', '1015198'), {
        role: 'SUPERVISOR',
      })
    );
  });
});
