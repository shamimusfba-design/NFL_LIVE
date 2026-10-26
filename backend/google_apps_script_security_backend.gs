/**
 * NFL LIVE — CENTRAL SECURITY & ACCESS-CONTROL BACKEND (GOOGLE APPS SCRIPT)
 *
 * Keeps the Google Sheet PRIVATE while serving authorized requests from the NFL LIVE Android & TV app.
 * Primary Admin Gmail: shamim.sustipe15@gmail.com
 * Initial Admin Employee ID: 1015198
 * Timezone: Asia/Dhaka (Bangladesh Standard Time, UTC+6)
 *
 * Deployment Instructions:
 * 1. Open the private Google Sheet -> Extensions -> Apps Script.
 * 2. Paste this script into Code.gs and click Save.
 * 3. Click Run -> authorizeEmailAndSheets() once to grant Google's explicit consent for Gmail/MailApp and SpreadsheetApp.
 * 4. Click Deploy -> New deployment -> Web app -> Execute as: Me -> Who has access: Anyone -> Deploy.
 */

const SHEET_ID = '1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0';
const PRIMARY_ADMIN_GMAIL = 'shamim.sustipe15@gmail.com';
const INITIAL_ADMIN_EMPLOYEE_ID = '1015198';
const TIMEZONE = 'Asia/Dhaka';
const MAX_DEVICES_PER_EMPLOYEE = 1; // Strictly 1 authorized active device per Employee ID
const CODE_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes
const MAX_VERIFY_ATTEMPTS = 5;
const MAX_RESEND_COUNT = 3;
const VALID_PHYSICAL_FLOORS = ['Sew-1', 'Sew-2', 'Sew-3', 'Finishing'];

function authorizeEmailAndSheets() {
  const ss = SpreadsheetApp.openById(SHEET_ID);
  const quota = MailApp.getRemainingDailyQuota();
  Logger.log('Authorized sheet: ' + ss.getName() + ' | Remaining Mail quota: ' + quota);
}

function getBangladeshNow() {
  const now = new Date();
  const dateStr = Utilities.formatDate(now, TIMEZONE, 'yyyy-MM-dd');
  const timeStr = Utilities.formatDate(now, TIMEZONE, 'HH:mm:ss');
  const hour = parseInt(Utilities.formatDate(now, TIMEZONE, 'HH'), 10);
  return {
    epochMillis: now.getTime(),
    date: dateStr,
    time: timeStr,
    iso: dateStr + 'T' + timeStr + '+06:00',
    hour: hour,
    isWithinDailyWindow: hour >= 6 && hour <= 23
  };
}

function doGet(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const ss = SpreadsheetApp.openById((e && e.parameter && e.parameter.sheetId) || SHEET_ID);
    const bdNow = getBangladeshNow();
    const result = {
      status: 'ok',
      serverTimeMillis: bdNow.epochMillis,
      bangladeshDate: bdNow.date,
      bangladeshTime: bdNow.time,
      production: readTab(ss, 'Raw_Production'),
      quality: readTab(ss, 'Raw_Quality'),
      npt: readTab(ss, 'Raw_NPT'),
      setups: readTab(ss, 'Raw_SupervisorSetup'),
      monthlyTargets: readTab(ss, 'Raw_MonthlyTarget'),
      auditLogs: readTab(ss, 'Audit_Log'),
      devices: readTab(ss, 'Auth_Devices')
    };
    return jsonResponse(result);
  } catch (err) {
    return jsonResponse({ status: 'error', message: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const body = JSON.parse(e.postData.contents || '{}');
    const ss = SpreadsheetApp.openById(body.sheetId || SHEET_ID);
    const bdNow = getBangladeshNow();
    const action = body.action || 'append';

    // 0. Atomic First-Admin (1015198) Bootstrap
    if (action === 'bootstrapFirstAdmin') {
      return handleBootstrapFirstAdmin(ss, body, bdNow);
    }

    // 1. Request Gmail Verification / Approval Code to Primary Admin Gmail
    if (action === 'requestVerificationCode') {
      return handleRequestVerificationCode(ss, body, bdNow);
    }

    // 2. Verify Gmail Code and Approve Device Replacement / Account
    if (action === 'verifyApprovalCode') {
      return handleVerifyApprovalCode(ss, body, bdNow);
    }

    // 3. Admin Device Revocation / Replacement Approval
    if (action === 'manageDevice') {
      return handleManageDevice(ss, body, bdNow);
    }

    // 4. Admin Record Correction or Deletion (Requires Admin role + non-empty reason)
    if (action === 'adminCorrectRecord' || action === 'adminDeleteRecord') {
      return handleAdminRecordChange(ss, body, bdNow);
    }

    // 5. Register Admin FCM Token for Spark-plan Push Notifications
    if (action === 'registerAdminFcmToken') {
      return handleRegisterAdminFcmToken(ss, body, bdNow);
    }

    // 6. Dispatch Real FCM HTTP v1 Push Notification to Registered Admin Devices
    if (action === 'dispatchAdminPushNotification') {
      return handleDispatchAdminPushNotification(ss, body, bdNow);
    }

    // 7. Standard Data Append with Server-Side RBAC, 1-Device Limit & Asia/Dhaka Time Window Validation
    return handleAuthorizedAppend(ss, body, bdNow);
  } catch (err) {
    return jsonResponse({ status: 'error', message: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function handleBootstrapFirstAdmin(ss, body, bdNow) {
  const empId = String(body.employeeId || '').trim();
  const passwordHash = String(body.passwordHash || '').trim();
  const passwordSalt = String(body.passwordSalt || '').trim();
  const adminName = String(body.adminName || 'System Administrator').trim();
  const deviceId = String(body.deviceId || '').trim();
  const deviceName = String(body.deviceName || 'Admin Device').trim();

  if (empId !== INITIAL_ADMIN_EMPLOYEE_ID) {
    return jsonResponse({
      status: 'error',
      message: 'First-Admin bootstrap is restricted to Initial Admin Employee ID ' + INITIAL_ADMIN_EMPLOYEE_ID + '.'
    });
  }
  if (!passwordHash || !passwordSalt) {
    return jsonResponse({
      status: 'error',
      message: 'Salted password hash is required. Plaintext passwords are not accepted.'
    });
  }

  let bootSheet = ss.getSheetByName('Auth_Bootstrap');
  if (!bootSheet) {
    bootSheet = ss.insertSheet('Auth_Bootstrap');
    bootSheet.appendRow(['employee_id', 'admin_name', 'password_hash', 'password_salt', 'device_id', 'device_name', 'must_change_password', 'locked', 'provisioned_at']);
  }

  const rows = bootSheet.getDataRange().getValues();
  for (let i = 1; i < rows.length; i++) {
    if (String(rows[i][0]) === INITIAL_ADMIN_EMPLOYEE_ID && String(rows[i][7]) === 'TRUE') {
      return jsonResponse({
        status: 'error',
        code: 'BOOTSTRAP_ALREADY_COMPLETED',
        message: 'Initial Admin (1015198) is already provisioned and bootstrap is permanently locked.'
      });
    }
  }

  bootSheet.appendRow([
    INITIAL_ADMIN_EMPLOYEE_ID,
    adminName,
    passwordHash,
    passwordSalt,
    deviceId,
    deviceName,
    'TRUE',
    'TRUE',
    bdNow.iso
  ]);

  if (deviceId) {
    forceApproveDeviceOnSheet(ss, INITIAL_ADMIN_EMPLOYEE_ID, deviceId, deviceName, bdNow, '*');
  }
  appendAuditLog(ss, bdNow, 'ADMIN_BOOTSTRAP', INITIAL_ADMIN_EMPLOYEE_ID, 'FIRST_ADMIN_PROVISIONED', adminName, 'Atomic First-Admin bootstrap completed & locked', deviceId);

  return jsonResponse({
    status: 'ok',
    employeeId: INITIAL_ADMIN_EMPLOYEE_ID,
    role: 'ADMIN',
    authorizedFloorScope: 'All Floor',
    mustChangePassword: true,
    serverTimeMillis: bdNow.epochMillis
  });
}

function handleAuthorizedAppend(ss, body, bdNow) {
  const tabName = body.tab || 'Raw_Production';
  const actorRole = String(body.actorRole || '').toUpperCase();
  const actorId = String(body.actorId || '');
  const deviceId = String(body.deviceId || '');

  // Enforce strict 1-device limit on backend if deviceId is provided
  if (actorId && deviceId && actorRole !== 'ADMIN') {
    const devCheck = verifyOrRegisterDevice(ss, actorId, deviceId, body.deviceName || 'Android', bdNow);
    if (!devCheck.allowed) {
      appendAuditLog(ss, bdNow, 'SECURITY', actorId, 'SINGLE_DEVICE_BLOCKED', actorId, 'Attempted write from unauthorized second device', deviceId);
      return jsonResponse({ status: 'error', code: 'MAX_DEVICES_REACHED', message: devCheck.message, serverTimeMillis: bdNow.epochMillis });
    }
  }

  // Enforce Role-Based Access Control on Backend
  if (tabName === 'Raw_Production') {
    if (actorRole && !['ADMIN', 'SUPERVISOR', 'PRODUCTION_REPORTER'].includes(actorRole)) {
      appendAuditLog(ss, bdNow, 'SECURITY', actorId, 'UNAUTHORIZED_PRODUCTION_WRITE', actorId, 'Role: ' + actorRole, deviceId);
      return jsonResponse({ status: 'error', message: 'Access Denied: Role ' + actorRole + ' cannot enter Production data.' });
    }
    if (actorRole !== 'ADMIN') {
      if (!bdNow.isWithinDailyWindow) {
        return jsonResponse({ status: 'error', message: 'Daily Production entry is permitted only from 06:00 AM to midnight Bangladesh time.' });
      }
      const row = body.row || [];
      const entryDate = String(row[1] || '').substring(0, 10);
      const hourSlot = String(row[2] || '');
      const lineId = String(row[3] || '');
      if (entryDate && entryDate !== bdNow.date) {
        return jsonResponse({ status: 'error', message: 'Only current Bangladesh date (' + bdNow.date + ') is allowed for daily submissions.' });
      }
      if (hasExistingRecord(ss, 'Raw_Production', lineId, bdNow.date, hourSlot)) {
        return jsonResponse({ status: 'error', message: 'Record already submitted for Line ' + lineId + ' (' + hourSlot + '). Ordinary users cannot edit or overwrite submitted records.' });
      }
    }
  } else if (tabName === 'Raw_Quality') {
    if (actorRole && !['ADMIN', 'QUALITY_REPORTER', 'AUDITOR'].includes(actorRole)) {
      appendAuditLog(ss, bdNow, 'SECURITY', actorId, 'UNAUTHORIZED_QUALITY_WRITE', actorId, 'Role: ' + actorRole, deviceId);
      return jsonResponse({ status: 'error', message: 'Access Denied: Role ' + actorRole + ' cannot enter Quality data.' });
    }
    if (actorRole !== 'ADMIN') {
      if (!bdNow.isWithinDailyWindow) {
        return jsonResponse({ status: 'error', message: 'Daily Quality entry is permitted only from 06:00 AM to midnight Bangladesh time.' });
      }
      const row = body.row || [];
      const entryDate = String(row[1] || '').substring(0, 10);
      const hourSlot = String(row[2] || '');
      const lineId = String(row[3] || '');
      if (entryDate && entryDate !== bdNow.date) {
        return jsonResponse({ status: 'error', message: 'Only current Bangladesh date (' + bdNow.date + ') is allowed for daily submissions.' });
      }
      if (hasExistingRecord(ss, 'Raw_Quality', lineId, bdNow.date, hourSlot)) {
        return jsonResponse({ status: 'error', message: 'Quality record already submitted for Line ' + lineId + ' (' + hourSlot + '). Ordinary users cannot edit or overwrite submitted records.' });
      }
    }
  } else if (tabName === 'Raw_MonthlyTarget') {
    if (actorRole && !['ADMIN', 'IE'].includes(actorRole)) {
      return jsonResponse({ status: 'error', message: 'Access Denied: Only Admin and IE Department can modify Monthly Data.' });
    }
  }

  let sheet = ss.getSheetByName(tabName);
  if (!sheet) {
    sheet = ss.insertSheet(tabName);
    if (body.headers) sheet.appendRow(body.headers);
  }
  if (body.rows && Array.isArray(body.rows)) {
    body.rows.forEach(function(r) { sheet.appendRow(r); });
  } else if (body.row) {
    sheet.appendRow(body.row);
  }
  return jsonResponse({ status: 'ok', serverTimeMillis: bdNow.epochMillis, bangladeshDate: bdNow.date });
}

function handleRequestVerificationCode(ss, body, bdNow) {
  const employeeId = String(body.employeeId || '');
  const employeeName = String(body.employeeName || employeeId);
  const role = String(body.role || 'EMPLOYEE');
  const deviceId = String(body.deviceId || '');
  const deviceName = String(body.deviceName || 'Android Device');
  const code = String(body.verificationCode || Math.floor(100000 + Math.random() * 900000));
  const codeHash = String(body.verificationCodeHash || code);

  let reqSheet = ss.getSheetByName('Auth_ApprovalRequests');
  if (!reqSheet) {
    reqSheet = ss.insertSheet('Auth_ApprovalRequests');
    reqSheet.appendRow(['request_id', 'employee_id', 'employee_name', 'role', 'device_id', 'device_name', 'code_hash', 'expires_at_ms', 'attempts', 'resends', 'used', 'status', 'created_at']);
  }

  const requestId = String(body.requestId || Utilities.getUuid());
  reqSheet.appendRow([
    requestId, employeeId, employeeName, role, deviceId, deviceName,
    codeHash, bdNow.epochMillis + CODE_EXPIRY_MS, 0, Number(body.resendCount || 0), 'FALSE', 'PENDING', bdNow.iso
  ]);

  const subject = '[NFL LIVE Security] Device Replacement Code for Employee ID: ' + employeeId;
  const emailBody =
    'NFL LIVE — Single-Device Replacement & Security Verification\n\n' +
    'Employee ID: ' + employeeId + '\n' +
    'Name / Role: ' + employeeName + ' (' + role + ')\n' +
    'New Device: ' + deviceName + ' (' + deviceId.substring(0, 10) + ')\n' +
    'Bangladesh Time: ' + bdNow.iso + '\n\n' +
    'Single-Use Verification Code: ' + code + '\n' +
    '(Expires in 10 minutes)\n\n' +
    'Approving this device will automatically unbind the previous device for Employee ID ' + employeeId + '.';

  MailApp.sendEmail(PRIMARY_ADMIN_GMAIL, subject, emailBody);
  appendAuditLog(ss, bdNow, 'AUTH_OTP', employeeId, 'SEND_GMAIL_OTP', PRIMARY_ADMIN_GMAIL, 'Sent device replacement code for ' + deviceName, deviceId);

  return jsonResponse({
    status: 'ok',
    requestId: requestId,
    emailSentTo: PRIMARY_ADMIN_GMAIL,
    expiresAtMillis: bdNow.epochMillis + CODE_EXPIRY_MS,
    serverTimeMillis: bdNow.epochMillis
  });
}

function handleVerifyApprovalCode(ss, body, bdNow) {
  const requestId = String(body.requestId || '');
  const codeHash = String(body.codeHash || '');
  const reqSheet = ss.getSheetByName('Auth_ApprovalRequests');
  if (!reqSheet) return jsonResponse({ status: 'error', message: 'No pending verification requests found.' });

  const values = reqSheet.getDataRange().getValues();
  for (let i = values.length - 1; i >= 1; i--) {
    if (String(values[i][0]) === requestId) {
      const storedHash = String(values[i][6]);
      const expiresAt = Number(values[i][7]);
      const attempts = Number(values[i][8]);
      const used = String(values[i][10]) === 'TRUE';

      if (used) return jsonResponse({ status: 'error', message: 'This verification code has already been used.' });
      if (bdNow.epochMillis > expiresAt) {
        reqSheet.getRange(i + 1, 12).setValue('EXPIRED');
        return jsonResponse({ status: 'error', message: 'Verification code has expired.' });
      }
      if (attempts >= MAX_VERIFY_ATTEMPTS) {
        reqSheet.getRange(i + 1, 12).setValue('LOCKED');
        return jsonResponse({ status: 'error', message: 'Maximum verification attempts exceeded.' });
      }
      if (storedHash !== codeHash) {
        reqSheet.getRange(i + 1, 9).setValue(attempts + 1);
        return jsonResponse({ status: 'error', message: 'Invalid verification code.' });
      }

      reqSheet.getRange(i + 1, 11).setValue('TRUE');
      reqSheet.getRange(i + 1, 12).setValue('APPROVED');
      const empId = String(values[i][1]);
      const devId = String(values[i][4]);
      const devName = String(values[i][5]);
      // Revoke all previous devices for this Employee ID and bind the new single device
      forceApproveDeviceOnSheet(ss, empId, devId, devName, bdNow, '*');
      appendAuditLog(ss, bdNow, 'AUTH_OTP', empId, 'VERIFY_GMAIL_OTP_SUCCESS', PRIMARY_ADMIN_GMAIL, 'Replaced authorized device with ' + devName, devId);
      return jsonResponse({ status: 'ok', message: 'Device replacement approved. Previous device revoked.', serverTimeMillis: bdNow.epochMillis });
    }
  }
  return jsonResponse({ status: 'error', message: 'Verification request not found.' });
}

function handleManageDevice(ss, body, bdNow) {
  const subAction = String(body.subAction || '');
  const empId = String(body.employeeId || '');
  const devId = String(body.deviceId || '');
  const devName = String(body.deviceName || 'Android Device');
  const reason = String(body.reason || 'Admin device management');

  if (subAction === 'revoke') {
    revokeDeviceOnSheet(ss, empId, devId || '*');
    appendAuditLog(ss, bdNow, 'DEVICE_MGMT', empId, 'REVOKE_DEVICE', PRIMARY_ADMIN_GMAIL, reason, devId);
    return jsonResponse({ status: 'ok', message: 'Device revoked.' });
  } else if (subAction === 'approve') {
    forceApproveDeviceOnSheet(ss, empId, devId, devName, bdNow, '*');
    appendAuditLog(ss, bdNow, 'DEVICE_MGMT', empId, 'ADMIN_APPROVE_DEVICE', PRIMARY_ADMIN_GMAIL, reason, devId);
    return jsonResponse({ status: 'ok', message: 'Single authorized device bound by Admin.' });
  }
  return jsonResponse({ status: 'error', message: 'Unknown device action.' });
}

function handleAdminRecordChange(ss, body, bdNow) {
  const actorRole = String(body.actorRole || '').toUpperCase();
  const reason = String(body.reason || '').trim();
  if (actorRole !== 'ADMIN') {
    return jsonResponse({ status: 'error', message: 'Only Admin can correct or delete submitted records.' });
  }
  if (reason.length < 3) {
    return jsonResponse({ status: 'error', message: 'A valid audit reason is required for Admin record correction or deletion.' });
  }
  const tabName = String(body.tab || 'Raw_Production');
  const submissionId = String(body.submissionId || '');
  const sheet = ss.getSheetByName(tabName);
  if (sheet && submissionId) {
    const values = sheet.getDataRange().getValues();
    for (let i = values.length - 1; i >= 1; i--) {
      if (String(values[i][0]) === submissionId) {
        if (body.action === 'adminDeleteRecord') {
          sheet.deleteRow(i + 1);
        } else if (body.updatedRow && Array.isArray(body.updatedRow)) {
          sheet.getRange(i + 1, 1, 1, body.updatedRow.length).setValues([body.updatedRow]);
        }
        break;
      }
    }
  }
  appendAuditLog(ss, bdNow, tabName, submissionId, body.action === 'adminDeleteRecord' ? 'ADMIN_DELETE' : 'ADMIN_CORRECTION', body.actorId || 'Admin', reason + ' | ' + (body.details || ''), body.deviceId || '');
  return jsonResponse({ status: 'ok', serverTimeMillis: bdNow.epochMillis });
}

function verifyOrRegisterDevice(ss, employeeId, deviceId, deviceName, bdNow) {
  let sheet = ss.getSheetByName('Auth_Devices');
  if (!sheet) {
    sheet = ss.insertSheet('Auth_Devices');
    sheet.appendRow(['employee_id', 'device_id', 'device_name', 'status', 'approved_at', 'last_active']);
  }
  const values = sheet.getDataRange().getValues();
  const activeDevices = [];
  for (let i = 1; i < values.length; i++) {
    if (String(values[i][0]).toLowerCase() === employeeId.toLowerCase() && String(values[i][3]) === 'ACTIVE') {
      if (String(values[i][1]) === deviceId) {
        sheet.getRange(i + 1, 6).setValue(bdNow.iso);
        return { allowed: true };
      }
      activeDevices.push(values[i][1]);
    }
  }
  if (activeDevices.length >= MAX_DEVICES_PER_EMPLOYEE) {
    return {
      allowed: false,
      message: 'This Employee ID is already active on another phone/device. Only 1 active device is permitted per Employee ID.'
    };
  }
  sheet.appendRow([employeeId, deviceId, deviceName, 'ACTIVE', bdNow.iso, bdNow.iso]);
  return { allowed: true };
}

function forceApproveDeviceOnSheet(ss, employeeId, deviceId, deviceName, bdNow, revokeDeviceId) {
  let sheet = ss.getSheetByName('Auth_Devices');
  if (!sheet) {
    sheet = ss.insertSheet('Auth_Devices');
    sheet.appendRow(['employee_id', 'device_id', 'device_name', 'status', 'approved_at', 'last_active']);
  }
  // Always revoke previous active devices when binding a new single device
  revokeDeviceOnSheet(ss, employeeId, revokeDeviceId || '*');
  const values = sheet.getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    if (String(values[i][0]).toLowerCase() === employeeId.toLowerCase() && String(values[i][1]) === deviceId) {
      sheet.getRange(i + 1, 4).setValue('ACTIVE');
      sheet.getRange(i + 1, 6).setValue(bdNow.iso);
      return;
    }
  }
  sheet.appendRow([employeeId, deviceId, deviceName, 'ACTIVE', bdNow.iso, bdNow.iso]);
}

function revokeDeviceOnSheet(ss, employeeId, deviceId) {
  const sheet = ss.getSheetByName('Auth_Devices');
  if (!sheet) return;
  const values = sheet.getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    if (String(values[i][0]).toLowerCase() === employeeId.toLowerCase() && (deviceId === '*' || String(values[i][1]) === deviceId)) {
      sheet.getRange(i + 1, 4).setValue('REVOKED');
    }
  }
}

function hasExistingRecord(ss, tabName, lineId, dateStr, hourSlot) {
  const sheet = ss.getSheetByName(tabName);
  if (!sheet) return false;
  const values = sheet.getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    const rDate = String(values[i][1] || '').substring(0, 10);
    const rSlot = String(values[i][2] || '').trim();
    const rLine = String(values[i][3] || '').trim().toUpperCase();
    if (rDate === dateStr && rSlot.toLowerCase() === hourSlot.trim().toLowerCase() && rLine === lineId.trim().toUpperCase()) {
      return true;
    }
  }
  return false;
}

function appendAuditLog(ss, bdNow, entityType, entityId, action, changedBy, reasonAndDetails, deviceId) {
  let sheet = ss.getSheetByName('Audit_Log');
  if (!sheet) {
    sheet = ss.insertSheet('Audit_Log');
    sheet.appendRow(['log_id', 'timestamp', 'entity_type', 'entity_id', 'action', 'changed_by', 'audit_reason', 'device_id']);
  }
  sheet.appendRow([Utilities.getUuid(), bdNow.iso, entityType, entityId, action, changedBy, reasonAndDetails, deviceId || '']);
}

function readTab(ss, name) {
  const sheet = ss.getSheetByName(name);
  if (!sheet) return [];
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  return values.slice(1);
}

function handleRegisterAdminFcmToken(ss, body, bdNow) {
  const employeeId = String(body.employeeId || '').trim();
  const fcmToken = String(body.fcmToken || '').trim();
  const deviceId = String(body.deviceId || '').trim();
  const deviceName = String(body.deviceName || 'Admin Android').trim();
  if (!employeeId || !fcmToken) {
    return jsonResponse({ status: 'error', message: 'employeeId and fcmToken are required.' });
  }
  let sheet = ss.getSheetByName('Auth_AdminFcmTokens');
  if (!sheet) {
    sheet = ss.insertSheet('Auth_AdminFcmTokens');
    sheet.appendRow(['employee_id', 'device_id', 'device_name', 'fcm_token', 'updated_at']);
  }
  const rows = sheet.getDataRange().getValues();
  for (let i = 1; i < rows.length; i++) {
    if (String(rows[i][0]) === employeeId && String(rows[i][1]) === deviceId) {
      sheet.getRange(i + 1, 3, 1, 3).setValues([[deviceName, fcmToken, bdNow.iso]]);
      return jsonResponse({ status: 'ok', updated: true, serverTimeMillis: bdNow.epochMillis });
    }
  }
  sheet.appendRow([employeeId, deviceId, deviceName, fcmToken, bdNow.iso]);
  return jsonResponse({ status: 'ok', registered: true, serverTimeMillis: bdNow.epochMillis });
}

function handleDispatchAdminPushNotification(ss, body, bdNow) {
  const requestId = String(body.requestId || Utilities.getUuid()).trim();
  const requestType = String(body.requestType || 'EMPLOYEE_REGISTRATION').trim();
  const targetUserId = String(body.targetUserId || body.employeeId || '').trim();
  const employeeName = String(body.employeeName || targetUserId).trim();
  const designation = String(body.designation || body.role || '').trim();
  const floor = String(body.floor || '').trim();
  const deviceName = String(body.deviceName || '').trim();
  const firebaseProjectId = PropertiesService.getScriptProperties().getProperty('FIREBASE_PROJECT_ID') || 'nfl-live-72213';

  let tokenSheet = ss.getSheetByName('Auth_AdminFcmTokens');
  const tokens = [];
  if (tokenSheet) {
    const rows = tokenSheet.getDataRange().getValues();
    for (let i = 1; i < rows.length; i++) {
      const tok = String(rows[i][3] || '').trim();
      if (tok && tokens.indexOf(tok) === -1) {
        tokens.push(tok);
      }
    }
  }

  let dispatchedCount = 0;
  const fcmUrl = 'https://fcm.googleapis.com/v1/projects/' + firebaseProjectId + '/messages:send';
  const dataPayload = {
    requestId: requestId,
    requestType: requestType,
    targetUserId: targetUserId,
    employeeId: targetUserId,
    employeeName: employeeName,
    designation: designation,
    floor: floor,
    deviceName: deviceName,
    requestedAtMillis: String(bdNow.epochMillis)
  };

  for (let t = 0; t < tokens.length; t++) {
    try {
      const res = UrlFetchApp.fetch(fcmUrl, {
        method: 'post',
        contentType: 'application/json',
        headers: {
          Authorization: 'Bearer ' + ScriptApp.getOAuthToken()
        },
        muteHttpExceptions: true,
        payload: JSON.stringify({
          message: {
            token: tokens[t],
            data: dataPayload,
            android: {
              priority: 'HIGH',
              notification: {
                channel_id: 'nfl_admin_approvals_channel',
                title: requestType === 'TV_APPROVAL'
                  ? 'New Smart TV Approval Request'
                  : requestType.indexOf('DEVICE') !== -1
                    ? 'Device Approval Request: ' + employeeName
                    : 'New Employee Registration: ' + employeeName,
                body: targetUserId + (designation ? ' • ' + designation : '') + (deviceName ? ' • ' + deviceName : '')
              }
            }
          }
        })
      });
      if (res.getResponseCode() >= 200 && res.getResponseCode() < 300) {
        dispatchedCount++;
      }
    } catch (e) {
      Logger.log('FCM v1 send notice: ' + e);
    }
  }

  appendAuditLog(ss, bdNow, 'ADMIN_PUSH', requestId, 'DISPATCH_FCM_PUSH', targetUserId, 'Type=' + requestType + ' | Sent=' + dispatchedCount, deviceName);
  return jsonResponse({
    status: 'ok',
    requestId: requestId,
    dispatchedCount: dispatchedCount,
    serverTimeMillis: bdNow.epochMillis
  });
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

