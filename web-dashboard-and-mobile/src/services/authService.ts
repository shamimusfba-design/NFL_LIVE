import { 
  getAuth, 
  signInWithEmailAndPassword, 
  signOut, 
  onAuthStateChanged, 
  User 
} from "firebase/auth";
import { 
  getFirestore, 
  doc, 
  getDoc 
} from "firebase/firestore";
import { UserProfile, UserRole } from "../types/nfl-types";

const auth = getAuth();
const db = getFirestore();

export const authService = {
  /**
   * Logs a user in and returns their profile (including role)
   */
  login: async (email: string, pass: string): Promise<{ user: User, profile: UserProfile }> => {
    const userCredential = await signInWithEmailAndPassword(auth, email, pass);
    const user = userCredential.user;

    // Fetch the role from the Firestore 'users' collection
    const userDoc = await getDoc(doc(db, "users", user.uid));
    if (!userDoc.exists()) {
      throw new Error("User profile not found in database.");
    }

    const profile = userDoc.data() as UserProfile;
    return { user, profile };
  },

  /**
   * Logs the user out
   */
  logout: () => signOut(auth),

  /**
   * Observes auth state changes (for persistence)
   */
  subscribeToAuth: (callback: (user: User | null, profile: UserProfile | null) => void) => {
    return onAuthStateChanged(auth, async (user) => {
      if (user) {
        const userDoc = await getDoc(doc(db, "users", user.uid));
        const profile = userDoc.exists() ? (userDoc.data() as UserProfile) : null;
        callback(user, profile);
      } else {
        callback(null, null);
      }
    });
  }
};