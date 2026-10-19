// utils/googleAuth.js
// SINGLE SIGN-ON: verifies a Google ID token sent by the Android app.
//
// The token is an RS256-signed JWT. We check its signature against Google's published
// public certificates, then check that it was issued for OUR OAuth client (audience),
// by Google (issuer), has not expired, and that the email is verified. No extra npm
// package is needed - just jsonwebtoken (already a dependency) and Node's built-in fetch.
//
// Reference: Google for Developers (2026) Verify the Google ID token on your server side.
const jwt = require("jsonwebtoken");

const CERTS_URL = "https://www.googleapis.com/oauth2/v1/certs";
let certCache = { certs: null, expiresAt: 0 };

async function getGoogleCerts() {
  if (certCache.certs && Date.now() < certCache.expiresAt) return certCache.certs;

  const response = await fetch(CERTS_URL);
  if (!response.ok) throw new Error(`Could not fetch Google certificates (${response.status})`);
  const certs = await response.json();

  // Respect Google's cache lifetime, default one hour.
  const cacheControl = (response.headers && response.headers.get("cache-control")) || "";
  const match = /max-age=(\d+)/.exec(cacheControl);
  certCache = { certs, expiresAt: Date.now() + (match ? Number(match[1]) : 3600) * 1000 };
  return certs;
}

/**
 * @returns {Promise<{googleId: string, email: string, name: string}>}
 * @throws if the token is invalid, expired, for a different client, or the email is unverified
 */
async function verifyGoogleIdToken(idToken) {
  const clientId = process.env.GOOGLE_WEB_CLIENT_ID;
  if (!clientId) throw new Error("GOOGLE_WEB_CLIENT_ID is not configured on the server");

  const decoded = jwt.decode(idToken, { complete: true });
  if (!decoded || !decoded.header || !decoded.header.kid) throw new Error("Malformed Google token");

  const certs = await getGoogleCerts();
  const pem = certs[decoded.header.kid];
  if (!pem) throw new Error("Unknown Google signing key");

  const payload = jwt.verify(idToken, pem, {
    algorithms: ["RS256"],
    audience: clientId,
    issuer: ["accounts.google.com", "https://accounts.google.com"],
  });

  if (!payload.email || payload.email_verified !== true) {
    throw new Error("Google account email is not verified");
  }

  return {
    googleId: payload.sub,
    email: String(payload.email).toLowerCase(),
    name: payload.name || String(payload.email).split("@")[0],
  };
}

module.exports = { verifyGoogleIdToken };
