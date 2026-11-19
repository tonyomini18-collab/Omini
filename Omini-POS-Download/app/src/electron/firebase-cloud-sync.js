/**
 * Optional Cloud Sync adapter connecting the PC Desktop engine to the same
 * Firebase project & named Firestore database used by the Android app.
 */
const FIRESTORE_DATABASE_ID = "ai-studio-android-aromapos-5c39add0-9320-45cf-a066-a5c1f668de36";

async function syncPendingToFirestore(engine, firebaseConfig, storeId) {
  if (!firebaseConfig || !firebaseConfig.apiKey || !storeId) {
    return { synced: 0, skipped: true };
  }
  const { initializeApp, getApps } = require("firebase/app");
  const { getFirestore, doc, setDoc, serverTimestamp } = require("firebase/firestore");

  const app = getApps().length ? getApps()[0] : initializeApp(firebaseConfig);
  const db = getFirestore(app, FIRESTORE_DATABASE_ID);

  const ops = [...(engine.state.pendingCloudSync || [])];
  let synced = 0;
  for (const op of ops) {
    try {
      const ref = doc(db, "stores", storeId, op.collectionName, op.documentId);
      await setDoc(ref, { ...op.payload, updatedAt: serverTimestamp() }, { merge: true });
      synced++;
      engine.state.pendingCloudSync = engine.state.pendingCloudSync.filter((x) => x.opId !== op.opId);
    } catch (_) {
      break;
    }
  }
  if (synced > 0) engine.persist();
  return { synced, skipped: false };
}

module.exports = { syncPendingToFirestore, FIRESTORE_DATABASE_ID };
