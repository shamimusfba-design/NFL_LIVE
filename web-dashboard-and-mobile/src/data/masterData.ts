import { LineMasterItem, UserAccount, LineTargetConfig, IncidentRecord, ProductionEntryRecord, QualityEntryRecord } from '../types';

export const ALL_FACTORY_LINES: LineMasterItem[] = [
  // Sew 1 (22 lines)
  { id: 'A1', floor: 'Sew 1', isActive: true }, { id: 'A2', floor: 'Sew 1', isActive: true },
  { id: 'B', floor: 'Sew 1', isActive: true }, { id: 'C', floor: 'Sew 1', isActive: true },
  { id: 'D1A', floor: 'Sew 1', isActive: true }, { id: 'D2A', floor: 'Sew 1', isActive: true },
  { id: 'D1B', floor: 'Sew 1', isActive: true }, { id: 'D2B', floor: 'Sew 1', isActive: true },
  { id: 'E1A', floor: 'Sew 1', isActive: true }, { id: 'E2A', floor: 'Sew 1', isActive: true },
  { id: 'E1B', floor: 'Sew 1', isActive: true }, { id: 'E2B', floor: 'Sew 1', isActive: true },
  { id: 'FA', floor: 'Sew 1', isActive: true }, { id: 'FB', floor: 'Sew 1', isActive: true },
  { id: 'G1A', floor: 'Sew 1', isActive: true }, { id: 'G2A', floor: 'Sew 1', isActive: true },
  { id: 'G1B', floor: 'Sew 1', isActive: true }, { id: 'G2B', floor: 'Sew 1', isActive: true },
  { id: 'H1A', floor: 'Sew 1', isActive: true }, { id: 'H2A', floor: 'Sew 1', isActive: true },
  { id: 'H1B', floor: 'Sew 1', isActive: true }, { id: 'H2B', floor: 'Sew 1', isActive: true },

  // Sew 2 (17 lines)
  { id: 'J1', floor: 'Sew 2', aliases: ['J'], isActive: true }, { id: 'J2', floor: 'Sew 2', aliases: ['J'], isActive: true },
  { id: 'K1', floor: 'Sew 2', isActive: true }, { id: 'K2', floor: 'Sew 2', isActive: true },
  { id: 'L1', floor: 'Sew 2', isActive: true }, { id: 'L2', floor: 'Sew 2', isActive: true },
  { id: 'M1', floor: 'Sew 2', aliases: ['M'], isActive: true }, { id: 'M2', floor: 'Sew 2', aliases: ['M'], isActive: true },
  { id: 'N1', floor: 'Sew 2', isActive: true }, { id: 'N2', floor: 'Sew 2', isActive: true },
  { id: 'O1', floor: 'Sew 2', isActive: true }, { id: 'O2', floor: 'Sew 2', isActive: true },
  { id: 'P1', floor: 'Sew 2', aliases: ['P'], isActive: true }, { id: 'P2', floor: 'Sew 2', aliases: ['P'], isActive: true },
  { id: 'Q1', floor: 'Sew 2', isActive: true }, { id: 'Q2', floor: 'Sew 2', isActive: true },
  { id: 'R1', floor: 'Sew 2', aliases: ['R'], isActive: true },

  // Sew 3 (23 lines)
  { id: 'S1', floor: 'Sew 3', isActive: true }, { id: 'S2', floor: 'Sew 3', isActive: true },
  { id: 'T1', floor: 'Sew 3', isActive: true }, { id: 'T2', floor: 'Sew 3', isActive: true },
  { id: 'U1', floor: 'Sew 3', isActive: true }, { id: 'U2', floor: 'Sew 3', isActive: true },
  { id: 'UA', floor: 'Sew 3', isActive: true }, { id: 'UB', floor: 'Sew 3', isActive: true },
  { id: 'V1A', floor: 'Sew 3', isActive: true }, { id: 'V1B', floor: 'Sew 3', isActive: true },
  { id: 'W1A', floor: 'Sew 3', isActive: true }, { id: 'W1B', floor: 'Sew 3', isActive: true },
  { id: 'W2A', floor: 'Sew 3', isActive: true }, { id: 'W2B', floor: 'Sew 3', isActive: true },
  { id: 'X1', floor: 'Sew 3', isActive: true }, { id: 'Y1', floor: 'Sew 3', isActive: true },
  { id: 'Y2', floor: 'Sew 3', isActive: true }, { id: 'Z1', floor: 'Sew 3', isActive: true },
  { id: 'Z2', floor: 'Sew 3', isActive: true }, { id: 'ZA1A', floor: 'Sew 3', isActive: true },
  { id: 'ZA1B', floor: 'Sew 3', isActive: true }, { id: 'ZA2A', floor: 'Sew 3', isActive: true },
  { id: 'ZA2B', floor: 'Sew 3', isActive: true }
];

// NEW GARMENT STYLES LIST
export const GARMENT_STYLES = [
  'SS T shirt', 'LS T shirt', 'Hoodie', 'Jacket', 'Hoody Jacket', 
  'Leggings', 'Pant', 'Body Suit', 'Jumpsuit', 'Custom'
];

// NEW DEFECT TYPES LIST
export const DEFECT_TYPES = [
  'Poor iron', 'Uncut thread', 'Dirty spot', 'Oil spot', 'Skip stitch',
  'Open seam', 'Broken stitch', 'Puckering', 'Pleat', 'Shading', 'Custom'
];

// NEW NPT TYPES LIST
export const NPT_TYPES = [
  'Machine Breakdown', 'Power Failure', 'Material Shortage', 
  'Manpower Shortage', 'Quality Issue', 'Setup Time', 'Custom'
];

export const STANDARD_USERS: UserAccount[] = [
  { id: 'u-1', name: 'Admin', username: 'admin', role: 'ADMIN', floor: 'All' },
  { id: 'u-2', name: 'Prod Reporter', username: 'prod_reporter', role: 'PRODUCTION_REPORTER', floor: 'Sew 2', assignedLine: 'K1' },
  { id: 'u-3', name: 'QA Reporter', username: 'qa_reporter', role: 'QUALITY_REPORTER', floor: 'Sew 2', assignedLine: 'K1' },
  { id: 'u-4', name: 'Supervisor', username: 'supervisor', role: 'SUPERVISOR', floor: 'Sew 2' },
  { id: 'u-5', name: 'Prod Manager', username: 'prod_mgr', role: 'PRODUCTION_MANAGER', floor: 'All' },
  { id: 'u-6', name: 'QA Manager', username: 'qa_mgr', role: 'QUALITY_MANAGER', floor: 'All' },
  { id: 'u-7', name: 'Mechanic', username: 'mechanic', role: 'MECHANIC', floor: 'Sew 2' },
  { id: 'u-8', name: 'IE Officer', username: 'ie_officer', role: 'IE', floor: 'All' },
  { id: 'u-9', name: 'Input Coord', username: 'input_coord', role: 'INPUT_MATERIAL', floor: 'Sew 2' },
  { id: 'u-10', name: 'Management', username: 'management', role: 'MANAGEMENT_VIEW', floor: 'All' }
];

// ... rest of your existing helper functions (generateSyntheticDemoDataset, etc.)
// (Note: I have kept the helper functions the same to avoid breaking your current demo)
// Copy the rest of your existing functions from the original masterData.ts file here.