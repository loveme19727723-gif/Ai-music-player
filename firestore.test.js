const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("songs_public").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("categories").where("active", "==", true).get());
  await assertFails(unauthDb.collection("app_settings").doc("config").get());
});

test("User profile isolation & anti-privilege-escalation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  // Owner create succeeds with role=user, blocked=false, credits<=100
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "user",
      blocked: false,
      credits: 50,
      preferredLanguage: "English",
      createdAt: pastDate(10000),
      updatedAt: pastDate(10000),
    })
  );

  // Self-assigned admin role on create fails
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      role: "admin",
      blocked: false,
      credits: 50,
      preferredLanguage: "English",
      createdAt: pastDate(10000),
      updatedAt: pastDate(10000),
    })
  );

  // Bob cannot read Alice's profile (PII protection)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Normal user cannot list /users
  await assertFails(aliceDb.collection("users").get());

  // Alice can spend credits or update displayName, but cannot mint credits or escalate role
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 45,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 500,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: pastDate(1000),
    })
  );
});

test("User songs subcollection: Kotlin payload parity & cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const songPayload = {
    userId: ALICE_UID,
    title: "Aneek DJ Bass",
    recipientName: "Aneek",
    prompt: "Fast Beat, Bass Boost, Bengali DJ for Aneek",
    lyrics: "[Intro]\nAneek in the mix...",
    language: "Banglish",
    genre: "DJ Bass Boost",
    style: "Bengali DJ",
    mood: "Energetic",
    voiceType: "Male",
    beat: "140 BPM Four-on-the-floor",
    instruments: "808 Sub Bass, Synth Lead, Dholak",
    durationSec: 60,
    bpm: 140,
    musicKey: "Am",
    chordProgression: ["Am", "F", "C", "G"],
    coverType: "dj",
    favorite: false,
    downloaded: false,
    playCount: 0,
    visibility: "private",
    variationIndex: 1,
    createdAt: pastDate(10000),
    updatedAt: pastDate(10000),
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("songs").doc("song_1").set(songPayload)
  );

  // Reject ghost @DocumentId 'id' field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("songs").doc("song_2").set({
      ...songPayload,
      id: "song_2",
    })
  );

  // Partial update allowed fields succeed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("songs").doc("song_1").update({
      favorite: true,
      playCount: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user read/list/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("songs").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("songs").doc("song_1").get());
});

test("Public songs: filtered query succeeds, unfiltered fails, admin features allowed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "chapchup481@gmail.com",
    email_verified: true,
  }).firestore();
  const spoofAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: "chapchup481@gmail.com",
    email_verified: false,
  }).firestore();

  const publicSong = {
    userId: ALICE_UID,
    creatorName: "Alice",
    title: "Midnight Sitar",
    recipientName: "",
    lyrics: "[Verse 1]\nMidnight strings...",
    language: "Bengali",
    genre: "Classic Bengali",
    style: "Acoustic Classic",
    mood: "Romantic",
    voiceType: "Female",
    instruments: "Sitar, Tabla, Grand Piano",
    durationSec: 90,
    bpm: 88,
    musicKey: "Dm",
    chordProgression: ["Dm", "Bb", "C", "Am"],
    coverType: "classic",
    visibility: "public",
    featuredSection: "none",
    playCount: 0,
    likeCount: 0,
    createdAt: pastDate(10000),
    updatedAt: pastDate(10000),
  };

  await assertSucceeds(aliceDb.collection("songs_public").doc("pub_1").set(publicSong));

  // Alice cannot self-promote to featuredSection='featured'
  await assertFails(
    aliceDb.collection("songs_public").doc("pub_2").set({
      ...publicSong,
      featuredSection: "featured",
    })
  );

  // Filtered query works for Bob; unfiltered query fails
  await assertSucceeds(bobDb.collection("songs_public").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("songs_public").get());

  // Verified admin can promote to featured, unverified email spoof fails
  await assertSucceeds(
    adminDb.collection("songs_public").doc("pub_1").update({
      featuredSection: "featured",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    spoofAdminDb.collection("songs_public").doc("pub_1").update({
      featuredSection: "trending",
      updatedAt: pastDate(500),
    })
  );
});

test("Categories & AppSettings: admin-only writes, verified filtered reads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "chapchup481@gmail.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    aliceDb.collection("categories").doc("cat_dj").set({
      name: "DJ Remix",
      group: "DJ",
      active: true,
      sortOrder: 1,
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  await assertSucceeds(
    adminDb.collection("categories").doc("cat_dj").set({
      name: "DJ Remix",
      group: "DJ",
      active: true,
      sortOrder: 1,
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  await assertSucceeds(aliceDb.collection("categories").where("active", "==", true).get());
  await assertFails(aliceDb.collection("categories").get());
});
