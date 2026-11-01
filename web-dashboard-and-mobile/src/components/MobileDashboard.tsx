import React from 'react';
import {
  FloorId,
  PeriodId,
  FloorKpis,
  LinePerformanceSummary,
  IncidentRecord,
  WeeklyQualityRecord,
  ProductionEntryRecord,
  QualityEntryRecord
} from '../types';
import { HourlyWallboardBucket } from '../services/calculationEngine';
import { TvDesktopDashboard } from './TvDesktopDashboard';

interface MobileDashboardProps {
  todayIso: string;
  selectedFloor: FloorId;
  onSelectFloor: (floor: FloorId) => void;
  selectedPeriod: PeriodId;
  onSelectPeriod: (p: PeriodId) => void;
  kpis: FloorKpis;
  floorBenchmarks?: Array<{ floor: string; todayEff: number; monthEff: number }>;
  lineSummaries: LinePerformanceSummary[];
  hourlySeries: HourlyWallboardBucket[];
  weeklyQuality: WeeklyQualityRecord[];
  productions: ProductionEntryRecord[];
  qualities: QualityEntryRecord[];
  topDefects: Array<{ defect: string; count: number; dhu: number }>;
  highestDhuLines: Array<{ line: string; dhu: number }>;
  isDemoMode: boolean;
  onToggleDemo: () => void;
  openIncidents: IncidentRecord[];
  allIncidents?: IncidentRecord[];
  onOpenIncidentPanel: () => void;
  onNavigateToReports: () => void;
}

export const MobileDashboard: React.FC<MobileDashboardProps> = props => {
  return (
    <div className="pb-16 bg-[#0A1522]">
      <TvDesktopDashboard {...props} />
    </div>
  );
};