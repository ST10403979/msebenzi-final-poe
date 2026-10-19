// tests/jobs.test.js
process.env.SQLITE_PATH = "./test-jobs.sqlite";

const request = require("supertest");
const fs = require("fs");
const app = require("../server");
const sequelize = require("../config/db");

let householdToken;
let workerToken;
let jobId;

beforeAll(async () => {
  await sequelize.sync({ force: true });

  const household = await request(app).post("/api/auth/register").send({
    name: "Test Household",
    email: "household@example.com",
    password: "password123",
    role: "household",
  });
  const householdLogin = await request(app).post("/api/auth/login").send({
    email: "household@example.com",
    password: "password123",
  });
  householdToken = householdLogin.body.token;

  await request(app).post("/api/auth/register").send({
    name: "Test Worker",
    email: "worker2@example.com",
    password: "password123",
    role: "worker",
  });
  const workerLogin = await request(app).post("/api/auth/login").send({
    email: "worker2@example.com",
    password: "password123",
  });
  workerToken = workerLogin.body.token;
});

afterAll(async () => {
  await sequelize.close();
  if (fs.existsSync("./test-jobs.sqlite")) fs.unlinkSync("./test-jobs.sqlite");
});

describe("Job endpoints", () => {
  test("rejects unauthenticated requests", async () => {
    const res = await request(app).get("/api/jobs");
    expect(res.statusCode).toBe(401);
  });

  test("household can post a job", async () => {
    const res = await request(app)
      .post("/api/jobs")
      .set("Authorization", `Bearer ${householdToken}`)
      .send({
        title: "Garden tidy-up",
        description: "Trim hedges and mow the lawn",
        category: "gardening",
        location: "Gqeberha",
        budget: 350,
      });
    expect(res.statusCode).toBe(201);
    expect(res.body.status).toBe("open");
    jobId = res.body.id;
  });

  test("rejects a job post with a negative budget", async () => {
    const res = await request(app)
      .post("/api/jobs")
      .set("Authorization", `Bearer ${householdToken}`)
      .send({
        title: "Bad job",
        description: "desc",
        category: "cleaning",
        location: "Cape Town",
        budget: -50,
      });
    expect(res.statusCode).toBe(400);
  });

  test("worker can view the job feed", async () => {
    const res = await request(app)
      .get("/api/jobs")
      .set("Authorization", `Bearer ${workerToken}`);
    expect(res.statusCode).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
    expect(res.body.length).toBeGreaterThan(0);
  });

  test("worker can accept an open job", async () => {
    const res = await request(app)
      .put(`/api/jobs/${jobId}/accept`)
      .set("Authorization", `Bearer ${workerToken}`);
    expect(res.statusCode).toBe(200);
    expect(res.body.job.status).toBe("accepted");
  });

  test("a second worker cannot accept the same job", async () => {
    const res = await request(app)
      .put(`/api/jobs/${jobId}/accept`)
      .set("Authorization", `Bearer ${householdToken}`);
    expect(res.statusCode).toBe(409);
  });
});
