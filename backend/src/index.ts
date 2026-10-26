import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { productionRouter } from './routes/production';
import { qualityRouter } from './routes/quality';
import { nptRouter } from './routes/npt';
import { supervisorRouter } from './routes/supervisor';
import { tvRouter } from './routes/tv';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Request logging
app.use((req, _res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
  next();
});

// Health check
app.get('/health', (_req, expressRes) => {
  expressRes.json({
    status: 'healthy',
    service: 'nfl-production-quality-backend',
    mode: process.env.APP_MODE || 'demo',
    timestamp: new Date().toISOString()
  });
});

// API Routes
app.use('/api/production', productionRouter);
app.use('/api/quality', qualityRouter);
app.use('/api/npt', nptRouter);
app.use('/api/supervisor', supervisorRouter);
app.use('/api/tv', tvRouter);

const adminFcmTokens = new Map<string, { employeeId: string; deviceId: string; fcmToken: string; updatedAt: string }>();

app.post('/api/auth/register-fcm-token', (req, res) => {
  const { employeeId, deviceId, fcmToken } = req.body || {};
  if (!employeeId || !fcmToken) {
    res.status(400).json({ status: 'error', message: 'employeeId and fcmToken are required.' });
    return;
  }
  const key = `${employeeId}:${deviceId || 'default'}`;
  adminFcmTokens.set(key, {
    employeeId: String(employeeId),
    deviceId: String(deviceId || 'default'),
    fcmToken: String(fcmToken),
    updatedAt: new Date().toISOString()
  });
  res.json({ status: 'ok', registeredCount: adminFcmTokens.size });
});

app.post('/api/notifications/admin-approval', async (req, res) => {
  const {
    requestId,
    requestType = 'EMPLOYEE_REGISTRATION',
    targetUserId = '',
    employeeName = '',
    designation = '',
    floor = '',
    deviceName = ''
  } = req.body || {};
  if (!requestId) {
    res.status(400).json({ status: 'error', message: 'requestId is required.' });
    return;
  }
  const projectId = process.env.FIREBASE_PROJECT_ID || 'nfl-live-72213';
  const accessToken = process.env.FCM_SERVER_OAUTH_TOKEN || '';
  const dataPayload: Record<string, string> = {
    requestId: String(requestId),
    requestType: String(requestType),
    targetUserId: String(targetUserId),
    employeeId: String(targetUserId),
    employeeName: String(employeeName || targetUserId),
    designation: String(designation),
    floor: String(floor),
    deviceName: String(deviceName),
    requestedAtMillis: String(Date.now())
  };

  let sentCount = 0;
  if (accessToken) {
    for (const entry of adminFcmTokens.values()) {
      try {
        const resp = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            message: {
              token: entry.fcmToken,
              data: dataPayload,
              android: { priority: 'HIGH' }
            }
          })
        });
        if (resp.ok) sentCount++;
      } catch (e) {
        console.warn('[FCM Send Notice]', e);
      }
    }
  }
  res.json({
    status: 'ok',
    requestId: String(requestId),
    dispatchedCount: sentCount,
    registeredAdminDevices: adminFcmTokens.size
  });
});

// Global Error Handler
app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error('[Unhandled Error]', err);
  res.status(500).json({
    error: 'Internal Server Error',
    message: err.message || 'An unexpected error occurred.'
  });
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, () => {
    console.log(`=======================================================`);
    console.log(` NFL Factory Production, Quality & NPT Backend`);
    console.log(` Server listening on port ${PORT}`);
    console.log(` TV Dashboard cache endpoint: http://localhost:${PORT}/api/tv/dashboard`);
    console.log(` Mode: ${process.env.APP_MODE || 'demo'}`);
    console.log(`=======================================================`);
  });
}

export default app;

