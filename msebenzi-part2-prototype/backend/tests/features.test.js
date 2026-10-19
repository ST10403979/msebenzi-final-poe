// tests/features.test.js
// Part 3 features: blob storage (media), Google single sign-on, push-token registration,
// role rules, idempotent offline job creation and account deletion.
process.env.SQLITE_PATH = "./test-features.sqlite";
process.env.GOOGLE_WEB_CLIENT_ID = "test-client-id.apps.googleusercontent.com";

const request = require("supertest");
const fs = require("fs");
const app = require("../server");
const sequelize = require("../config/db");
const googleAuth = require("../utils/googleAuth");
const push = require("../utils/push");
const Media = require("../models/Media");

// Smallest byte sequences that start like a real JPEG / PNG (our API checks the magic bytes).
const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]), Buffer.alloc(200, 7)]);
const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(50, 1)]);

let hh; // household { token, id }
let wk; // worker    { token, id }

async function signUp(name, email, role) {
  const res = await request(app).post("/api/auth/register").send({ name, email, password: "password123", role });
  return { token: res.body.token, id: res.body.user.id, res };
}
const auth = (u) => ({ Authorization: `Bearer ${u.token}` });
const jobBody = (extra = {}) => ({
  title: "Paint fence", description: "Two coats", category: "Painting", location: "Gqeberha", budget: 500, ...extra,
});

beforeAll(async () => {
  await sequelize.sync({ force: true });
  hh = await signUp("Feature Household", "fh@example.com", "household");
  wk = await signUp("Feature Worker", "fw@example.com", "worker");
});

afterAll(async () => {
  await sequelize.close();
  if (fs.existsSync("./test-features.sqlite")) fs.unlinkSync("./test-features.sqlite");
});

describe("Registration", () => {
  test("returns a token so the app can log in immediately", () => {
    expect(hh.res.statusCode).toBe(201);
    expect(typeof hh.token).toBe("string");
    expect(hh.res.body.user.passwordHash).toBeUndefined();
  });

  test("treats emails case-insensitively", async () => {
    const res = await request(app).post("/api/auth/register").send({
      name: "Dup", email: "FH@Example.com", password: "password123", role: "worker",
    });
    expect(res.statusCode).toBe(409);
  });
});

describe("Blob storage (media)", () => {
  let mediaId;

  test("requires authentication", async () => {
    const res = await request(app).post("/api/media").set("Content-Type", "image/jpeg").send(JPEG);
    expect(res.statusCode).toBe(401);
  });

  test("stores a JPEG as a BLOB and returns its id", async () => {
    const res = await request(app).post("/api/media").set(auth(hh)).set("Content-Type", "image/jpeg").send(JPEG);
    expect(res.statusCode).toBe(201);
    expect(res.body.id).toBeGreaterThan(0);
    mediaId = res.body.id;
    const row = await Media.findByPk(mediaId);
    expect(row.size).toBe(JPEG.length);
    expect(row.mimeType).toBe("image/jpeg");
  });

  test("serves the exact bytes back with the right content type", async () => {
    const res = await request(app).get(`/api/media/${mediaId}`).set(auth(wk)).buffer(true).parse((r, cb) => {
      const chunks = [];
      r.on("data", (c) => chunks.push(c));
      r.on("end", () => cb(null, Buffer.concat(chunks)));
    });
    expect(res.statusCode).toBe(200);
    expect(res.headers["content-type"]).toMatch(/image\/jpeg/);
    expect(Buffer.compare(res.body, JPEG)).toBe(0);
  });

  test("accepts PNG too", async () => {
    const res = await request(app).post("/api/media").set(auth(hh)).set("Content-Type", "image/png").send(PNG);
    expect(res.statusCode).toBe(201);
  });

  test("rejects a file that only claims to be an image", async () => {
    const res = await request(app).post("/api/media").set(auth(hh)).set("Content-Type", "image/jpeg")
      .send(Buffer.from("this is not an image at all"));
    expect(res.statusCode).toBe(415);
  });

  test("rejects non-image content types", async () => {
    const res = await request(app).post("/api/media").set(auth(hh)).set("Content-Type", "application/json")
      .send({ hello: "world" });
    expect(res.statusCode).toBe(415);
  });

  test("returns 404 for an unknown image", async () => {
    const res = await request(app).get("/api/media/99999").set(auth(hh));
    expect(res.statusCode).toBe(404);
  });

  test("a user can set their own uploaded image as profile photo, not someone else's", async () => {
    const mine = await request(app).post("/api/media").set(auth(wk)).set("Content-Type", "image/jpeg").send(JPEG);
    const ok = await request(app).put("/api/users/me").set(auth(wk)).send({ avatarMediaId: mine.body.id });
    expect(ok.statusCode).toBe(200);
    expect(ok.body.user.avatarMediaId).toBe(mine.body.id);

    const stolen = await request(app).put("/api/users/me").set(auth(wk)).send({ avatarMediaId: mediaId });
    expect(stolen.statusCode).toBe(400);
  });

  test("replacing a profile photo removes the old image", async () => {
    const first = (await request(app).get("/api/users/me").set(auth(wk))).body.avatarMediaId;
    const next = await request(app).post("/api/media").set(auth(wk)).set("Content-Type", "image/png").send(PNG);
    await request(app).put("/api/users/me").set(auth(wk)).send({ avatarMediaId: next.body.id });
    expect(await Media.findByPk(first)).toBeNull();
  });
});

describe("Jobs: roles, photos and offline-sync idempotency", () => {
  test("a worker cannot post a job", async () => {
    const res = await request(app).post("/api/jobs").set(auth(wk)).send(jobBody());
    expect(res.statusCode).toBe(403);
  });

  test("a household can post a job with a photo; the category is normalised", async () => {
    const photo = await request(app).post("/api/media").set(auth(hh)).set("Content-Type", "image/jpeg").send(JPEG);
    const res = await request(app).post("/api/jobs").set(auth(hh)).send(jobBody({ photoMediaId: photo.body.id }));
    expect(res.statusCode).toBe(201);
    expect(res.body.photoMediaId).toBe(photo.body.id);
    expect(res.body.category).toBe("painting");
  });

  test("a job cannot use someone else's photo", async () => {
    const photo = await request(app).post("/api/media").set(auth(wk)).set("Content-Type", "image/jpeg").send(JPEG);
    const res = await request(app).post("/api/jobs").set(auth(hh)).send(jobBody({ photoMediaId: photo.body.id }));
    expect(res.statusCode).toBe(400);
  });

  test("replaying the same offline create (same clientRef) does not duplicate the job", async () => {
    const body = jobBody({ title: "Offline job", clientRef: "abc-123" });
    const first = await request(app).post("/api/jobs").set(auth(hh)).send(body);
    const second = await request(app).post("/api/jobs").set(auth(hh)).send(body);
    expect(first.statusCode).toBe(201);
    expect(second.statusCode).toBe(200);
    expect(second.body.id).toBe(first.body.id);
    const all = await request(app).get("/api/jobs").set(auth(wk));
    expect(all.body.filter((j) => j.title === "Offline job")).toHaveLength(1);
  });

  test("a household cannot accept a job", async () => {
    const job = await request(app).post("/api/jobs").set(auth(hh)).send(jobBody({ title: "Role check" }));
    const res = await request(app).put(`/api/jobs/${job.body.id}/accept`).set(auth(hh));
    expect(res.statusCode).toBe(403);
  });

  test("rejects over-long titles and non-numeric ids", async () => {
    const long = await request(app).post("/api/jobs").set(auth(hh)).send(jobBody({ title: "x".repeat(121) }));
    expect(long.statusCode).toBe(400);
    const bad = await request(app).get("/api/jobs/abc").set(auth(hh));
    expect(bad.statusCode).toBe(400);
  });
});

describe("Google single sign-on", () => {
  afterEach(() => jest.restoreAllMocks());
  const profile = { googleId: "g-111", email: "sso.user@example.com", name: "Sso User" };

  test("rejects a missing token", async () => {
    const res = await request(app).post("/api/auth/google").send({});
    expect(res.statusCode).toBe(400);
  });

  test("rejects a token Google does not accept", async () => {
    jest.spyOn(googleAuth, "verifyGoogleIdToken").mockRejectedValue(new Error("bad token"));
    const res = await request(app).post("/api/auth/google").send({ idToken: "forged" });
    expect(res.statusCode).toBe(401);
  });

  test("asks a brand-new Google user which role they want", async () => {
    jest.spyOn(googleAuth, "verifyGoogleIdToken").mockResolvedValue(profile);
    const res = await request(app).post("/api/auth/google").send({ idToken: "ok" });
    expect(res.statusCode).toBe(200);
    expect(res.body.needsRole).toBe(true);
    expect(res.body.token).toBeUndefined();
  });

  test("creates the account once a role is chosen, then logs in again with no role", async () => {
    jest.spyOn(googleAuth, "verifyGoogleIdToken").mockResolvedValue(profile);
    const created = await request(app).post("/api/auth/google").send({ idToken: "ok", role: "worker", language: "xh" });
    expect(created.statusCode).toBe(200);
    expect(created.body.user.role).toBe("worker");
    expect(created.body.user.language).toBe("xh");
    expect(created.body.user.googleId).toBeUndefined();

    const again = await request(app).post("/api/auth/google").send({ idToken: "ok" });
    expect(again.body.user.id).toBe(created.body.user.id);
    expect(typeof again.body.token).toBe("string");
  });

  test("links to an existing password account with the same verified email", async () => {
    jest.spyOn(googleAuth, "verifyGoogleIdToken").mockResolvedValue({ googleId: "g-222", email: "fw@example.com", name: "x" });
    const res = await request(app).post("/api/auth/google").send({ idToken: "ok" });
    expect(res.body.user.id).toBe(wk.id);
  });

  test("the real verifier refuses to run without a configured client id", async () => {
    const saved = process.env.GOOGLE_WEB_CLIENT_ID;
    delete process.env.GOOGLE_WEB_CLIENT_ID;
    await expect(googleAuth.verifyGoogleIdToken("x.y.z")).rejects.toThrow(/not configured/);
    process.env.GOOGLE_WEB_CLIENT_ID = saved;
  });

  test("the real verifier rejects a malformed token", async () => {
    await expect(googleAuth.verifyGoogleIdToken("not-a-jwt")).rejects.toThrow();
  });
});

describe("Push notifications", () => {
  test("registers and clears a device token", async () => {
    const token = "fcm-token-" + "x".repeat(40);
    const put = await request(app).put("/api/users/me/fcm-token").set(auth(wk)).send({ token });
    expect(put.statusCode).toBe(200);
    const User = require("../models/User");
    expect((await User.findByPk(wk.id)).fcmToken).toBe(token);

    const del = await request(app).delete("/api/users/me/fcm-token").set(auth(wk));
    expect(del.statusCode).toBe(200);
    expect((await User.findByPk(wk.id)).fcmToken).toBeNull();
  });

  test("moves a token to the newest account that registers it", async () => {
    const User = require("../models/User");
    const token = "shared-phone-" + "y".repeat(40);
    await request(app).put("/api/users/me/fcm-token").set(auth(wk)).send({ token });
    await request(app).put("/api/users/me/fcm-token").set(auth(hh)).send({ token });
    expect((await User.findByPk(wk.id)).fcmToken).toBeNull();
    expect((await User.findByPk(hh.id)).fcmToken).toBe(token);
  });

  test("rejects an invalid token", async () => {
    const res = await request(app).put("/api/users/me/fcm-token").set(auth(wk)).send({ token: "short" });
    expect(res.statusCode).toBe(400);
  });

  test("sending is a harmless no-op when Firebase is not configured", async () => {
    delete process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    const result = await push.sendToUser(wk.id, { title: "t", body: "b" });
    expect(result.sent).toBe(false);
  });

  test("alerts are still created for workers when a matching job is posted", async () => {
    await request(app).put("/api/users/me").set(auth(wk)).send({ skills: "plumbing" });
    await request(app).post("/api/jobs").set(auth(hh)).send(jobBody({ title: "Burst pipe", category: "plumbing" }));
    await new Promise((r) => setTimeout(r, 300)); // notifications are created in the background
    const res = await request(app).get("/api/notifications").set(auth(wk));
    expect(res.body.some((n) => n.type === "JOB_MATCH" && /Burst pipe/.test(n.message))).toBe(true);
  });
});

describe("Account deletion", () => {
  test("anonymises the account, cancels open jobs and blocks the old login", async () => {
    const victim = await signUp("Delete Me", "deleteme@example.com", "household");
    const job = await request(app).post("/api/jobs").set(auth(victim)).send(jobBody({ title: "Orphan job" }));
    const photo = await request(app).post("/api/media").set(auth(victim)).set("Content-Type", "image/jpeg").send(JPEG);
    await request(app).put("/api/users/me").set(auth(victim)).send({ bio: "secret bio", avatarMediaId: photo.body.id });

    const del = await request(app).delete("/api/users/me").set(auth(victim));
    expect(del.statusCode).toBe(200);

    const User = require("../models/User");
    const row = await User.findByPk(victim.id);
    expect(row.name).toBe("Deleted user");
    expect(row.email).toMatch(/@msebenzi\.invalid$/);
    expect(row.bio).toBeNull();
    expect(await Media.findByPk(photo.body.id)).toBeNull();

    const Job = require("../models/Job");
    expect((await Job.findByPk(job.body.id)).status).toBe("cancelled");

    const login = await request(app).post("/api/auth/login").send({ email: "deleteme@example.com", password: "password123" });
    expect(login.statusCode).toBe(401);
  });

  test("requires authentication", async () => {
    const res = await request(app).delete("/api/users/me");
    expect(res.statusCode).toBe(401);
  });
});

describe("Public pages", () => {
  test("serves the privacy policy and deletion instructions Google Play needs", async () => {
    const privacy = await request(app).get("/privacy");
    expect(privacy.statusCode).toBe(200);
    expect(privacy.text).toMatch(/Privacy Policy/);
    const del = await request(app).get("/delete-account");
    expect(del.statusCode).toBe(200);
    expect(del.text).toMatch(/Delete account/);
  });
});
