import { 
  getFirestore, 
  collection, 
  addDoc, 
  query, 
  where, 
  onSnapshot, 
  orderBy, 
  serverTimestamp,
  getDocs 
} from "firebase/firestore";
import { 
  ProductionEntry, 
  QualityEntry, 
  ProductionLine, 
  UserProfile 
} from "../types/nfl-types";

const db = getFirestore();

export const databaseService = {
  
  // --- PRODUCTION DATA ---

  /**
   * Saves a new production record
   */
  saveProduction: async (entry: Omit<ProductionEntry, 'timestamp'>) => {
    const prodRef = collection(db, "production");
    return await addDoc(prodRef, {
      ...entry,
      timestamp: serverTimestamp(),
    });
  },

  /**
   * REAL-TIME: Listens to production changes for a specific line or floor
   * This is what makes the Dashboard update instantly.
   */
  subscribeToProduction: (
    callback: (data: ProductionEntry[]) => void, 
    floorId?: string, 
    lineId?: string
  ) => {
    let q = query(collection(db, "production"), orderBy("timestamp", "desc"));

    if (floorId) q = query(q, where("floorId", "==", floorId));
    if (lineId) q = query(q, where("lineId", "==", lineId));

    return onSnapshot(q, (snapshot) => {
      const entries = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      })) as ProductionEntry[];
      callback(entries);
    });
  },

  // --- QUALITY DATA ---

  /**
   * Saves a new quality record (Restricted by Firestore Rules to Admin only)
   */
  saveQuality: async (entry: Omit<QualityEntry, 'timestamp'>) => {
    const qualRef = collection(db, "quality");
    return await addDoc(qualRef, {
      ...entry,
      timestamp: serverTimestamp(),
    });
  },

  /**
   * REAL-TIME: Listens to quality changes
   */
  subscribeToQuality: (
    callback: (data: QualityEntry[]) => void, 
    lineId?: string
  ) => {
    let q = query(collection(db, "quality"), orderBy("timestamp", "desc"));
    if (lineId) q = query(q, where("lineId", "==", lineId));

    return onSnapshot(q, (snapshot) => {
      const entries = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      })) as QualityEntry[];
      callback(entries);
    });
  },

  // --- CONFIGURATION & SETUP ---

  /**
   * Fetches all lines for a specific floor
   */
  getLinesByFloor: async (floorId: string): Promise<ProductionLine[]> => {
    const q = query(collection(db, "lines"), where("floorId", "==", floorId));
    const querySnapshot = await getDocs(q);
    return querySnapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as ProductionLine));
  },

  /**
   * Gets all lines (for Admin to setup)
   */
  getAllLines: async (): Promise<ProductionLine[]> => {
    const querySnapshot = await getDocs(collection(db, "lines"));
    return querySnapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as ProductionLine));
  }
};