// utils/push.js
// REAL-TIME PUSH NOTIFICATIONS via Firebase Cloud Messaging (HTTP v1 API).
//
// Setup (see docs/SETUP_GUIDE.md): in the Firebase console create a service account key and
// put the whole JSON file contents in the FIREBASE_SERVICE_ACCOUNT_JSON environment variable.
// If it is not set, push is silently skipped - the Android app still shows alerts by polling
// the API, so nothing breaks.
//
// No firebase-admin package is used: we sign a short-lived OAuth2 JWT with the service
// account key (jsonwebtoken) and call the FCM REST endpoint with Node's built-in fetch.
//
// Reference: Firebase (2026) Send a message using the FCM HTTP v1 API.
const jwt = require("jsonwebtoken");
const User = require("../models/User");

let serviceAccount; // undefined = not loaded yet, false = not configured
let tokenCache = { value: null, expiresAt: 0 };

function loadServiceAccount() {
  if (serviceAccount !== undefined) return serviceAccount;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    serviceAccount = false;
    return serviceAccount;
  }
  try {
    serviceAccount = JSON.parse(raw);
  } catch (err) {
    console.error("[push] FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON - push disabled");
    serviceAccount = false;
  }
  return serviceAccount;
}

async function getAccessToken(sa) {
  if (tokenCache.value && Date.now() < tokenCache.expiresAt) return tokenCache.value;

  const now = Math.floor(Date.now() / 1000);
  const assertion = jwt.sign(
    {
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    },
    sa.private_key,
    { algorithm: "RS256" }
  );

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`OAuth token request failed (${response.status})`);
  const body = await response.json();
  tokenCache = { value: body.access_token, expiresAt: Date.now() + (body.expires_in - 60) * 1000 };
  return tokenCache.value;
}

/**
 * Sends a data message to every device registered for the user.
 * Data-only messages make the Android app's MsebenziMessagingService build the
 * notification itself, whether the app is open or closed.
 * Never throws: push is a best-effort extra on top of the in-app alert.
 */
async function sendToUser(userId, { title, body, data = {} }) {
  try {
    const sa = loadServiceAccount();
    if (!sa) return { sent: false, reason: "push not configured" };

    const user = await User.findByPk(userId);
    if (!user || !user.fcmToken) return { sent: false, reason: "no device token" };

    const accessToken = await getAccessToken(sa);
    const stringData = { title, body };
    for (const [k, v] of Object.entries(data)) stringData[k] = String(v);

    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: { token: user.fcmToken, data: stringData, android: { priority: "HIGH" } },
        }),
      }
    );

    if (!response.ok) {
      const text = await response.text();
      // A stale token (app uninstalled) - forget it so we stop trying.
      if (response.status === 404 || text.includes("UNREGISTERED")) {
        user.fcmToken = null;
        await user.save();
      }
      console.error(`[push] FCM rejected message for user ${userId}: ${response.status}`);
      return { sent: false, reason: `fcm ${response.status}` };
    }
    console.log(`[push] Push sent to user ${userId}`);
    return { sent: true };
  } catch (err) {
    console.error("[push] Failed to send push:", err.message);
    return { sent: false, reason: err.message };
  }
}

module.exports = { sendToUser };
