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
