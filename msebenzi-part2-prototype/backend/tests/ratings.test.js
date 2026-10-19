// tests/ratings.test.js
process.env.SQLITE_PATH = "./test-ratings.sqlite";
process.env.ADMIN_TOKEN = "test_admin_token";

const request = require("supertest");
const fs = require("fs");
const app = require("../server");
const sequelize = require("../config/db");

let householdToken, workerToken, workerId, jobId;

beforeAll(async () => {
  await sequelize.sync({ force: true });

  await request(app).post("/api/auth/register").send({
    name: "Rating Household", email: "ratehh@example.com", password: "password123", role: "household",
  });
  householdToken = (await request(app).post("/api/auth/login").send({
    email: "ratehh@example.com", password: "password123",
  })).body.token;

  const workerRes = await request(app).post("/api/auth/register").send({
    name: "Rating Worker", email: "rateworker@example.com", password: "password123", role: "worker",
  });
  workerId = workerRes.body.user.id;
  workerToken = (await request(app).post("/api/auth/login").send({
    email: "rateworker@example.com", password: "password123",
  })).body.token;

  const jobRes = await request(app).post("/api/jobs")
    .set("Authorization", `Bearer ${householdToken}`)
    .send({ title: "Deep clean", description: "desc", category: "cleaning", location: "Mthatha", budget: 200 });
  jobId = jobRes.body.id;

  await request(app).put(`/api/jobs/${jobId}/accept`).set("Authorization", `Bearer ${workerToken}`);
});

afterAll(async () => {
  await sequelize.close();
  if (fs.existsSync("./test-ratings.sqlite")) fs.unlinkSync("./test-ratings.sqlite");
});

describe("Rating restrictions", () => {
  test("cannot rate a job that isn't completed yet", async () => {
    const res = await request(app)
      .post(`/api/jobs/${jobId}/rate`)
      .set("Authorization", `Bearer ${householdToken}`)
      .send({ score: 5 });
    expect(res.statusCode).toBe(409);
  });
});

describe("Rating a completed job", () => {
  beforeAll(async () => {
    await request(app).put(`/api/jobs/${jobId}/complete`).set("Authorization", `Bearer ${householdToken}`);
  });

  test("rejects an out-of-range score", async () => {
    const res = await request(app)
      .post(`/api/jobs/${jobId}/rate`)
      .set("Authorization", `Bearer ${householdToken}`)
      .send({ score: 7 });
    expect(res.statusCode).toBe(400);
  });

  test("household can rate the worker", async () => {
    const res = await request(app)
      .post(`/api/jobs/${jobId}/rate`)
      .set("Authorization", `Bearer ${householdToken}`)
      .send({ score: 5, comment: "Great work!" });
    expect(res.statusCode).toBe(201);
  });

  test("rating updates the worker's average", async () => {
    const res = await request(app)
      .get(`/api/users/${workerId}`)
      .set("Authorization", `Bearer ${householdToken}`);
    expect(res.body.ratingAverage).toBe(5);
    expect(res.body.ratingCount).toBe(1);
  });

  test("the same rater cannot rate the same job twice", async () => {
    const res = await request(app)
      .post(`/api/jobs/${jobId}/rate`)
      .set("Authorization", `Bearer ${householdToken}`)
      .send({ score: 3 });
    expect(res.statusCode).toBe(409);
  });

  test("an uninvolved user cannot rate the job", async () => {
    await request(app).post("/api/auth/register").send({
      name: "Bystander", email: "bystander@example.com", password: "password123", role: "worker",
    });
    const bystanderToken = (await request(app).post("/api/auth/login").send({
      email: "bystander@example.com", password: "password123",
    })).body.token;

    const res = await request(app)
      .post(`/api/jobs/${jobId}/rate`)
      .set("Authorization", `Bearer ${bystanderToken}`)
      .send({ score: 4 });
    expect(res.statusCode).toBe(403);
  });
});

describe("Worker verification", () => {
  test("worker can request verification", async () => {
    const res = await request(app)
      .put("/api/users/me/request-verification")
      .set("Authorization", `Bearer ${workerToken}`);
    expect(res.statusCode).toBe(200);
    expect(res.body.user.verificationStatus).toBe("pending");
  });

  test("admin endpoint rejects a missing/invalid token", async () => {
    const res = await request(app).put(`/api/admin/users/${workerId}/verify`);
    expect(res.statusCode).toBe(401);
  });

  test("admin endpoint approves verification with the correct token", async () => {
    const res = await request(app)
      .put(`/api/admin/users/${workerId}/verify`)
      .set("x-admin-token", "test_admin_token");
    expect(res.statusCode).toBe(200);
    expect(res.body.user.verificationStatus).toBe("verified");
  });
});

describe("Notifications", () => {
  test("worker received a JOB_MATCH notification when the cleaning job was posted", async () => {
    const res = await request(app)
      .get("/api/notifications")
      .set("Authorization", `Bearer ${workerToken}`);
    expect(res.statusCode).toBe(200);
    const types = res.body.map((n) => n.type);
    expect(types).toContain("JOB_MATCH");
  });

  test("household received a JOB_ACCEPTED notification", async () => {
    const res = await request(app)
      .get("/api/notifications")
      .set("Authorization", `Bearer ${householdToken}`);
    const types = res.body.map((n) => n.type);
    expect(types).toContain("JOB_ACCEPTED");
  });
});
