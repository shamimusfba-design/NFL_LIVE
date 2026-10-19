/**
 * NFL LIVE - UNIFIED DATA MODELS
 * This file is the single source of truth for all application data.
 */

export type UserRole = 'admin' | 'supervisor' | 'operator';

export interface UserProfile {
  uid: string;
  name: string;
  role: UserRole;
  assignedFloor?: string;
  createdAt: any; // Firebase Timestamp
}

export interface Floor {
  id: string;
  name: string;
}

export interface ProductionLine {
  id: string;
  floorId: string;
  name: string;
  targetConfig: {
    hourlyTarget: number;
    dailyTarget: number;
  };
}

export interface ProductionEntry {
  id?: string;
  lineId: string;
  floorId: string;
  date: string; // YYYY-MM-DD
  timestamp: any;
  qty: number;
  target: number;
  efficiency: number;
  enteredBy: string;
}

export interface QualityEntry {
  id?: string;
  lineId: string;
  floorId: string;
  date: string; // YYYY-MM-DD
  timestamp: any;
  defectQty: number;
  dhu: number;
  enteredBy: string;
}

export interface DashboardKPIs {
  totalProduction: number;
  totalQuality: number;
  avgEfficiency: number;
  currentFloor: string;
}