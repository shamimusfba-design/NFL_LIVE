import { google, sheets_v4 } from 'googleapis';
import { ProductionEntry, QualityEntry, NptEvent, SupervisorSetup, TvDashboardResponse } from '../types';

export class GoogleSheetsService {
  private sheetsClient: sheets_v4.Sheets | null = null;
  private isConfigured = false;
  private spreadsheetId: string;
  private writeQueue: Promise<unknown> = Promise.resolve();
  private processedSubmissionIds = new Set<string>();

  // In-memory cache when in demo mode or offline
  private inMemoryProduction: ProductionEntry[] = [];
  private inMemoryQuality: QualityEntry[] = [];
  private inMemoryNpt: NptEvent[] = [];
  private inMemorySetups = new Map<string, SupervisorSetup>();

  constructor() {
    this.spreadsheetId = process.env.GOOGLE_SHEET_ID || '1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0';
    this.initClient();
  }

  private initClient(): void {
    const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
    const credJson = process.env.GOOGLE_SERVICE_ACCOUNT_KEY;

    if (credJson || credPath) {
      try {
        const auth = new google.auth.GoogleAuth({
          scopes: ['https://www.googleapis.com/auth/spreadsheets'],
          ...(credJson ? { credentials: JSON.parse(credJson) } : { keyFile: credPath })
        });
        this.sheetsClient = google.sheets({ version: 'v4', auth });
        this.isConfigured = true;
        console.log('[GoogleSheetsService] Live credentials initialized successfully.');
      } catch (err) {
        console.warn('[GoogleSheetsService] Failed to load credentials, falling back to demo mode:', err);
        this.isConfigured = false;
      }
    } else {
      console.log('[GoogleSheetsService] No credentials configured. Running in Demo Mode.');
      this.isConfigured = false;
    }
  }

  public isLiveMode(): boolean {
    return this.isConfigured && process.env.APP_MODE === 'live';
  }

  /**
   * Enqueues writes sequentially to prevent Google Sheets rate-limit or race condition bugs.
   */
  private async runSerialized<T>(operation: () => Promise<T>): Promise<T> {
    const resultPromise = this.writeQueue.then(operation);
    this.writeQueue = resultPromise.catch(() => {});
    return resultPromise;
  }

  /**
   * Append production entry idempotently using submissionId.
   */
  public async appendProduction(entry: ProductionEntry): Promise<{ success: boolean; duplicate: boolean }> {
    if (this.processedSubmissionIds.has(entry.submissionId)) {
      console.log(`[GoogleSheetsService] Duplicate production submission ${entry.submissionId} ignored.`);
      return { success: true, duplicate: true };
    }

    this.processedSubmissionIds.add(entry.submissionId);
    this.inMemoryProduction.unshift(entry);

    if (!this.isLiveMode() || !this.sheetsClient) {
      return { success: true, duplicate: false };
    }

    return this.runSerialized(async () => {
      try {
        const row = [
          entry.submissionId,
          entry.date,
          entry.hourSlot,
          entry.lineId,
          entry.styleNumber,
          entry.outputQty,
          entry.remarks || '',
          new Date(entry.timestamp).toISOString(),
          entry.modifiedBy,
          entry.revision
        ];

        await this.sheetsClient!.spreadsheets.values.append({
          spreadsheetId: this.spreadsheetId,
          range: 'Raw_Production!A:J',
          valueInputOption: 'USER_ENTERED',
          requestBody: { values: [row] }
        });

        return { success: true, duplicate: false };
      } catch (error) {
        console.error('[GoogleSheetsService] Write to Raw_Production failed:', error);
        throw error;
      }
    });
  }

  /**
   * Append quality entry idempotently using submissionId.
   */
  public async appendQuality(entry: QualityEntry): Promise<{ success: boolean; duplicate: boolean }> {
    if (this.processedSubmissionIds.has(entry.submissionId)) {
      console.log(`[GoogleSheetsService] Duplicate quality submission ${entry.submissionId} ignored.`);
      return { success: true, duplicate: true };
    }

    this.processedSubmissionIds.add(entry.submissionId);
    this.inMemoryQuality.unshift(entry);

    if (!this.isLiveMode() || !this.sheetsClient) {
      return { success: true, duplicate: false };
    }

    return this.runSerialized(async () => {
      try {
        const defectDetails = entry.defects.map(d => `${d.defectType}:${d.count}`).join(';');
        const row = [
          entry.submissionId,
          entry.date,
          entry.hourSlot,
          entry.lineId,
          entry.styleNumber,
          entry.checkedGarments,
          entry.defectiveGarments,
          entry.totalDefects,
          defectDetails,
          entry.remarks || '',
          new Date(entry.timestamp).toISOString(),
          entry.modifiedBy
        ];

        await this.sheetsClient!.spreadsheets.values.append({
          spreadsheetId: this.spreadsheetId,
          range: 'Raw_Quality!A:L',
          valueInputOption: 'USER_ENTERED',
          requestBody: { values: [row] }
        });

        return { success: true, duplicate: false };
      } catch (error) {
        console.error('[GoogleSheetsService] Write to Raw_Quality failed:', error);
        throw error;
      }
    });
  }

  /**
   * Append / Update NPT event.
   */
  public async recordNpt(event: NptEvent): Promise<void> {
    const existingIndex = this.inMemoryNpt.findIndex(e => e.id === event.id);
    if (existingIndex >= 0) {
      this.inMemoryNpt[existingIndex] = event;
    } else {
      this.inMemoryNpt.unshift(event);
    }

    if (!this.isLiveMode() || !this.sheetsClient) {
      return;
    }

    await this.runSerialized(async () => {
      const row = [
        event.id,
        event.lineId,
        event.styleNumber,
        event.reason,
        event.isFullLine ? 'FULL' : 'PARTIAL',
        event.affectedManpower,
        new Date(event.startTimeMillis).toISOString(),
        event.endTimeMillis ? new Date(event.endTimeMillis).toISOString() : '',
        event.isClosed ? 'CLOSED' : 'RUNNING',
        event.lostManMinutes,
        event.machineOrOpRef || '',
        event.remarks || '',
        event.createdBy
      ];

      await this.sheetsClient!.spreadsheets.values.append({
        spreadsheetId: this.spreadsheetId,
        range: 'Raw_NPT!A:M',
        valueInputOption: 'USER_ENTERED',
        requestBody: { values: [row] }
      });
    });
  }

  /**
   * Save Supervisor Line Setup Snapshot.
   */
  public async saveLineSetup(setup: SupervisorSetup): Promise<void> {
    this.inMemorySetups.set(setup.lineId, setup);

    if (!this.isLiveMode() || !this.sheetsClient) {
      return;
    }

    await this.runSerialized(async () => {
      const row = [
        setup.lineId,
        setup.date,
        setup.styleNumber,
        setup.orderNumber,
        setup.smv,
        setup.targetHourlyPcs,
        setup.targetEfficiencyPct,
        setup.operatorsCount,
        setup.helpersCount,
        setup.includeHelpersInEfficiency ? 'YES' : 'NO',
        setup.workingMinutesPerHour,
        new Date(setup.snapshotTimestamp).toISOString()
      ];

      await this.sheetsClient!.spreadsheets.values.append({
        spreadsheetId: this.spreadsheetId,
        range: 'Raw_SupervisorSetup!A:L',
        valueInputOption: 'USER_ENTERED',
        requestBody: { values: [row] }
      });
    });
  }

  public getInMemoryProduction(): ProductionEntry[] {
    return this.inMemoryProduction;
  }

  public getInMemoryQuality(): QualityEntry[] {
    return this.inMemoryQuality;
  }

  public getInMemoryNpt(): NptEvent[] {
    return this.inMemoryNpt;
  }

  public getLineSetup(lineId: string): SupervisorSetup | undefined {
    return this.inMemorySetups.get(lineId);
  }
}

export const sheetsService = new GoogleSheetsService();
