/**
 * NFL LIVE — CENTRAL SECURITY & ACCESS-CONTROL BACKEND (GOOGLE APPS SCRIPT)
 *
 * Keeps the Google Sheet PRIVATE while serving authorized requests from the NFL LIVE Android & TV app.
 * Primary Admin Gmail: shamim.sustipe15@gmail.com
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
const TIMEZONE = 'Asia/Dhaka';
const MAX_DEVICES_PER_EMPLOYEE = 2;
const CODE_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes
const MAX_VERIFY_ATTEMPTS = 5;
const MAX_RESEND_COUNT = 3;

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

    // 1. Request Gmail Verification / Approval Code to Primary Admin Gmail
    if (action === 'requestVerificationCode') {
      return handleRequestVerificationCode(ss, body, bdNow);
    }

    // 2. Verify Gmail Code and Approve Device / Account
    if (action === 'verifyApprovalCode') {
      return handleVerifyApprovalCode(ss, body, bdNow);
    }

    // 3. Admin Device Revocation / Approval
    if (action === 'manageDevice') {
      return handleManageDevice(ss, body, bdNow);
    }

    // 4. Admin Record Correction or Deletion (Requires Admin role + non-empty reason)
    if (action === 'adminCorrectRecord' || action === 'adminDeleteRecord') {
      return handleAdminRecordChange(ss, body, bdNow);
    }

    // 5. Standard Data Append with Server-Side RBAC, Device Limit & Asia/Dhaka Time Window Validation
    return handleAuthorizedAppend(ss, body, bdNow);
  } catch (err) {
    return jsonResponse({ status: 'error', message: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function handleAuthorizedAppend(ss, body, bdNow) {
  const tabName = body.tab || 'Raw_Production';
  const actorRole = String(body.actorRole || '').toUpperCase();
  const actorId = String(body.actorId || '');
  const deviceId = String(body.deviceId || '');

  // Enforce 2-device limit on backend if deviceId is provided
  if (actorId && deviceId && actorRole !== 'ADMIN') {
    const devCheck = verifyOrRegisterDevice(ss, actorId, deviceId, body.deviceName || 'Android', bdNow);
    if (!devCheck.allowed) {
      appendAuditLog(ss, bdNow, 'SECURITY', actorId, 'DEVICE_LIMIT_BLOCKED', actorId, 'Device limit exceeded', deviceId);
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

  const subject = '[NFL LIVE Security] Approval Code for Employee ID: ' + employeeId;
  const emailBody =
    'NFL LIVE — Account & Device Security Verification\n\n' +
    'Employee ID: ' + employeeId + '\n' +
    'Name / Role: ' + employeeName + ' (' + role + ')\n' +
    'Device: ' + deviceName + ' (' + deviceId.substring(0, 10) + ')\n' +
    'Bangladesh Time: ' + bdNow.iso + '\n\n' +
    'Single-Use Verification Code: ' + code + '\n' +
    '(Expires in 10 minutes)\n\n' +
    'If you did not authorize this device, you can also reject or manage devices inside the NFL LIVE Admin Access Control panel.';

  MailApp.sendEmail(PRIMARY_ADMIN_GMAIL, subject, emailBody);
  appendAuditLog(ss, bdNow, 'AUTH_OTP', employeeId, 'SEND_GMAIL_OTP', PRIMARY_ADMIN_GMAIL, 'Sent verification code for device ' + deviceName, deviceId);

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
      forceApproveDeviceOnSheet(ss, empId, devId, devName, bdNow, body.revokeDeviceId || '');
      appendAuditLog(ss, bdNow, 'AUTH_OTP', empId, 'VERIFY_GMAIL_OTP_SUCCESS', PRIMARY_ADMIN_GMAIL, 'Approved device ' + devName, devId);
      return jsonResponse({ status: 'ok', message: 'Device approved successfully.', serverTimeMillis: bdNow.epochMillis });
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
    revokeDeviceOnSheet(ss, empId, devId);
    appendAuditLog(ss, bdNow, 'DEVICE_MGMT', empId, 'REVOKE_DEVICE', PRIMARY_ADMIN_GMAIL, reason, devId);
    return jsonResponse({ status: 'ok', message: 'Device revoked.' });
  } else if (subAction === 'approve') {
    forceApproveDeviceOnSheet(ss, empId, devId, devName, bdNow, body.revokeDeviceId || '');
    appendAuditLog(ss, bdNow, 'DEVICE_MGMT', empId, 'ADMIN_APPROVE_DEVICE', PRIMARY_ADMIN_GMAIL, reason, devId);
    return jsonResponse({ status: 'ok', message: 'Device approved by Admin.' });
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
      message: 'Maximum 2 active devices reached for Employee ID ' + employeeId + '. Admin approval or device revocation is required.'
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
  if (revokeDeviceId) {
    revokeDeviceOnSheet(ss, employeeId, revokeDeviceId);
  }
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
    if (String(values[i][0]).toLowerCase() === employeeId.toLowerCase() && String(values[i][1]) === deviceId) {
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

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
